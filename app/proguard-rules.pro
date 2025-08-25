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

-dontobfuscate

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}


-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature
-keepattributes *Annotation*

 # Keep AppSettings reflection and its annotation
 -keep @interface app.flicky.data.repository.Setting
 -keep class app.flicky.data.repository.AppSettings { *; }
 -keepclassmembers class app.flicky.data.repository.AppSettings {
     @app.flicky.data.repository.Setting <fields>;
 }

 -keep,allowobfuscation,allowshrinking class app.flicky.data.model.**$$serializer { *; }
 -keepclassmembers class app.flicky.data.model.** {
     *** Companion;
 }
 -keepclasseswithmembers class app.flicky.data.model.** {
     kotlinx.serialization.KSerializer serializer(...);
 }


 -keep class app.flicky.install.InstallResultReceiver { *; }

 # -keep class com.google.gson.stream.** { *; }
