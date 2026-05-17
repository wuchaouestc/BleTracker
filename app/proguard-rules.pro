# Bluetooth Tracker ProGuard Rules

# Chaquopy Python
-keep class com.chaquo.python.** { *; }

# Room
-keep class com.example.bletracker.data.db.entity.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
