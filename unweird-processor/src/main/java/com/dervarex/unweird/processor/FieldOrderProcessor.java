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
import java.util.TreeSet;

/**
 * Writes two indexes the runtime reads instead of scanning or reflecting:
 * <ul>
 *   <li>{@value #INDEX}: for every subclass of {@code com.dervarex.unweird.Container}, its widget fields
 *       in source order (reflection doesn't guarantee field order). One line per class:
 *       {@code binary.ClassName=field1,field2}.</li>
 *   <li>{@value #DEFAULTS}: every class annotated {@code @Default}, one binary class name per line.</li>
 * </ul>
 * Classes are matched by name, so this module does not depend on unweird-core.
 */
@SupportedAnnotationTypes("*")
public final class FieldOrderProcessor extends AbstractProcessor {

    static final String INDEX = "META-INF/unweird/fields.idx";
    static final String DEFAULTS = "META-INF/unweird/defaults.idx";
    private static final String CONTAINER = "com.dervarex.unweird.Container";
    private static final String COMPONENT = "com.dervarex.unweird.Component";
    private static final String DEFAULT = "com.dervarex.unweird.annotation.Default";
    private static final String WIDGET = "com.dervarex.unweird.Widget";
    private static final String SKIP = "com.dervarex.unweird.annotation.Skip";

    private final Map<String, List<String>> entries = new TreeMap<>();
    private final Set<String> defaults = new TreeSet<>();

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

        if (round.processingOver()) {
            if (!entries.isEmpty()) {
                write(INDEX, entries.entrySet().stream().map(e -> e.getKey() + "=" + String.join(",", e.getValue())).toList());
            }
            if (!defaults.isEmpty()) {
                write(DEFAULTS, List.copyOf(defaults));
            }
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
        if (hasAnnotation(type, DEFAULT)) {
            recordDefault(type, types);
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

    private void recordDefault(TypeElement type, Types types) {
        TypeElement component = processingEnv.getElementUtils().getTypeElement(COMPONENT);
        boolean isComponent = component != null && types.isSubtype(types.erasure(type.asType()), types.erasure(component.asType()));
        if (type.getKind() != ElementKind.CLASS || !isComponent) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@Default only works on classes that extend Component", type);
            return;
        }
        if (type.getModifiers().contains(Modifier.ABSTRACT)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@Default components can't be abstract; one shared instance is created from it", type);
            return;
        }
        defaults.add(processingEnv.getElementUtils().getBinaryName(type).toString());
    }

    private static boolean hasSkip(VariableElement field) {
        return hasAnnotation(field, SKIP);
    }

    private static boolean hasAnnotation(Element element, String annotation) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().contentEquals(annotation)) {
                return true;
            }
        }
        return false;
    }

    private void write(String resource, List<String> lines) {
        try {
            FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", resource);
            try (Writer out = new java.io.OutputStreamWriter(file.openOutputStream(), StandardCharsets.UTF_8)) {
                for (String line : lines) {
                    out.write(line + "\n");
                }
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "unweird: could not write " + resource + ": " + e);
        }
    }
}
