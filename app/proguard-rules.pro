# Keep serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.adb.controller.**$$serializer { *; }
-keepclassmembers class com.adb.controller.** { *** Companion; }
-keepclasseswithmembers class com.adb.controller.** { kotlinx.serialization.KSerializer serializer(...); }
