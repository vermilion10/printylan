# Printylan architecture

Printylan plugs USB printers into the Android print framework. You connect a printer to your
phone or tablet with an OTG cable, and every app with a Print menu (Chrome, Files, Photos, Docs)
can send pages to it. Printylan never shows its own print dialog. Android owns the dialog, and
Printylan supplies the printer list, the capabilities, and the bytes that go down the cable.

## How a page reaches the printer

```
 Any app                Android print framework                 Printylan
 -------                -----------------------                 ---------
 PrintManager.print() -> Print dialog + spooler  -- binds -->   PrintylanPrintService
                          |                                        |
                          | discovery session                      +-- UsbDiscoverySession
                          |   "which printers exist?"              |     UsbPrinterScanner (attach/detach)
                          |   "what can this one do?"              |     PrinterProbe (Device ID, port status)
                          |                                        |
                          | onPrintJobQueued(PDF)                  +-- PrintJobProcessor
                          v                                              1. spool PDF to cache
                                                                         2. open USB, read Device ID
                                                                         3. pick driver
                                                                         4. PDF -> printer language
                                                                         5. bulk OUT transfer
                                                                                 |
                                                                                 v
                                                                           USB printer
```

1. You open the print dialog. Android binds `PrintylanPrintService` and asks for a
   `PrinterDiscoverySession`.
2. `UsbDiscoverySession` publishes every attached USB device that exposes a printer class
   interface (class 7, subclass 1).
3. You select a printer. The session asks for USB permission if Printylan lacks it, reads the
   IEEE 1284 Device ID and port status, picks a driver, and hands the driver's capabilities
   (paper sizes, resolutions, color, duplex) to the dialog.
4. You tap Print. The spooler renders the document to PDF and calls `onPrintJobQueued`.
5. `PrintJobProcessor` copies the PDF to the cache directory, converts it with the driver, and
   streams the result to the printer's bulk OUT endpoint in 16 KiB chunks.
6. The job completes once the last byte leaves the phone. USB printer class devices report no
   per job status, so Printylan cannot tell when the paper comes out.

## Modules

| Module | Kind | Contents |
|--------|------|----------|
| `:core:model` | Kotlin/JVM | Domain types: `PrinterCapabilities`, `JobTicket`, `DeviceId`, `PortStatus`. No Android imports. |
| `:core:driver` | Kotlin/JVM | Page description languages: PDF passthrough, PWG Raster, PCL raster, Canon IVEC. PackBits, dithering, driver selection. Unit tested on the JVM. |
| `:app` | Android | USB access, PDF rendering, the `PrintService`, and the Material 3 settings screen. |

The two core modules compile without the Android SDK. You can test a driver change in seconds
with `./gradlew :core:driver:test`, no device or emulator needed.

### `:app` packages

| Package | Responsibility |
|---------|----------------|
| `usb` | `UsbPrinter` (interface and endpoint selection), `UsbPrinterScanner` (live device list), `UsbPermissions` (suspend wrapper around the system dialog), `UsbPrinterConnection` (class requests: GET_DEVICE_ID, GET_PORT_STATUS), `UsbBulkOutputStream` (chunked writes with stall handling), `BackChannelReader` (drains the bulk IN endpoint during a job), `PrinterProbe`. |
| `render` | `BandedPdfPage`: renders a PDF page 128 rows at a time and serves rows to drivers. |
| `service` | `PrintylanPrintService`, `UsbDiscoverySession`, `PrintJobProcessor`, `AndroidPrintMapping` (framework types to model types and back). |
| `data` | `DriverPreferences`: the language a user picked for a printer model. |
| `ui` | `MainActivity`, `MainViewModel`, `PrintersScreen`, `FilePrinter` (prints a picked PDF or image through `PrintManager`), and the theme. |

`AppContainer` in `PrintylanApp.kt` wires these together by hand. The graph holds six objects,
and a DI framework would cost more build time than it saves. Swap in Hilt once the graph grows
past what fits on one screen.

## Drivers

A printer tells you which languages it speaks in the `CMD` field of its Device ID, for example
`MFG:HP;MDL:LaserJet P1102;CMD:PJL,PCL,PWGRaster;`. `DriverSelector` picks the first match in
this order:

