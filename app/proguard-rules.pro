-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.nahabit.tracker.** { *; }
-keepclasseswithmembers class com.nahabit.tracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
