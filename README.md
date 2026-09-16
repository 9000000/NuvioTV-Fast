<div align="center">

  <img src="assets/brand/app_logo_wordmark.png" alt="NuvioTV" width="300" />
  <br />
  <br />

  <p>
    <strong>NuvioTV Enhanced Edition</strong> — A multi-source optimized Android TV fork focusing on UI/UX refinement, low-RAM stability, TorrServer torrent streaming, and snappy navigation.
  </p>

  <p>
    <a href="https://github.com/9000000/NuvioTV/releases/latest">Latest Releases</a> ·
    <a href="#-key-optimizations--features">Key Features</a> ·
    <a href="#1--torrserver-integration-native-p2p-torrent-streaming">TorrServer</a> ·
    <a href="#-installation">Installation</a> ·
    <a href="#%EF%B8%8F-build-from-source">Build from Source</a>
  </p>

</div>

---

## 🌟 About This Fork

This repository is a **custom fork of NuvioTV** that aggregates, curates, and enhances features from multiple reputable open-source sources, including:
* The official upstream project: [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV)
* Memory-tuning & low-RAM optimizations: [hackerslash/NuvioTV-Lite](https://github.com/hackerslash/NuvioTV-Lite)
* Various community fixes, translations, and performance enhancements

### Core Objectives:
- **UI/UX Refinement**: Delivering a visually modern, intuitive TV interface with multiple home screen layouts, smooth transitions, and stable remote D-pad focus without cursor jumps.
- **Hardware-Aware Memory Management**: Intelligent physical RAM detection (`DeviceMemoryTier`) tailored for budget and low-RAM devices (≤ 2GB RAM, e.g., Mi Box, Fire TV Stick, Chromecast HD, Onn Box), completely preventing Low Memory Killer (LMK) crash issues.
- **Native Torrent Streaming with TorrServer**: Integrated P2P engine enabling direct streaming of torrents and magnet links with both embedded and remote server options.
- **Zero-Latency Cold Start**: Restores user sessions immediately on launch to render the UI instantly (0s freeze), validating authentication tokens asynchronously in the background.
- **Seamless In-App OTA Updates**: Built-in automatic updater pulling releases directly from this fork's GitHub Releases with a persistent signing keystore.

---

## ⚡ Key Optimizations & Features

### 1. 🧲 TorrServer Integration (Native P2P Torrent Streaming)
* **Torrent Streaming**: Stream torrents and magnet links on-the-fly without requiring third-party debrid accounts.
  * **Remote & External Server Support**: Seamlessly connect to your existing home NAS, PC, Docker, or LAN/Android TorrServer instance (`http://<ip>:8090`) with optional HTTP Basic Authentication.
* **Smart Multi-File Picker**: Automatically selects the main video track or presents an in-player file selector for multi-episode series and season packs.
* **Preloading & Adaptive Buffering**: Pre-caches configurable data blocks before starting playback, ensuring instant seek response and jitter-free streaming even on unstable swarms.
* **Real-time Torrent HUD**: Monitor live download/upload speeds, active peer/seed counts, and cache fill progress directly in the player statistics overlay.
* **Granular Settings Control**: Easily configure server endpoints, toggle preloading, save torrents to database, or enable global search trackers (GST) straight from TV Settings.

### 2. 🎨 UI/UX & Jetpack Compose Performance
* **Modular Home Layouts**: Support for multiple home screen styles (**Modern Home**, **Classic Home**, **Grid Home**) accompanied by modular, categorized settings.
* **Snappy Navigation**: Shortened transition latencies tailored for television remote controls, making browsing responsive and crisp.
* **Stable Remote Focus**: Deterministic item keys prevent focus loss or card jumping when catalogs reload or page in new content.
* **Recomposition & Render Tuning**:
  * Gradient shaders and scrims cached via `drawWithCache` and `remember`, avoiding per-frame heap allocations.
  * Hero banner logos decoded at fixed screen-height bounds, preventing continuous re-decoding during scale animations.
  * Optional RGB_565 image decoding mode saving up to 50% poster bitmap memory while preserving crisp visual quality.

### 3. 🧠 Hardware Physical RAM Tiering
* **Physical RAM Single Source of Truth (`DeviceMemoryTier`)**: Identifies actual installed hardware RAM via `/proc/meminfo` and `ActivityManager.MemoryInfo().totalMem` rather than JVM heap size assumptions (where `largeHeap` reports 512MB on both 2GB and 8GB devices).
* **Safe Memory Ceiling**: Enforces an explicit 250MB buffer ceiling on low-RAM devices, preventing kernel LMK terminations during heavy playback.
* **Parallel Connection Guard (`clampParallel`)**: Clamps connection count and chunk sizes when Performance Mode is enabled on resource-constrained devices.

### 4. 🚀 Fast Startup & Network Concurrency
* **Instant Cold Start (`AuthManager`)**: Emits the restored user session prior to network verification, enabling instant UI layout while validating in the background.
* **Main Thread Offloading (`SupabaseModule`)**: Eliminates synchronous `runBlocking` calls during client initialization.
* **Bounded Addon Stream Concurrency**: Uses Semaphores to limit concurrent stream scraper queries (capped at 3 on low-RAM vs 8 on high-RAM), preventing network/memory spikes when many addons are installed.
* **Catalog Caching & Request Deduplication**: In-memory LRU cache (5-minute TTL, 48 entries) combined with `ConcurrentHashMap<String, Deferred>` deduplication and off-main DTO mapping.

### 5. 🎬 Player & Post-Play Buffers
* **Adaptive Dynamic Buffering**: Target buffer duration (15s – 40s) dynamically computed from physical memory budget, eliminating rebuffering stalls on high-bitrate content (4K UHD Remux).
* **On-Demand Post-Play Prefetching**: Low-RAM devices resolve only the currently visible recommendation card instead of eagerly fetching details and trailers for 4 candidates concurrently during active 4K playback.
* **Lightweight HUD Stats**: Directly reads cached RAM info, eliminating repetitive `ActivityManager` polling per second.

---

## 📥 Installation

### Download Android TV APK
Download the latest release APK from **[GitHub Releases](https://github.com/9000000/NuvioTV/releases/latest)** matching your device's architecture:

* **`armeabi-v7a`**: Most 32-bit Android TV boxes and sticks (Mi Box, Fire TV Stick, etc.).
* **`arm64-v8a`**: Modern 64-bit Android TV devices.
* **`universal`**: Universal build compatible with all architectures.

> [!TIP]
> This build features an **In-app Updater** with a persistent release keystore. After initial installation, future updates will be delivered and installed seamlessly from within the app.

---

## 🛠️ Build from Source

Requirements: **Android Studio**, **JDK 17 or 21**, and the **Android SDK**.

```bash
# Clone the repository
git clone https://github.com/9000000/NuvioTV.git
cd NuvioTV

# Build Full Debug APK
./gradlew :app:assembleFullDebug

# Build Full Release APK
./gradlew :app:assembleFullRelease
```

---

## 🙏 Credits & Acknowledgments

This project is built upon the wonderful contributions of the open-source community:
* Upstream project: **[NuvioTV](https://github.com/NuvioMedia/NuvioTV)** by NuvioMedia and contributors.
* Memory optimizations: **[NuvioTV-Lite](https://github.com/hackerslash/NuvioTV-Lite)** by `@hackerslash`.
* Torrent streaming engine: **[TorrServer](https://github.com/YouROK/TorrServer)** by YouROK.
* Community fixes, localizations, and suggestions from Stremio and Android TV developers.

---

## 📄 License

Licensed under the [GNU General Public License v3.0](./LICENSE).
