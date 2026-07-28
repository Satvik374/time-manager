# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.kairo.timemanager.** {
    *** Companion;
}
-keepclasseswithmembers class com.kairo.timemanager.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kairo.timemanager.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
