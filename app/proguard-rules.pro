-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.nahabit.tracker.** { *; }
-keepclasseswithmembers class com.nahabit.tracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
