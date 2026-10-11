# Release instrumentation shares this app's Kotlin runtime. R8 cannot infer
# every entry point used only by the separately built AndroidJUnitRunner and
# Espresso APK. Keep the observed cross-APK boundaries, not all of Kotlin.
# These rules also apply to the APK verified by the external host journey.

# AndroidJUnitRunner's TestDirCalculator constructs lambdas and lazy values.
-keep class kotlin.jvm.internal.Lambda { *; }
-keep class kotlin.LazyKt { *; }

# Espresso tracing calls this inherited default-argument bridge through the
# CollectionsKt facade; retaining only the declaring member loses the facade.
-keep class kotlin.collections.CollectionsKt {
    public static java.lang.String joinToString$default(...);
}

# Espresso's TreeTraversalIterable iterator extends this app-provided class.
-keep class kotlin.collections.AbstractIterator { *; }

# Espresso's matcher description obtains a Kotlin class token and its name.
-keep class kotlin.jvm.internal.Reflection {
    public static kotlin.reflect.KClass getOrCreateKotlinClass(java.lang.Class);
}
-keep interface kotlin.reflect.KClass {
    public java.lang.String getSimpleName();
}
