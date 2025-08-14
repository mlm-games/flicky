# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
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

-keepattributes Signature
-keepattributes *Annotation*
-dontobfuscate

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keep class app.flicky.data.repository.AppSettings { *; }
-keepclassmembers class app.flicky.data.repository.AppSettings { *; }
-keep @app.flicky.data.repository.Setting class * { *; }
-keepclasseswithmembers class * {
    @app.flicky.data.repository.Setting <fields>;
}

-keepattributes *Annotation*,EnclosingMethod,Signature,KotlinMetadata

-keep class kotlin.Metadata { *; }

-keep class kotlin.reflect.** { *; }


-keepattributes RuntimeVisibleAnnotations
-keepclassmembers class app.flicky.data.repository.AppSettings {
    @app.flicky.data.repository.Setting *;
}

# Keep all Setting annotations
-keep @interface app.flicky.data.repository.Setting
-keepattributes *Annotation*

# Keep all enum classes used in annotations
-keepclassmembers enum app.flicky.data.repository.SettingCategory { *; }
-keepclassmembers enum app.flicky.data.repository.SettingType { *; }

# Keep all reflection metadata
-keepattributes Signature, InnerClasses
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.** { *; }

-keepclassmembers class app.flicky.data.repository.AppSettings {
    <fields>;
    <methods>;
}

-keep class app.flicky.data.repository.SettingsManager { *; }

-keep class app.flicky.ui.screens.SettingsScreenKt { *; }
