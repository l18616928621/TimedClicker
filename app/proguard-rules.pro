# ProGuard rules for TimedClicker
-keep class com.workbuddy.timedclicker.** { *; }
-keepclassmembers class com.workbuddy.timedclicker.** { *; }

# Keep accessibility service
-keep public class * extends android.accessibilityservice.AccessibilityService

# Kotlin
-keepattributes *Annotation*
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
