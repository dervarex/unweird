package com.dervarex.unweird.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Records, for every subclass of {@code com.dervarex.unweird.Container}, the widget fields in source order.
 * Java reflection does not guarantee field order, so the runtime reads this index instead.
 *
 * <p>Output: {@value #INDEX}, one line per class: {@code binary.ClassName=field1,field2}.
 * Classes are matched by name, so this module does not depend on unweird-core.
 */
@SupportedAnnotationTypes("*")
public final class FieldOrderProcessor extends AbstractProcessor {

    static final String INDEX = "META-INF/unweird/fields.idx";
    private static final String CONTAINER = "com.dervarex.unweird.Container";
    private static final String WIDGET = "com.dervarex.unweird.Widget";
    private static final String SKIP = "com.dervarex.unweird.annotation.Skip";

    private final Map<String, List<String>> entries = new TreeMap<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        TypeElement container = processingEnv.getElementUtils().getTypeElement(CONTAINER);
        TypeElement widget = processingEnv.getElementUtils().getTypeElement(WIDGET);

        if (container != null && widget != null) {
            Types types = processingEnv.getTypeUtils();
            TypeMirror containerType = types.erasure(container.asType());
            TypeMirror widgetType = types.erasure(widget.asType());
            for (Element root : round.getRootElements()) {
                scan(root, types, containerType, widgetType);
            }
        }

        if (round.processingOver() && !entries.isEmpty()) {
            write();
        }
        return false; // never claim annotations; other processors (Lombok) still see them
    }

    private void scan(Element element, Types types, TypeMirror containerType, TypeMirror widgetType) {
        if (!(element instanceof TypeElement type)) {
            return;
        }
        if (type.getKind() == ElementKind.CLASS && types.isSubtype(types.erasure(type.asType()), containerType)) {
            record(type, types, widgetType);
        }
        for (TypeElement nested : ElementFilter.typesIn(type.getEnclosedElements())) {
            scan(nested, types, containerType, widgetType);
        }
    }

    private void record(TypeElement type, Types types, TypeMirror widgetType) {
        List<String> fields = new ArrayList<>();
        // javac returns enclosed elements in source order; that's the order we persist.
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (field.getModifiers().contains(Modifier.STATIC) || hasSkip(field)) {
                continue;
            }
            if (types.isAssignable(types.erasure(field.asType()), widgetType)) {
                fields.add(field.getSimpleName().toString());
            }
        }
        String binaryName = processingEnv.getElementUtils().getBinaryName(type).toString();
        entries.put(binaryName, fields);
    }

    private static boolean hasSkip(VariableElement field) {
        for (AnnotationMirror mirror : field.getAnnotationMirrors()) {
            if (((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().contentEquals(SKIP)) {
                return true;
            }
        }
        return false;
    }

    private void write() {
        try {
            FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", INDEX);
            try (Writer out = new java.io.OutputStreamWriter(file.openOutputStream(), StandardCharsets.UTF_8)) {
                for (Map.Entry<String, List<String>> entry : entries.entrySet()) {
                    out.write(entry.getKey() + "=" + String.join(",", entry.getValue()) + "\n");
                }
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "unweird: could not write " + INDEX + ": " + e);
        }
    }
}
