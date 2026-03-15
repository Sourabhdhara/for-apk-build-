# QuickChat ProGuard rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.sdck.quickchat.** { *; }
