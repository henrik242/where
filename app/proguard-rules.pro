# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep the data classes used by Gson
-keep class no.synth.where.data.FylkeGeoJSON { *; }
-keep class no.synth.where.data.FylkeFeature { *; }
-keep class no.synth.where.data.FylkeGeometry { *; }
-keep class no.synth.where.data.Track { *; }
-keep class no.synth.where.data.SavedPoint { *; }

# Keep GSON specific classes
-keep class com.google.gson.stream.** { *; }
