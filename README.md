# M3E Calc

A Material 3 **Expressive** calculator for Android: keypad, scientific panel,
history, unit & currency converter, settings, about, credits and a download
manager for updates. Everything is stored on the device — no accounts, no
analytics, no network calls except the optional update check.

Portrait phone app (designed against 412 × 892 dp), light and dark, following
the system setting, with dynamic colour on Android 12+ and the Purple scheme as
the fallback.

---

## Screens

| Screen | What it does |
| --- | --- |
| **Home** | The calculator: 200 dp calculation surface with expression / divider / result, a floating toolbar row (History, Converter, Advanced, Settings, About) with a tonal backspace button filling the rest of the line, and five connected button rows. |
| **Advanced** | A panel over the keypad with `sin cos tan asin acos atan ln log √ x² x^y 1/x \|x\| n! π e mod Ans` plus a DEG/RAD toggle. |
| **History** | Every calculation, stored in SQLite. Search, tap to reuse a result, long-press to copy or delete (with Undo), clear all, choose how many entries to keep. |
| **Converter** | Six categories on a floating toolbar — currency, weight, data, length, temperature, number base. The keypad builds an expression (so `2×1024` works), the value is converted live, recent conversions are stored, currency rates are editable. |
| **Settings** | Appearance (System / Light / Dark), Theme (Dynamic ↔ Purple, with a live 45 sp preview), button press-effect strength and haptics, angle unit, decimal places, thousands separator, history retention, data controls. |
| **About** | App mark, version and build date, and the way out to the update check, GitHub and credits. |
| **Special thanks** | Credits, plus a **Share app** extended FAB. |
| **Download manager** | Checks the GitHub releases API for a newer version, downloads the APK with progress, installs or deletes it. |

Navigation follows the design: History slides in from the left, Converter,
Settings, About, Special thanks and the download manager slide in from the
right, and back — the button, the gesture and the arrow in the app bar — plays
the entry transition in reverse.

## Design system

* **Colour** — every colour in the app is read from a `ColorScheme` of Material
  3 roles (`primary`, `surfaceContainerHigh`, `outlineVariant`, …). Light and
  dark use the exact values from the design; on Android 12+ the roles are
  derived from the wallpaper palette (`system_accent{1,2,3}_*`,
  `system_neutral{1,2}_*`, mapped from tone to palette index).
* **Shape** — pill buttons, 8 dp inner corners inside a connected group, 20 dp
  cards, 28 dp dialogs, 48 dp calculation surfaces, 16 dp extended FAB corners.
* **Type** — the standard M3 type scale (display → label), Roboto, weights
  400/500/700.
* **Motion** — damped-spring interpolators (`SpringInterpolator`) used for
  screen transitions, sheet open/close, switch thumbs and press release. Press
  feedback is a ripple plus a slight scale, and the depth of that scale is the
  "Press effect" slider in Settings (40% by default).
* **Icons** — the real **Material Symbols Rounded** font, subset to the glyphs
  this app draws (16 kB, see `tools/make-font.py`).

## Project layout

```
app/
  version.properties                 version name / code (read by the build)
  src/main/AndroidManifest.xml
  src/main/assets/fonts/             subset of Material Symbols Rounded
  src/main/res/                      strings, themes, launcher icon
  src/main/kotlin/com/flexteam/m3ecalc/
    MainActivity.kt                  screen host, insets, back stack, transitions
    Screen.kt  App.kt                screen contract, singletons
    theme/                           colour roles, dynamic colour, type, motion
    ui/                              buttons, connected groups, floating toolbar,
                                     top app bar, slider, switch, dialog, snack bar
    calc/                            tokenizer + parser + evaluator + formatter
    data/                            settings, SQLite storage, unit tables
    net/                             update check, download, file provider
    screens/                         the seven screens
tools/
  setup-toolchain.sh                 downloads the build toolchain (cached)
  build-apk.sh                       resources → R → Kotlin → dex → pack → sign
  test-calc.sh                       JVM tests for the engine and the converter
  pack-apk.py                        aligned APK writer (zipalign equivalent)
  make-font.py                       regenerates the icon font subset
  calc-tests/                        the tests themselves
```

## Building

The project builds **without Gradle**, because the environment it was built in
has no route to `maven.google.com`, `repo1.maven.org` or `dl.google.com`. The
build tools come from the package registries that mirror them instead — see the
header of `tools/setup-toolchain.sh`:

| tool | source |
| --- | --- |
| JDK 17 runtime | PyPI `jdk4py` |
| Kotlin compiler 1.9.25 (+ stdlib) | npm `kotlin-compiler` |
| aapt2 2.20 (linux x86_64) | npm `aaptjs3` |
| d8 8.2.2, ecj, apksigner, `android.jar` (API 34) | npm `@drxiaozhi/minapk` |

The same three commands run in CI: [`tools/ci/build.yml`](tools/ci/build.yml) is
a ready GitHub Actions workflow. It lives outside `.github/workflows/` because
the bot account used to push this branch is not allowed to create workflow
files — copy it to `.github/workflows/build.yml` to switch CI on.

```sh
tools/setup-toolchain.sh          # once, ~180 MB cached in /tmp/m3e-toolchain
tools/test-calc.sh                # 79 JVM tests: engine + converter
tools/build-apk.sh                # -> M3E-Calc-<version>-release-signed.apk
```

`build-apk.sh` runs aapt2 → ecj (`R.java`) → kotlinc (against `android.jar`,
no JDK on the classpath) → d8 (`min-api 26`, release) → `pack-apk.py` (aligned,
`resources.arsc` STORED) → apksigner (v2 + v3), then verifies the signature and
prints `aapt2 dump badging`.

**Signing.** A keystore is generated on first build at `keystore/release.jks`
(alias `m3ecalc`, password `m3ecalc123`) and is git-ignored. To ship updates,
keep that file or point the build at your own:

```sh
KEYSTORE=/path/to/release.jks KEY_ALIAS=mykey \
KEY_STOREPASS=... KEY_KEYPASS=... tools/build-apk.sh
```

## A note on the component library

The design asks for the standard Jetpack Compose `material3` components
(including the Expressive APIs). Compose, the AndroidX libraries and the
Android Gradle Plugin are all Maven artifacts, and none of them can be
downloaded in this sandbox, so the app is written against the framework
(`android.widget`, `android.graphics`, `android.animation`) and ships a small
component library in `ui/` that implements the same components to the same
tokens: `M3Button` (filled / tonal / elevated / outlined / text, M3 size scale),
connected button groups, `FloatingToolbar`, `M3TopBar`, `M3Slider` (16 dp track,
4 × 44 dp handle), `M3Switch`, `M3Dialog`, snack bars and dividers. Roles,
shapes, the type scale and the spring motion match the spec; the components are
drawn with framework views instead of Compose.

The trade-off is deliberate: a real, installable, signed APK over source that
could not be compiled here. Porting the screens to Compose is mechanical — the
colour roles, type scale, shapes and spring specs are already isolated in
`theme/`.

## Data

* `m3ecalc.db` (SQLite): calculations, conversions, editable currency rates.
* `m3e-settings.xml` (SharedPreferences): appearance, theme, press effect,
  haptics, angle unit, precision, separator, history limit, remembered units.
* Downloads land in the app's external files directory and are served to the
  installer through a local `content://` provider.

The bundled currency rates are a reference table, per 1 USD, editable from the
converter; they are never refreshed behind your back, and the UI shows when
they were last set.

## Licence

MIT — see [LICENSE](LICENSE).
