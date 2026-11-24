# Project R8 / ProGuard optimization and keep rules for Cipher Virtual.
# Enabled via release { isMinifyEnabled = true; isShrinkResources = true } in app/build.gradle.kts.

# Preserve stack trace metadata for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata, generics, and runtime annotations for reflection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Preserve @Keep annotated classes and members accessed via reflection
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <init>(...);
}

# Preserve Parcelable CREATOR instances and Serializable fields/methods
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Preserve enum values()/valueOf() methods accessed reflectively
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Preserve JNI native methods and their owning class descriptors
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Room database, entities, and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *

# App data models, hardware profiles, security, and telemetry packages
-keep class com.example.data.model.** { *; }
-keep class com.example.data.hardware.DeviceProfile { *; }
-keep class com.ciphernodes.ciphervirtual.security.** { *; }
-keep class com.ciphernodes.ciphervirtual.monitoring.** { *; }

# Google Play Integrity & GMS Tasks
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.android.play.core.integrity.**

# Kotlin Coroutines debug agent stubs
-dontwarn kotlinx.coroutines.debug.**
