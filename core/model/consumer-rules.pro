-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class net.igng.core.**$$serializer { *; }
-keepclassmembers class net.igng.core.** {
    *** Companion;
}
-keepclasseswithmembers class net.igng.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
