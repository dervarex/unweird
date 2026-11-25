package com.dervarex.unweird.intellij

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/** The inspections against small stand-ins for unweird's classes. */
class InspectionsTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor() = JAVA_21

    override fun setUp() {
        super.setUp()
        myFixture.addClass("package com.dervarex.unweird; public abstract class Widget {}")
        myFixture.addClass("package com.dervarex.unweird; public abstract class Container extends Widget {}")
        myFixture.addClass("package com.dervarex.unweird; public abstract class Screen extends Container {}")
        myFixture.addClass("package com.dervarex.unweird; public abstract class Component extends Container {}")
        myFixture.addClass("""package com.dervarex.unweird;
            public class Text extends Widget {
                public Text(String text) {}
                public Text(java.util.function.Supplier<String> text) {}
            }""")
        myFixture.addClass("""package com.dervarex.unweird;
            public class Image extends Widget { public Image(String resource) {} }""")
        myFixture.addClass("package com.dervarex.unweird.annotation; public @interface Shortcut { String value(); }")
        myFixture.addClass("package com.dervarex.unweird.annotation; public @interface Default { }")
    }

    fun testMisspelledShortcutIsFixed() {
        myFixture.enableInspections(ShortcutInspection())
        myFixture.configureByText("Notes.java", """
            import com.dervarex.unweird.Screen;
            import com.dervarex.unweird.annotation.Shortcut;
            class Notes extends Screen {
                @Shortcut(<error descr="Unknown key \"Ctl\": unweird would read this shortcut as something else. Did you mean \"Ctrl+S\"?">"C<caret>tl+S"</error>)
                void save() {}
                @Shortcut("Ctrl+N")
                void create() {}
            }
        """.trimIndent())
        myFixture.checkHighlighting()
        myFixture.launchAction(myFixture.findSingleIntention("Change to \"Ctrl+S\""))
        assertTrue(myFixture.editor.document.text.contains("@Shortcut(\"Ctrl+S\")"))
    }

    fun testTextFromAChangingFieldBecomesALambda() {
        myFixture.enableInspections(LiveTextInspection())
        myFixture.configureByText("Hello.java", """
            import com.dervarex.unweird.Screen;
            import com.dervarex.unweird.Text;
            import com.dervarex.unweird.Image;
            class Hello extends Screen {
                String name = "";
                static final String APP = "Notes";
                Text greeting = new Text(<warning descr="Evaluated once: this won't show later changes to 'name'. unweird re-reads a lambda after every event.">"Hi " + na<caret>me</warning>);
                Text title = new Text("Welcome to " + APP);
                Image logo = new Image("/logo-" + name + ".png");
            }
        """.trimIndent())
        myFixture.checkHighlighting()
        myFixture.launchAction(myFixture.findSingleIntention("Make it live: () -> ..."))
        assertTrue(myFixture.editor.document.text.contains("new Text(() -> \"Hi \" + name)"))
    }

    fun testScreensNeedAConstructorWithoutParameters() {
        myFixture.enableInspections(ConstructorInspection())
        myFixture.configureByText("Detail.java", """
            import com.dervarex.unweird.Screen;
            class Detail extends Screen {
                class <error descr="unweird creates screens itself and can't create an inner class: make it static">Inner</error> extends Screen {}
            }
            class <error descr="unweird creates screens itself and needs a constructor without parameters">Oth<caret>er</error> extends Screen {
                Other(String id) {}
            }
            abstract class Base extends Screen {
                Base(String id) {}
            }
        """.trimIndent())
        myFixture.checkHighlighting()
        myFixture.launchAction(myFixture.findSingleIntention("Add a constructor without parameters"))
        assertTrue(myFixture.editor.document.text.contains("Other() {"))
    }
}
