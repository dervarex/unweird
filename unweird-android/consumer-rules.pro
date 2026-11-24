# The desktop backend in unweird-core refers to LWJGL, which Android apps don't include.
-dontwarn org.lwjgl.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
# Screens, components and their hooks are found by reflection (field order index, @Shortcut, the first screen's type).
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keep class * extends com.dervarex.unweird.Widget { *; }
-keep class * extends com.dervarex.unweird.UnweirdActivity
-keep class com.dervarex.unweird.AndroidVg { native <methods>; }
