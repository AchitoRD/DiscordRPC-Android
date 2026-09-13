-keep class com.discordrpc.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
