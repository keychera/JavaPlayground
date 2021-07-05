package self.chera.annotations;

import com.google.auto.service.AutoService;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// doing this tutorial: https://www.baeldung.com/java-annotation-processing-builder
@SupportedAnnotationTypes("self.chera.annotations.BuilderProperty")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class BuilderProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        annotations.forEach(annotation -> {
            var annotatedElement = roundEnv.getElementsAnnotatedWith(annotation);
            var annotatedMethods = annotatedElement.stream().collect(Collectors.partitioningBy(
                    element -> ((ExecutableType) element.asType()).getParameterTypes().size() == 1 && element
                            .getSimpleName().toString().startsWith("set")));

            var wronglyAnnotatedMethods = annotatedMethods.get(false);
            wronglyAnnotatedMethods.forEach(mistake -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@self.chera.annotations.BuilderProperty must be applied to a set<Something> method " + "with a single argument",
                    mistake));

            var setters = annotatedMethods.get(true);
            if (!setters.isEmpty()) {
                String className = ((TypeElement) setters.get(0).getEnclosingElement()).getQualifiedName().toString();
                var setterNamesToArgumentNames = setters.stream().collect(Collectors
                        .toMap(setter -> setter.getSimpleName().toString(),
                                setter -> ((ExecutableType) setter.asType()).getParameterTypes().get(0).toString()));
                try {
                    writeBuilderFile(className, setterNamesToArgumentNames);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        return true;
    }

    private void writeBuilderFile(String className, Map<String, String> setterNamesToArgumentType) throws IOException {

        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }

        String simpleClassName = className.substring(lastDot + 1);
        String builderClassName = className + "Builder";
        String builderSimpleClassName = builderClassName.substring(lastDot + 1);

        var builderFile = processingEnv.getFiler().createSourceFile(builderClassName);

        final var builderClass = Roaster.create(JavaClassSource.class);
        builderClass.setPackage(packageName).setName(builderSimpleClassName).setPublic()
                .addField().setPrivate().setType(simpleClassName).setName("object").setLiteralInitializer("new " + simpleClassName + "();").getOrigin() // object holder
                .addMethod().setPublic().setReturnType(simpleClassName).setName("build").setBody("return object;"); // build()

        setterNamesToArgumentType.forEach((setterName, argumentType) -> builderClass // every setter method
                .addMethod().setPublic().setReturnType(builderSimpleClassName).setName(setterName)
                .setBody(String.format("object.%s(value);return this;", setterName))
                .addParameter(argumentType, "value"));

        try (var out = new PrintWriter(builderFile.openWriter())) {
            out.print(builderClass);
        }
    }
}
