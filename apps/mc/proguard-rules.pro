-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class net.igng.mcstatus.**$$serializer { *; }
-keepclassmembers class net.igng.mcstatus.** {
    *** Companion;
}
-keepclasseswithmembers class net.igng.mcstatus.** {
    kotlinx.serialization.KSerializer serializer(...);
}
