# Espresso 3.7.0 brings error_prone_annotations 2.30.0. Its CLASS-retained
# IncompatibleModifiers metadata mentions a JDK compiler-model enum; Android
# never executes this annotation member. Scope the diagnostic exception to
# the instrumented-test APK, leaving app shrinking rules unchanged.
-dontwarn javax.lang.model.element.Modifier

# AndroidJUnitRunner discovers test entry points reflectively. Keep only this
# test class and its JUnit methods in the test APK; the app remains optimized.
-keep class staff.android.delivery.DeliveryJourneyTest {
    public <init>();
    @org.junit.Test <methods>;
}
-keepattributes RuntimeVisibleAnnotations
