-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class net.igng.core.updater.**$$serializer { *; }
-keepclassmembers class net.igng.core.updater.** {
    *** Companion;
}
-keepclasseswithmembers class net.igng.core.updater.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class net.igng.core.updater.IgngUpdateFileProvider {
    void <init>();
}