| Priority | Driver | Accepts `CMD` tokens | Output | Color | Duplex |
|----------|--------|----------------------|--------|-------|--------|
| 1 | `PdfPassthroughDriver` | `PDF` | The spooled PDF, unchanged | Printer decides | Printer decides |
| 2 | `PwgRasterDriver` | `PWGRASTER`, `PWG` | PWG 5102.4 raster, 8 bit sGray or sRGB | Yes | Long and short edge |
| 3 | `PclRasterDriver` | `PCL`, `PCL3`, `PCL3GUI`, `PCL5`, `PCL5E`, `PCL5C` | PCL 5 raster, 1 bit, PackBits | Monochrome | Long and short edge |
| 4 | `CanonIvecDriver` | `IVEC` | IVEC XML commands with one PWG Raster file (8 bit sRGB) per page | Color and monochrome | No |

### Canon IVEC

Canon PIXMA models such as the G2030 report only `CMD:IVEC`. They do not accept Canon's older
BJ raster commands. A job is a sequence of IVEC XML commands on the bulk OUT endpoint:

1. `StartJob`, then `SetJobConfiguration` with the current date and time.
2. `SetConfiguration` with the paper size (for example `iso_a4_210x297mm`), plain paper
   (`stationery`), and `color` or `monochrome`.
3. For each page, `SetPageConfiguration` (whether another page follows) and `SendData` with the
   byte count, followed by a complete PWG Raster file for that page.
4. `EndJob`.

The printer halftones the pages itself, so the driver always sends 8 bit sRGB and leaves the
color mode to `SetConfiguration`. Each page covers only the printable area the printer reports
in its `GetCapability` response (4800 x 6826 pixels at 600 dpi for A4, placed 3.0 mm from the
top and 3.4 mm from the left edge). `SendData` announces the page size before the data, so each
compressed page is written to a temporary file first. The command sequence matches Canon's
cnijfilter2 driver, and unit tests compare every command with output captured from it.

The user can override the choice per printer model on the main screen. That covers printers
with a missing or wrong `CMD` field.

Drivers come in two shapes:

- `PassthroughDriver` receives the PDF as a stream and forwards it.
- `RasterDriver` receives `RasterPage` objects. A `RasterPage` hands out rows top to bottom,
  one call per row, so the renderer never holds a full page in memory.

A `RasterDriver` can also narrow `printableArea` to the part of the sheet it sends; the page
is still laid out on the full sheet and cropped, not scaled. Drivers whose language has no copy
count set `encodesCopies` to false, and the processor sends every page once per copy.

To add a language, implement one of the two interfaces in `:core:driver`, register it in
`DriverSelector.all`, and add a `DriverId`. The service and UI pick it up without changes.

### Memory

An A4 page at 600 dpi measures 4960 x 7016 pixels. As one ARGB bitmap it needs 139 MB, which
kills the print service on low end phones. `BandedPdfPage` renders 128 rows at a time into a
2.5 MB bitmap by shifting the `PdfRenderer` transform matrix. PWG Raster compares each row with
the next one to encode repeated lines, so it keeps two rows in memory. PCL keeps one row plus
the dithering error buffer.

### Orientation and scaling

The spooler sizes each PDF page to the paper the user chose, rotated for landscape documents.
Printers feed paper in portrait, so `BandedPdfPage` rotates landscape pages by 90 degrees and
scales each page to fit the sheet, centered. It then crops the result to the driver's printable
area, which matches the margins the print dialog showed the app, so content keeps its size.

## Threads

- The framework calls `PrintService`, `PrinterDiscoverySession`, and every `PrintJob` method on
  the main thread. Both the session and the job processor run their coroutines on
  `Dispatchers.Main.immediate` and switch away only for blocking work.
- All USB traffic goes through `AppContainer.usbDispatcher`, a single thread view of
  `Dispatchers.IO`. A status probe from the dialog therefore never interleaves with the bytes
  of a running job, and jobs print one after another.
- File copies run on `Dispatchers.IO`.

## Errors and stalls

Bidirectional printers can send data back on their bulk IN endpoint. The Canon G2030 queues a
status message there every few seconds, and once the queue is full it accepts jobs but feeds the
paper through blank. `BackChannelReader` therefore reads and discards the bulk IN data for as
long as a job stream is open, and for three seconds after the last byte.

A printer that runs out of paper stops reading from its bulk endpoint. `bulkTransfer` then
times out after 10 seconds. `UsbBulkOutputStream` asks its `StallHandler` whether to retry. The
job processor's handler reads GET_PORT_STATUS, marks the job blocked with a reason such as "Out
of paper", and keeps retrying until data flows or
the user cancels. When data flows again, the job returns to started.

