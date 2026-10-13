# Room generated code
-keep class androidx.room.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# BouncyCastle ships via reflection in a few places
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep model classes used by the binary packet codec if reflection is ever added
-keep class com.wifichat.mesh.model.** { *; }
-keep class com.wifichat.mesh.data.entity.** { *; }
