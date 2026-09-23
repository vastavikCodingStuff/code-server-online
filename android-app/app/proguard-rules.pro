# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers,allowobfuscation class com.vastavik.codeauth.** {
  <fields>;
  <methods>;
}
