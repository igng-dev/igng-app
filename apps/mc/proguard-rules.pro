-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class net.igng.core.**$$serializer { *; }
-keep,includedescriptorclasses class net.igng.mcstatus.**$$serializer { *; }
-keepclassmembers class net.igng.core.** {
    *** Companion;
}
-keepclassmembers class net.igng.mcstatus.** {
    *** Companion;
}
-keepclasseswithmembers class net.igng.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class net.igng.mcstatus.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
