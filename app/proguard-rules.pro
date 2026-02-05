# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Chaquopy/Python classes
-keep class com.chaquo.python.** { *; }

# Keep OkHttp classes
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Jsoup classes
-keep class org.jsoup.** { *; }
