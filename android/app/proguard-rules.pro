# Barajyq release (R8). Most AndroidX/Compose libs ship their own consumer rules;
# these are extra safety nets for Sign-In and DataStore.

# Keep line numbers for readable Play Console crash stacks.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Credential Manager: provider is instantiated reflectively from play-services-auth.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
-keep class androidx.credentials.** { *; }

# Sign in with Google (googleid) — parsed from Bundle data.
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# DataStore Preferences (protobuf-lite under the hood).
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Enum names are persisted in DataStore (Interest.valueOf / AppLanguage tags).
-keepclassmembers enum kz.barajyq.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# Per-app language holder service (manifest-declared, AppCompat).
-keep class androidx.appcompat.app.AppLocalesMetadataHolderService { *; }
