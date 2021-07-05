package self.chera.annotations;

import com.google.auto.service.AutoService;

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

    private void writeBuilderFile(String className, Map<String, String> setterMap) throws IOException {

        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }

        String simpleClassName = className.substring(lastDot + 1);
        String builderClassName = className + "Builder";
        String builderSimpleClassName = builderClassName.substring(lastDot + 1);

        var builderFile = processingEnv.getFiler().createSourceFile(builderClassName);

        try (var out = new PrintWriter(builderFile.openWriter())) {

            if (packageName != null) {
                out.print("package ");
                out.print(packageName);
                out.println(";");
                out.println();
            }

            out.print("public class ");
            out.print(builderSimpleClassName);
            out.println(" {");
            out.println();

            out.print("    private ");
            out.print(simpleClassName);
            out.print(" object = new ");
            out.print(simpleClassName);
            out.println("();");
            out.println();

            out.print("    public ");
            out.print(simpleClassName);
            out.println(" build() {");
            out.println("        return object;");
            out.println("    }");
            out.println();

            setterMap.forEach((methodName, argumentType) -> {

                out.print("    public ");
                out.print(builderSimpleClassName);
                out.print(" ");
                out.print(methodName);

                out.print("(");

                out.print(argumentType);
                out.println(" value) {");
                out.print("        object.");
                out.print(methodName);
                out.println("(value);");
                out.println("        return this;");
                out.println("    }");
                out.println();
            });

            out.println("}");
        }
    }
}
