package com.dervarex.unweird.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiles small sources with the processor, against minimal stand-ins for the unweird-core types. */
class FieldOrderProcessorTest {

    private static final List<JavaFileObject> CORE_STUBS = List.of(
            source("com.dervarex.unweird.Widget", "package com.dervarex.unweird; public abstract class Widget {}"),
            source("com.dervarex.unweird.Container", "package com.dervarex.unweird; public abstract class Container extends Widget {}"),
            source("com.dervarex.unweird.Component", "package com.dervarex.unweird; public abstract class Component extends Container {}"),
            source("com.dervarex.unweird.Text", "package com.dervarex.unweird; public class Text extends Widget {}"),
            source("com.dervarex.unweird.annotation.Skip", "package com.dervarex.unweird.annotation; public @interface Skip {}"),
            source("com.dervarex.unweird.annotation.Default", "package com.dervarex.unweird.annotation; public @interface Default {}"));

    @TempDir
    Path out;

    private record Result(boolean success, List<String> errors) {
    }

    private Result compile(JavaFileObject... sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> units = new ArrayList<>(CORE_STUBS);
        units.addAll(List.of(sources));
        var task = compiler.getTask(null, null, diagnostics, List.of("-d", out.toString(), "-proc:full"), null, units);
        task.setProcessors(List.of(new FieldOrderProcessor()));
        boolean success = task.call();
        List<String> errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .toList();
        return new Result(success, errors);
    }

    @Test
    void recordsWidgetFieldsInSourceOrder() throws IOException {
        Result result = compile(source("app.Form", """
                package app;
                import com.dervarex.unweird.*;
                import com.dervarex.unweird.annotation.Skip;
                public class Form extends Component {
                    Text zebra = new Text();
                    static Text ignoredStatic = new Text();
                    @Skip Text skipped = new Text();
                    String notAWidget = "";
                    Text apple = new Text();
                    public static class Inner extends Component { Text only = new Text(); }
                }
                """));
        assertTrue(result.success(), result.errors().toString());
        // the core stand-ins are compiled in the same run, so keep only the app's entries
        List<String> index = Files.readAllLines(out.resolve(FieldOrderProcessor.INDEX)).stream()
                .filter(line -> line.startsWith("app."))
                .toList();
        assertEquals(List.of("app.Form=zebra,apple", "app.Form$Inner=only"), index);
    }

    @Test
    void indexesDefaultComponents() throws IOException {
        Result result = compile(source("app.Sidebar", """
                package app;
                @com.dervarex.unweird.annotation.Default
                public class Sidebar extends com.dervarex.unweird.Component {}
                """));
        assertTrue(result.success(), result.errors().toString());
        assertEquals(List.of("app.Sidebar"), Files.readAllLines(out.resolve(FieldOrderProcessor.DEFAULTS)));
    }

    @Test
    void rejectsDefaultOnNonComponents() throws IOException {
        Result result = compile(source("app.NotAComponent", """
                package app;
                @com.dervarex.unweird.annotation.Default
                public class NotAComponent {}
                """));
        assertFalse(result.success());
        assertTrue(result.errors().getFirst().contains("extend Component"), result.errors().toString());
    }

    @Test
    void rejectsAbstractDefaults() throws IOException {
        Result result = compile(source("app.Base", """
                package app;
                @com.dervarex.unweird.annotation.Default
                public abstract class Base extends com.dervarex.unweird.Component {}
                """));
        assertFalse(result.success());
        assertTrue(result.errors().getFirst().contains("abstract"), result.errors().toString());
    }

    private static JavaFileObject source(String className, String code) {
        URI uri = URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension);
        return new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }
}
