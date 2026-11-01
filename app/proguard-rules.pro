# Keep kotlinx.serialization generated serializers and Companion entry points for session and account models.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keep,includedescriptorclasses class <1>$$serializer { *; }
-keepclassmembers class com.swaralab.domain.model.** {
    *** Companion;
}
