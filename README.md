# Printylan

Printylan is an Android print service for USB printers. Connect a printer to your phone or tablet
with an OTG cable and print from any app that has a Print option, or pick a PDF or image
directly in Printylan.

## Features

- Detects USB printer class devices as soon as they are connected
- Reads each printer's IEEE 1284 Device ID and selects a matching printer language
- Per-model language override for printers that report their languages incorrectly
- Prints PDFs and images (JPEG, PNG and other formats Android can decode) from within the app
- Color and monochrome printing, A4, A5, Letter, Legal and F4 paper
- Banded rendering, so a 600 dpi page needs only a few megabytes of memory
- Reports blocked jobs with a reason, such as "Out of paper"
- Material 3 interface with dynamic color

## Supported printers

| Printer language | Selected when the Device ID lists | Typical printers |
|------------------|-----------------------------------|------------------|
| PDF | `PDF` | Printers with native PDF support |
| PWG Raster | `PWGRaster`, `PWG` | IPP Everywhere and AirPrint capable printers |
| PCL raster | `PCL`, `PCL3`, `PCL3GUI`, `PCL5`, `PCL5E`, `PCL5C` | HP and HP compatible lasers and office inkjets |
| Canon IVEC | `IVEC` | Canon PIXMA G-series and similar inkjets, tested on the G2030 |

Host-based ("GDI") printers that only accept a proprietary language, such as the HP LaserJet 1020
or most Canon LBP lasers, are not supported.

## Requirements

- Android 8.0 (API 26) or newer with USB host support
- A USB OTG cable or adapter

## Installation

1. Download the latest APK from the [Releases](../../releases) page and install it.
2. Open Printylan and tap **Open print settings**, then turn on **Printylan**.
3. Connect the printer and tap **Allow access** on its card when asked.

Release builds are signed with a temporary key. To install a newer release, uninstall the
previous version first.

## Usage

- **From another app:** choose Print, then select the USB printer in the Android print dialog.
- **From Printylan:** tap **Print a file**, choose a PDF or image, and confirm the settings in the
  print dialog.
- **Printer language:** each printer card shows the language Printylan detected and the languages
  the printer reports. Select a different language there if the automatic choice does not print.

## Building

```
./gradlew :core:model:test :core:driver:test   # JVM tests, no Android SDK required
./gradlew :app:assembleDebug                    # requires the Android SDK (compileSdk 35)
```

## Project structure

```
core/model    Domain types shared by all modules (pure Kotlin)
core/driver   Printer languages: PDF, PWG Raster, PCL raster, Canon IVEC (pure Kotlin)
app           Print service, USB access, PDF rendering, Material 3 UI
docs          Design documentation
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the print pipeline, driver selection,
threading, error handling and known limitations.
