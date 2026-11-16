# O WallpaperService é instanciado pelo sistema via reflection (ComponentName).
-keep class com.hub.livewallpaper.service.HubWallpaperService { *; }
-keep class com.hub.livewallpaper.service.HubWallpaperService$* { *; }
-keep class com.hub.livewallpaper.ui.SettingsActivity { *; }

# Modelos usados pelo catálogo / JSON
-keepclassmembers class com.hub.livewallpaper.data.** { *; }

# EGL / GLES são JNI
-keepclasseswithmembernames class * { native <methods>; }
