# BarCodeNcrypt R8 keep-rules.
#
# The release build runs R8 with shrinking + obfuscation; every reflective surface
# below needs an explicit keep. Adding new reflective libraries? Drop them here.

# Keep stack-trace mapping useful for crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# Hilt / Dagger
# Hilt's generated components are referenced reflectively at injection sites.
# -----------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep,allowobfuscation @dagger.hilt.android.HiltAndroidApp class *
-keep,allowobfuscation @dagger.hilt.android.AndroidEntryPoint class *
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_Factory { *; }
-keep class **_Factory$* { *; }

# -----------------------------------------------------------------------------
# Room
# Room entities are queried via generated impl classes that reflectively read
# field annotations.
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-keepclassmembers class * {
    @androidx.room.PrimaryKey *;
    @androidx.room.ColumnInfo *;
    @androidx.room.Embedded *;
    @androidx.room.Relation *;
}

# -----------------------------------------------------------------------------
# Tink
# AEAD / KeysetReader uses reflective lookups during AeadConfig.register() and
# during subtle X25519 / HKDF / AesGcmJce instantiation.
# -----------------------------------------------------------------------------
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# -----------------------------------------------------------------------------
# argon2kt — Argon2id KDF used by Plan 2 bootstrap
# JNI bindings are loaded reflectively.
# -----------------------------------------------------------------------------
-keep class com.lambdapioneer.argon2kt.** { *; }
-dontwarn com.lambdapioneer.argon2kt.**

# -----------------------------------------------------------------------------
# Firebase Auth + App Check (Play Integrity provider in release)
# Firebase classes look up Service implementations by reflection.
# -----------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# -----------------------------------------------------------------------------
# ML Kit barcode scanning
# Native model loading happens via reflective registration.
# -----------------------------------------------------------------------------
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# -----------------------------------------------------------------------------
# ZXing — QR generation
# -----------------------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# -----------------------------------------------------------------------------
# Gson — JSON (de)serialization of the skipped-key map and any future structs.
# Strip Gson-targeted annotations and keep model fields reflectively.
# -----------------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-dontwarn sun.misc.**

# -----------------------------------------------------------------------------
# SQLCipher (net.zetetic:android-database-sqlcipher 4.5.x)
# The library wires JNI through SQLiteDatabase.loadLibs reflectively.
# -----------------------------------------------------------------------------
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# -----------------------------------------------------------------------------
# AzNavRail — DSL + reflective scope lookups
# -----------------------------------------------------------------------------
-keep class com.hereliesaz.aznavrail.** { *; }
-dontwarn com.hereliesaz.aznavrail.**

# -----------------------------------------------------------------------------
# Kotlin coroutines + reflection
# -----------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keepclassmembers class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-dontwarn kotlinx.coroutines.**

# -----------------------------------------------------------------------------
# App: keep the public crypto surface intact so test vectors + on-wire format
# stay byte-stable even after obfuscation reorders fields.
# -----------------------------------------------------------------------------
-keep class com.hereliesaz.barcodencrypt.crypto.MessageHeader { *; }
-keep class com.hereliesaz.barcodencrypt.crypto.MessageHeader$Companion { *; }
-keep class com.hereliesaz.barcodencrypt.crypto.MessageEnvelope { *; }
-keep class com.hereliesaz.barcodencrypt.crypto.RatchetState { *; }
-keep class com.hereliesaz.barcodencrypt.crypto.SkippedKeyId { *; }
