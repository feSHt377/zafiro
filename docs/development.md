# Development environment

Zafiro is a multi-module Android application built with Kotlin and Compose. The
`app` module hosts the UI; `business/api` exposes business contracts and the other
`business` modules implement them. `agent-runtime` embeds Python through Chaquopy.
The libraries under `libs/` are vendored source, so no submodule checkout is needed.

## Toolchain

| Tool | Project requirement |
| --- | --- |
| JDK | 17 (also used by JVM module toolchains) |
| Gradle | 9.3.1, downloaded by the checked-in wrapper |
| Android Gradle Plugin | 9.1.1 |
| Android SDK platforms | 35, 36 and 37.0; libterm compiles against 35, Okia against 36 |
| Android SDK Build Tools | 36.0.0 |
| Python | 3.11, matching the Chaquopy runtime |
| Android platform-tools | `adb`, needed only for device debugging |

Install SDK packages through Android Studio's SDK Manager or the current Android CLI:

```text
android sdk install platforms/android-35 platforms/android-36 platforms/android-37.0 build-tools/36.0.0 platform-tools
```

Use the current SDK package index: legacy repository XML files may omit SDK 37.
The existing Material 3 and Compose dependencies require `compileSdk = 37`.

Copy `local.properties.example` to `local.properties` and fill in absolute paths
using forward slashes. `sdk.dir` selects the SDK; `python.executable` selects the
host interpreter for both Chaquopy packaging and Python runtime tests. The
Windows environment script uses `java.home` to select the JDK. Configure Android
Studio's Gradle JDK to the same JDK 17 directory.

## Windows PowerShell

Run from the repository root:

```powershell
. ./scripts/dev-env.ps1
./gradlew.bat --version
./gradlew.bat :app:assembleDebug --no-daemon
./gradlew.bat :agent-runtime:testPythonRuntime --no-daemon
```

The script changes only the current shell environment and stores Gradle caches
under `.tools/gradle`. Both `.tools/` and `local.properties` are ignored by Git.
If PowerShell blocks local scripts, use `Set-ExecutionPolicy -Scope Process Bypass`
in that shell before dot-sourcing the script.

## Linux and macOS

Set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to your SDK directory. Configure
`sdk.dir` and `python.executable` in `local.properties`, then run:

```bash
./gradlew :app:assembleDebug --no-daemon
./gradlew :agent-runtime:testPythonRuntime --no-daemon
```

## Build and debug

A debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Building
does not require a connected device or a release signing key. Debug includes
`arm64-v8a` for devices and `x86_64` for emulators.

Run `:app:adbReverse` explicitly when a connected device needs access to local
development services. It forwards ports 8788, 1234, 4004, 51337 and 51338. Ordinary
builds do not change device port mappings. Device installation is a separate
operation.

Release builds require your own signing key and the `RELEASE_*` properties
documented in the README. Keep keys and passwords out of the repository.

## Troubleshooting

- **Java 8 is selected:** load `scripts/dev-env.ps1` or correct `JAVA_HOME`;
  verify the launcher JVM with the wrapper's `--version` command.
- **Python is missing or has the wrong version:** point `python.executable` at
  Python 3.11. A Windows Store `python3.exe` alias is not a working interpreter.
- **SDK platform missing:** install platforms 35, 36 and 37.0 in the SDK selected
  by `sdk.dir`; another SDK installation does not satisfy that path.
- **Downloads fail:** check access to Google Maven, Maven Central, the Gradle
  distribution host, Chaquopy, JitPack and PyPI. The Windows wrapper already
  supports the Windows system proxy; `ZAFIRO_NO_PROXY=1` disables that detection.
  The Python packaging process uses `HTTP_PROXY` / `HTTPS_PROXY`.
