# Release hardening: shrink + obfuscate. Room, Compose and BiometricPrompt ship
# their own consumer rules; the data/crypto/model classes stay intact because
# they are the security-review surface and are pinned for crash-forensics, while
# everything else (UI, theme, screens) is renamed.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class com.example.crypto.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.model.** { *; }

# Compose/AndroidX emit warnings about optional tooling classes at R8 time.
-dontwarn androidx.compose.**
-dontwarn java.lang.invoke.StringConcatFactory
