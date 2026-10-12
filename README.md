# Mustang Markdown Preview Tool

MustangMDView is an Android Markdown reader built with Kotlin and Jetpack Compose. Import documents into a persistent bookshelf, scan a folder you authorize, and tap a title to open a full-screen preview.

## Features

- CommonMark rendering with tables, task lists, strikethrough, and automatic links.
- Headings, nested lists, block quotes, inline code, and code blocks with bundled syntax highlighting for recognized languages.
- Horizontally scrollable code blocks and wide tables, with selectable body text.
- HTTPS images and relative images from authorized folders, with loading placeholders, error feedback, and retry.
- A dark purple theme, responsive reading width, and support for system font scaling.
- Persistent bookshelf entries and document permissions. Duplicate document URIs are ignored; documents with the same name at different locations remain separate.
- Single and multiple selection, select all, pinning, and removal. Removing an entry only removes it from the bookshelf; the original file is preserved.
- An adaptive launcher icon featuring a horse head and book pages.

The current app interface is primarily in Simplified Chinese.

## Requirements

- Android 8.0 (API 26) or later to run the app.
- JDK 17 to build the project.
- Android SDK 35 and Android SDK Build Tools 35.0.0.

The project pins Gradle 8.14.4, Android Gradle Plugin 8.13.0, Kotlin 2.2.20, and Compose BOM 2025.09.00. Dependency versions are defined in `gradle/libs.versions.toml`.

## Build and install

Clone the repository:

```sh
git clone https://github.com/josebergo/MustangMarkdownPreviewTool.git
cd MustangMarkdownPreviewTool
```

Open the project in Android Studio, or configure your SDK location in a local `local.properties` file. For example, on Windows:

```properties
sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk
```

On Windows, build the debug APK and run unit tests and lint:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On macOS or Linux:

```sh
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Gradle Wrapper downloads the `8.14.4-all` distribution from a mirror and verifies its SHA-256 checksum. This distribution includes sources for Android Studio's Kotlin DSL support.

Google Maven dependencies use the mirror configured in `gradle.properties`. To use the official repository, append the following option to your Gradle command:

```sh
-PgoogleMirror=https://dl.google.com/dl/android/maven2/
```

Maven Central uses its default repository. Configure any required proxy locally through Gradle JVM options.

## Usage

1. Use **Add documents** to import one or more `.md` or `.markdown` files, or **Scan folder** to authorize and scan a folder and its subfolders.
2. Tap a document title to read it. Links open through system apps; long-press text to select and copy it.
3. Relative images require access to their containing folder. Folder imports retain this context. For individually imported documents, use the folder action in the reader or the image error panel to grant access.
4. Tap non-interactive body text to show or hide the reader toolbar. Use system Back to return to the bookshelf.
5. Use **Manage**, select entries, then choose **Pin** or **Remove**. Pinned entries appear first and retain their relative order. Use **Bookshelf** or system Back to leave management mode.

The action names above are English translations of the current interface labels.

## Verification

Run instrumented tests with a connected device or running emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Check reader text and syntax token contrast on Windows:

```powershell
powershell -File scripts/verify-contrast.ps1
```

Unit tests cover document rules, bookshelf ordering, and Markdown rendering. Instrumented tests cover document access, image loading, reader interaction, and ViewModel state. The debug build includes a permission-protected test DocumentsProvider; the release build excludes it.

## Limitations

- Documents must use UTF-8, with or without a BOM, and may be at most 8 MB.
- Image source files may be at most 12 MB. Decoding downsamples images to a maximum dimension of 1600 pixels; loading is serialized to limit peak memory use.
- Bitmap formats supported by Android, such as PNG, JPEG, WebP, and GIF, are supported. GIFs display a static frame; SVG images are not supported.
- Remote images must use HTTPS. Local images require folder authorization through Android's Storage Access Framework; the app does not request all-files access.
- Raw HTML is escaped as readable text. Document scripts are not executed.
- Math formulas, Mermaid diagrams, custom Markdown dialects, editing, and cloud sync are not implemented.
- Providers that do not offer persistent document permissions may require documents to be imported again after restart.

## Project structure

```text
app/src/main/java/com/mustang/mdview/
  data/             Document access, bookshelf persistence, and image loading
  ui/bookshelf/     Bookshelf screen and state
  ui/reader/        Reader screen, Markdown rendering, and state
  ui/theme/         Compose theme
app/src/main/assets/reader/  Reader styles and bundled scripts
app/src/test/               Unit tests
app/src/androidTest/        Instrumented tests and fixtures
app/src/debug/              Test document provider
gradle/                     Dependency catalog and Gradle Wrapper
scripts/                    Contrast verification script
third_party/                Third-party license texts
```

## License

See [LICENSE](LICENSE) for the project license and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party attribution and license details.