Other failures end the job with `PrintJob.fail(message)`. The message comes from string
resources, so you can translate it.

| Failure | What the user sees |
|---------|-------------------|
| Printer unplugged before the job starts | "The printer is no longer connected" |
| User denies the USB permission dialog | "USB access was denied" |
| No driver matches and no override is set | "Unsupported printer language" |
| Transfer error mid job | "Printing failed" (details in logcat under `PrintJobProcessor`) |

## Permissions

Android requires explicit user consent before an app opens a USB device. Printylan asks in two
places:

- **Plug in.** The manifest registers `MainActivity` for `USB_DEVICE_ATTACHED` with a printer
  class filter. Android offers to open Printylan when you connect a printer, and accepting that
  prompt grants access to the device.
- **On demand.** `UsbPermissions.request()` shows the system dialog from the print dialog or the
  main screen.

Printylan needs no network permission. It never touches the internet.

## UI

One screen, built with Jetpack Compose Material 3:

- A large top app bar that collapses as you scroll.
- A setup card that links to Settings, Printing, where you turn the service on.
- One outlined card per attached printer with its name, USB ids, a status label (Ready, Out of
  paper, Offline, No access), and a dropdown to force a printer language.
- An empty state that explains the OTG cable when no printer is attached.
- An extended floating action button, **Print a file**, that opens the system file picker for a
  PDF or image and hands it to the Android print dialog. It collapses to an icon while the list
  scrolls.

It follows Material 3 rules: dynamic color on Android 12+ with the baseline scheme as fallback,
color roles from `MaterialTheme.colorScheme` only, edge to edge drawing with scaffold insets,
spacing tokens on an 8dp grid (`ui/theme/Spacing.kt`), 16dp margins on compact windows and 24dp
from medium up, and content capped at 840dp wide on tablets and desktops.

The same activity doubles as the print service settings screen (`android:settingsActivity`), so
the gear icon in Settings, Printing opens it.

## Known limits

- **Host based printers.** Many cheap lasers and inkjets (HP LaserJet 1020, most Canon LBP,
  Samsung SPL) speak only a proprietary language that the vendor's desktop driver produces.
  Printylan cannot drive them until someone writes a driver for that language.
- **Canon IVEC paper types.** `CanonIvecDriver` always asks for plain paper. Photo paper and
  borderless printing are not offered.
- **IPP over USB** (interface protocol 4) printers expose a better path: full IPP with job status
  and real capabilities. Printylan ignores protocol 4 interfaces today and uses protocol 1 or 2
  when the device offers one.
- **Capabilities come from the driver, not the printer.** A USB printer class device cannot
  report its paper sizes, so each driver advertises a fixed list: A4, Letter, Legal, and A5 at
  300 and 600 dpi, plus F4 at 600 dpi for Canon IVEC printers.
- **Cancel mid page.** Cancelling stops the stream between USB chunks. The printer may print a
  partial page or wait for data until its own timeout.
- **PCL margins.** PCL places raster at the logical page origin, about 1/6 inch in from the paper
  edge, so the right edge of a full bleed page gets clipped.
- **No job status after transfer.** The job reads Completed once Printylan sends the last byte.

## Roadmap

1. **IPP over USB transport.** Speak HTTP over the protocol 4 bulk endpoints and reuse an IPP
   client for real capabilities, job status, and PDF or PWG Raster submission.
2. **More languages.** PostScript (wrap each raster page in an image operator), ESC/P 2 for Epson
   inkjets, ESC/POS for receipt printers, Apple URF.
3. **Test page.** A button on each printer card that prints a generated calibration page
   through `PrintManager`, so you can check a driver choice without another app.
4. **Job recovery.** On `onConnected`, fail jobs the framework still lists as started after a
   process death, and restart queued ones.
5. **Per printer defaults.** Let you set default paper size and resolution per model in
   `DriverPreferences`.
6. **Instrumented tests.** Drive `PrintJobProcessor` against a fake `UsbPrinterConnection` that
   records bytes, and assert on the stream each driver produces from real PDFs.

## References

- USB Device Class Definition for Printing Devices 1.1
- IEEE 1284-2000, Device ID string format
- PWG 5102.4, PWG Raster Format
- PWG 5101.1, Media Standardized Names
- HP PCL 5 Printer Language Technical Reference Manual
- Canon cnijfilter2 (GPL source), for the IVEC command sequence and PWG page format
- Android: `android.printservice.PrintService`, `android.hardware.usb.UsbManager`,
  `android.graphics.pdf.PdfRenderer`
