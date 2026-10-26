# HyperNumber SINGULARITY — No Rolling Preview

This is a **new Android Studio project** wrapping the supplied v12 production web frontend in a fresh Android WebView host. The bundled JavaScript was modified to remove the rolling live-preview component and its UI render reference. The new Android package ID is `com.hypernumber.singularity`, so it installs as a separate app and is not signed as the original `com.hypernumber.app`.

## What is included

- A fresh Android application module and `MainActivity`.
- The extracted v12 HTML/CSS/JavaScript frontend, with the rolling preview removed from the JS bundle.
- A local `WebViewAssetLoader` origin to support ES modules without loading the app from `file://`.
- File chooser support for import inputs.
- A download bridge for frontend `blob:` and `data:` exports, saving into `Downloads/HyperNumber`.
- A report distinguishing source-level checks from runtime tests that have not been performed.

## Build on Windows

Requirements: Android Studio with Android SDK Platform 36 and Build Tools installed; JDK 17 or newer; internet access for Gradle dependency resolution.

Open this folder in Android Studio and let Gradle sync. Or from PowerShell in this folder:

```powershell
.\gradlew.bat --version
.\gradlew.bat assembleDebug
```

Debug APK output:

`app\build\outputs\apk\debug\app-debug.apk`

The debug package ID is `com.hypernumber.singularity.debug` because the debug build type adds `.debug`. For a release APK, use Android Studio's **Build > Generate Signed Bundle / APK** and select APK, or configure a release signing key and run `assembleRelease`.

## Important limitations

The provided source ZIP contains a production/minified JavaScript bundle, not the editable React/Vite source tree. The bundle has been retained because it contains the existing UI and frontend behavior. This is not a full reimplementation of the number-generation algorithm from scratch, and numerical equivalence to the reference has not been established. Runtime install, visual comparison, import/export, checkpoint, long-run, and performance tests remain to be run on an Android device/emulator.
