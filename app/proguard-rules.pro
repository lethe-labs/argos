# Receivers are bound by class name from AndroidManifest.xml. AAPT normally
# generates keep rules for them, but losing one would silently stop Argos from
# logging in release builds only, so they are pinned explicitly.
-keep class io.github.lethe_labs.argos.platform.ArgosAdminReceiver { *; }
-keep class io.github.lethe_labs.argos.platform.BootReceiver { *; }

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
