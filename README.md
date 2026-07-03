# EMC Field Assistant

EMC Field Assistant is an Android app for organizing photos during EMC test work. It helps record photos by project, sample/model, test item, and photo category while the test is happening, instead of sorting and renaming photos manually after the test.

The app is designed around a workflow similar to the project/test-item logic used by an older internal EMC64 workflow, but this repository does not include EMC64 or any EMC64 automation code.

## Use Case

Typical use:

1. Enter the project/order information, manufacturer, sample name, model, and configuration.
2. Select a test item such as CE, RE, ESD, Surge, CS, or sample nameplate.
3. Take photos directly inside the selected test item.
4. Copy the organized photo folders from the phone when the test is finished.

Photos are saved under the Android Pictures directory in a structured folder layout, making later report preparation easier.

## Features

- Project/order, manufacturer, sample, model, and configuration fields.
- CSV import for model and configuration options.
- Existing project folder selection for continuing or补拍 existing work.
- Grouped EMC test items.
- Photo categories such as overview, detail, vertical polarity, and horizontal polarity.
- CameraX-based photo capture.
- Volume-down hardware shutter support while inside the app.
- MediaStore saving to a user-visible Pictures folder.

## Build

Requirements:

- JDK 17
- Android SDK
- Gradle wrapper included in this repository

Build debug APK:

```bash
./gradlew assembleDebug
```

The debug APK will be generated under:

```text
app/build/outputs/apk/debug/
```

## Main Source

```text
app/src/main/java/com/agentos/emcfieldassistant/MainActivity.kt
```

## Privacy

This repository intentionally excludes device dumps, test photos, logs, internal EMC64 files, and local build outputs.

## License

MIT License. See `LICENSE`.
