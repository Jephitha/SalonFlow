# Keep data classes used with Gson/JSON
-keepclassmembers class com.salonflow.app.NotificationEntry { *; }

# Keep database inner classes
-keepclassmembers class com.salonflow.app.SalonDatabase$* { *; }

# Keep Compose
-dontwarn androidx.compose.**

# Keep Google Sign-In
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.common.api.** { *; }

# Keep AccountManager usage
-keep class android.accounts.AccountManager { *; }
-keep class android.accounts.AccountManagerFuture { *; }
