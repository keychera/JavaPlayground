package self.chera.annotations;

import com.google.auto.service.AutoService;
import lombok.AllArgsConstructor;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static javax.tools.Diagnostic.Kind.*;

@SupportedAnnotationTypes("self.chera.annotations.AutoCSV")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class AutoCSVProcessor extends AbstractProcessor {
    void print(Diagnostic.Kind kind, Object msg, Element element) {
        processingEnv.getMessager().printMessage(kind, msg.toString(), element);
    }

    @AllArgsConstructor
    private static class FilterStrategy {
        public Predicate<Element> strategy;
        public String errorMessage;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        annotations.forEach(annotation -> {

            var allAnnotatedElements = roundEnv.getElementsAnnotatedWith(annotation);

            Function<Element, Optional<? extends Element>> getHeaderField = element -> element.getEnclosedElements().stream().filter(
                    e -> e.getSimpleName().toString().equals("header")
            ).findAny();

            var filters = List.of(
                    new FilterStrategy(e -> e.getEnclosedElements().size() > 0,
                            "Class is empty"),
                    new FilterStrategy(e -> getHeaderField.apply(e).isPresent(),
                            "Class has no field named `header`"),
                    new FilterStrategy(e -> getHeaderField.apply(e).get().asType().getKind().equals(TypeKind.ARRAY),
                            "`header` is not an ArrayType"),
                    new FilterStrategy(e -> ((ArrayType) getHeaderField.apply(e).get().asType()).getComponentType().toString().equals(String.class.getCanonicalName()),
                            "`header` is not an Array of String")
            );

            List<Element> elements = new ArrayList<>(allAnnotatedElements);
            for (var filter : filters) {
                var partitionedElement = elements.stream().collect(Collectors.partitioningBy(filter.strategy));
                partitionedElement.get(false).forEach(mistake -> print(ERROR, filter.errorMessage + "[actual]" + ((ArrayType) getHeaderField.apply(mistake).get().asType()).getComponentType(), mistake));
                elements = partitionedElement.get(true);
            }

            elements.forEach(this::generateAutoCSV);
        });
        return true;
    }

    private void generateAutoCSV(Element element) {

        String packageName = null;
        var className = element.toString() + "CSV";
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }
        var simpleClassName = className.substring(lastDot + 1);

        var csvClass = Roaster.create(JavaClassSource.class);
        csvClass.setName(simpleClassName);
        csvClass.setPackage(packageName);

        JavaFileObject builderFile;
        try {
            builderFile = processingEnv.getFiler().createSourceFile(className);
            try (var out = new PrintWriter(builderFile.openWriter())) {
                out.print(csvClass);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
