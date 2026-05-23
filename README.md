# Road Damage Detection APP

> A real-time pavement crack detection Android application powered by PyTorch Mobile.

<div style="text-align: center;">
  <img src="https://img.shields.io/badge/Android-9%2B-brightgreen" alt="Android">
  <img src="https://img.shields.io/badge/Kotlin-1.9%2B-blue" alt="Kotlin">
  <img src="https://img.shields.io/badge/PyTorch-Mobile-red" alt="PyTorch">
  <img src="https://img.shields.io/badge/License-GPL-yellow" alt="License">
</div>

---

## 📑 Table of Contents

- [📖 About The Project](#-about-the-project)
- [🔬 Research Background](#-research-background)
- [✨ Features](#-features)
- [🧠 Detection Categories](#-detection-categories)
- [🏗️ System Architecture](#️-system-architecture)
- [📈 Key Performance Metrics](#-key-performance-metrics)
- [📁 Project Structure](#-project-structure)
- [🛠️ Environment Requirements](#️-environment-requirements)
- [🔧 Installation & Building](#-installation--building)
- [📲 Usage Guide](#-usage-guide)
- [🤝 Contributing](#-contributing)
- [📜 License](#-license)
- [📚 Citation & Acknowledgments](#-citation--acknowledgments)
- [🔗 Links](#-links)
- [🌟 Star History](#-star-history)


---

## 📖 About The Project

This project implements a real-time pavement crack detection mobile application based on the **MCANet-Tiny** lightweight classification network. The application leverages on-device edge computing to identify five types of road conditions in real time, making it suitable for deployment on resource-constrained devices such as smartphones and embedded boards.

The core technical framework has been validated on multiple edge platforms including the Huawei P50 Pro smartphone, Radxa Cubic A7A embedded board, and Orange Pi 3B. Performance metrics demonstrate real-time inference speeds reaching up to **166.67 FPS (INT8-quantized, Huawei P50 Pro)** while maintaining approximately **94% classification accuracy**, far exceeding the standard real-time requirement of 30 FPS.

This project originates from the research paper [ESWA-D-25-36177 R4], titled *Edge-Deployable Crack Detection: Synergizing GAN-Based Synthesis with MCANet-Tiny for Real-Time Inspection*, currently under review at *Expert Systems With Applications*. The algorithms, parameters, and performance benchmarks presented in this README are derived from experimental data in that paper.

---

## 🔬 Research Background

Pavement cracks are among the most common forms of road surface damage. If not repaired promptly, cracks can gradually develop into more severe structural failures, posing significant safety hazards. Traditional manual inspection methods rely heavily on human labor and are prone to subjectivity, traffic interference, and low efficiency.

Deep learning techniques, particularly Convolutional Neural Networks (CNNs), have significantly improved detection accuracy and efficiency through powerful automatic feature extraction. However, building high-performance CNN models requires training with large-scale, high-quality datasets, which presents a dual challenge in road engineering applications:

- High-quality crack image datasets are difficult to acquire due to constraints from traffic control, equipment costs, and data collection time.
- Existing data often consist of low-resolution images, failing to meet the quality requirements for model training.

To address these bottlenecks, this research adopts a synergistic co-design approach:

1. **RoadFreq-GAN**: A frequency-aware lightweight GAN incorporating a Frequency-Aware Discriminator and Coordinate Attention (CA) mechanisms. It synthesizes diverse, texture-rich high-resolution crack images, substantially mitigating sample insufficiency under tested conditions.
2. **MCANet-Tiny**: An edge-oriented lightweight classifier refined from the MobileNetV3-Small backbone. It integrates Coordinate Attention with a "Tiny" pruning regime (Depth Adaptation and Global Channel Scaling) to drastically reduce redundancy.

Experimental results demonstrate that MCANet-Tiny achieves **93.40% accuracy** on a challenging cross-dataset test benchmark. Compared to the baseline MobileNetV3-Small model, MCANet-Tiny reduces the number of parameters by **92.1%** (down to 0.12 M) and reduces GFLOPs by **83.3%** (down to 0.02), while increasing accuracy by 5.1 percentage points (from 88.30% to 93.40%). The INT8-quantized model deployed on the Huawei P50 Pro achieves **166.67 FPS** inference speed at **94.26% accuracy**, far exceeding the real-time threshold of 30 FPS.

---

## ✨ Features

| Feature                    | Description                                                  |
|----------------------------|--------------------------------------------------------------|
| 🎥 **Real-time Detection** | Live camera preview with instant crack classification        |
| 🖼️ **Image Detection**    | Detect cracks from selected image files                      |
| 🎬 **Video Detection**     | Process pre-recorded videos for offline analysis             |
| 🗺️ **GPS Location**       | Automatically record location metadata for detection records |
| 📊 **Detection History**   | Local database storage with search and filter capabilities   |
| 📁 **Export Data**         | Export history records in TXT and Excel formats              |
| 🔄 **Model Hot-swap**      | Switch between different PyTorch .ptl models on the fly      |

---

## 🧠 Detection Categories

The model classifies road surface conditions into the following five categories:

| Label | Category               | Description                                                |
|-------|------------------------|------------------------------------------------------------|
| 0     | **Transverse Crack**   | Cracks oriented perpendicular to the road direction        |
| 1     | **Longitudinal Crack** | Cracks oriented parallel to the road direction             |
| 2     | **Alligator Crack**    | Interconnected network of cracks resembling alligator skin |
| 3     | **Pothole**            | Bowl-shaped depression in the road surface                 |
| 4     | **Normal**             | No damage detected                                         |

---

## 🏗️ System Architecture

The application adopts a modular design comprising the following core components:

### 📦 Core Modules

| Component            | File                  | Description                                                            |
|----------------------|-----------------------|------------------------------------------------------------------------|
| **MainActivity**     | `MainActivity.kt`     | Camera preview, capture/video recording, and real-time detection logic |
| **HistoryActivity**  | `HistoryActivity.kt`  | Detection history management with data export/clear capabilities       |
| **DetailActivity**   | `DetailActivity.kt`   | Detailed view for individual detection records                         |
| **HistoryAdapter**   | `HistoryAdapter.kt`   | RecyclerView adapter for displaying detection history                  |
| **AppDatabase**      | `AppDatabase.kt`      | Room database configuration and singleton access                       |
| **HistoryRecordDao** | `HistoryRecordDao.kt` | Database access object with CRUD operations                            |
| **HistoryRecord**    | `HistoryRecord.kt`    | Data entity class representing a detection record                      |
| **DataExporter**     | `DataExporter.kt`     | Export functionality for TXT and Excel formats                         |

### 🗃️ Database Schema

Detection records are stored using **Android Room**, with the following fields:

```kotlin
@PrimaryKey
val timestamp: Long          // Timestamp of detection (primary key)
val imagePath: String        // Path to saved detection image
val className: String        // Detected category name
val confidence: Float        // Confidence score (0-1)
val processingTime: Long     // Model inference time (ms)
val fps: Float?              // Frames per second (for video mode)
val recordType: String       // Record type: "IMAGE" / "VIDEO_FRAME" / "VIDEO_SUMMARY"
val location: String?        // GPS coordinates (if available)
val startLocation: String?   // Start position for video recording
val endLocation: String?     // End position for video recording
```

### 🔄 Data Flow

1. **Image Capture**: CameraX captures frames → PyTorch Mobile inference → Save image + metadata
2. **Location Recording**: GPS-enabled retrieval → Location string formatting → Append to record
3. **History Export**: Database query → DataExporter file generation → MediaStore storage

---

## 📈 Key Performance Metrics

The following metrics are derived from experimental data in the associated research paper:

### Model Size and Computational Complexity

| Metric                   | Baseline (MobileNetV3 Small) | MCANet-Tiny | Reduction   |
|--------------------------|------------------------------|-------------|-------------|
| Parameters (M)           | 1.52                         | 0.12        | **92.1%** ↓ |
| GFLOPs                   | 0.12                         | 0.02        | **83.3%** ↓ |
| Model Size (MB)          | 6.00                         | 0.54        | **91.0%** ↓ |
| Accuracy (Cross-dataset) | 88.30%                       | **93.40%**  | +5.1% ↑     |

### Real-time Inference Performance (INT8-quantized, Huawei P50 Pro)

| Model                  | Accuracy   | FPS        |
|------------------------|------------|------------|
| EfficientNet-Lite0     | 94.18%     | 30.30      |
| ShuffleNetV2 (1.0x)    | 91.82%     | 76.92      |
| MobileNetV3 Large      | 94.45%     | 45.45      |
| MobileNetV3 Small      | 93.87%     | 62.50      |
| **MCANet-Tiny (FP32)** | 94.45%     | 78.52      |
| **MCANet-Tiny (INT8)** | **94.26%** | **166.67** |

### Multi-platform Deployment Performance

| Platform        | Processor         | FPS        | Accuracy |
|-----------------|-------------------|------------|----------|
| Huawei P50 Pro  | Kirin 9000 + 16GB | **166.67** | 94.26%   |
| Radxa Cubic A7A | A733 + 12GB       | 142.86     | 94.26%   |
| Orange Pi 3B    | RK3566 + 2GB      | 78.25      | 93.80%   |

*Note: All platforms are assessed on real-world video streams using the INT8-quantized model.*

---

## 📁 Project Structure

```
RoadCrackDetectionAPP/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/roaddamagedetector/
│   │   │   ├── AppDatabase.kt          # Room database configuration
│   │   │   ├── HistoryRecord.kt        # Entity data class
│   │   │   ├── HistoryRecordDao.kt     # Database access object
│   │   │   ├── HistoryAdapter.kt       # RecyclerView adapter
│   │   │   ├── HistoryActivity.kt      # History management UI
│   │   │   ├── DetailActivity.kt       # Record detail view
│   │   │   ├── MainActivity.kt         # Main activity (camera + detection)
│   │   │   └── DataExporter.kt         # Export (TXT/Excel) functionality
│   │   ├── res/                        # Layouts, drawables, values
│   │   └── assets/                     # PyTorch .ptl model files
│   └── build.gradle.kts                # App-level build configuration
├── gradle/                             # Gradle wrapper configuration
├── build.gradle.kts                    # Project-level build configuration
├── settings.gradle.kts                 # Gradle settings
├── gradle.properties                   # Gradle properties
└── .gitignore                          # Git ignore rules
```

---

## 🛠️ Environment Requirements

### Development Environment
| Requirement        | Version                              |
|--------------------|--------------------------------------|
| **Android Studio** | Ladybug (2024.2.1) or later          |
| **Gradle**         | 8.2+ (Kotlin DSL)                    |
| **Kotlin**         | 1.9.0+                               |
| **Android SDK**    | API Level 26 (Android 8.0) or higher |
| **JDK**            | 17                                   |

### Build Dependencies (actual versions from the project)

The following key dependencies are used in this project (as defined in `libs.versions.toml`):

| Library                                 | Version |
|-----------------------------------------|---------|
| `androidx.core:core-ktx`                | 1.17.0  |
| `androidx.camera:camera-core`           | 1.5.3   |
| `org.pytorch:pytorch_android_lite`      | 2.1.0   |
| `androidx.room:room-runtime`            | 2.8.4   |
| `com.github.bumptech.glide:glide`       | 5.0.5   |
| `org.apache.poi:poi-ooxml`              | 5.5.1   |
| `com.guolindev.permissionx:permissionx` | 1.8.1   |

For the complete list of dependencies, please refer to the `build.gradle.kts` and `libs.versions.toml` files.

```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    
    // CameraX
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    implementation("androidx.camera:camera-video:1.3.0")
    
    // PyTorch Mobile
    implementation("org.pytorch:pytorch_android_lite:1.12.2")
    implementation("org.pytorch:pytorch_android_torchvision_lite:1.12.2")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // MediaStore
    implementation("androidx.media:media:1.7.0")
    
    // Glide (image loading)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // Excel Export (Apache POI)
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
}
```

### Device Requirements

- **Minimum Android Version**: API Level 26 (Android 8.0)
- **Camera**: Rear-facing camera
- **GPS**: Location services (optional, for location tagging)
- **Storage**: 50 MB free space (for application + models)

---

## 🔧 Installation & Building

### 1. Clone the repository

```bash
git clone https://github.com/Winckedy/RoadCrackDetectionAPP.git
cd RoadCrackDetectionAPP
```

### 2. Open in Android Studio

- Open Android Studio
- Select `Open an Existing Project`
- Navigate to and select the cloned directory

### 3. Gradle sync

Android Studio will automatically sync Gradle files. If not, click `File → Sync Project with Gradle Files`.

### 4. Add model file

Copy your PyTorch `.ptl` model file (e.g., `MCANet-Tiny.ptl`) to the `app/src/main/assets/` directory. If the assets folder does not exist, create it manually. The application will automatically load and use models found in this directory.

> **Note**: The application supports loading **any PyTorch .ptl model file** placed in the assets directory, with hot‑swappable selection through the model picker. No model is provided with the source code; you must train or obtain the model according to the methodology described in the associated research paper (see the **Citation** section for details).

### 5. Build and run

- Connect an Android device with USB debugging enabled, or start an emulator
- Click `Run → Run 'app'` (or use the toolbar)
- The application will install and launch automatically

---

## 📲 Usage Guide

### Granting Permissions

On first launch, the application requests the following permissions:

- **Camera**: Required for real-time detection and image capture
- **Location (GPS)**: Optional, for geo-tagging detection records
- **Storage**: Required for saving captured images and exported data (only requested on Android 9 and below)
- **Microphone**: Declared but not actively used (reserved for future video recording features)

All requested permissions must be granted for full functionality.

### Main Interface

After launching the application, the camera preview displays in real time. The interface includes the following controls:

| Control                | Function                                      |
|------------------------|-----------------------------------------------|
| 📷 **Capture Button**  | Capture current frame and run crack detection |
| 🎥 **Record Button**   | Start/stop video recording (video mode)       |
| 🖼️ **Gallery Button** | Select images or videos from gallery          |
| ⚙️ **Menu**            | Access detection history and model settings   |
| 📍 **Location Status** | View GPS status (ready/loading/off)           |

### Switching Models

The application supports hot-swapping between different PyTorch .ptl model files:

1. Tap the model name dropdown in the top toolbar
2. Select a different model from the list
3. The new model is loaded immediately and used for subsequent detection

> **Note**: All `.ptl` model files in the assets directory are automatically detected and listed.

### Detection Process

**Real-time mode:**
1. Position the camera to capture a road surface
2. Tap the **Capture** button
3. The application performs inference and displays the classification result
4. If GPS is enabled, the current location is automatically attached

**Image/Video selection mode:**
1. Tap the **Gallery** button
2. Select one or more images or a video from the gallery
3. The application analyzes the selected media and displays results

### History Management

Tap the **history icon** in the toolbar to access detection history:

- **View details**: Tap any record to view its complete information (image, result, confidence, timestamp, location)
- **Filter by time range**: Use the date/time picker to view records within a specific range
- **Export data**: Export history in TXT or Excel format
- **Clear history**: Delete all records, records older than 24 hours, or records within a specified time range

### Export Data Format

Exported files include the following fields:

| Field           | Description                             |
|-----------------|-----------------------------------------|
| Timestamp       | Detection time (yyyy-MM-dd HH:mm:ss)    |
| Class Name      | Detected category name                  |
| Confidence      | Confidence score (0–100%)               |
| Processing Time | Model inference time (ms)               |
| FPS             | Frames per second (for video detection) |
| Location        | GPS coordinates (longitude, latitude)   |

---

## 🤝 Contributing

Contributions are welcome! If you would like to contribute, please follow these steps:

1. **Fork** this repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a **Pull Request**

### Reporting Issues

If you encounter any issues, please submit an issue report containing:

- Device model and Android version
- Steps to reproduce the issue
- Logcat output (if applicable)
- Screenshots (if applicable)

---

## 📜 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.

You may freely use, modify, and distribute this software, but any derivative work must also be released under the GPLv3. The full license text can be found at [https://www.gnu.org/licenses/gpl-3.0.html](https://www.gnu.org/licenses/gpl-3.0.html).

---

## 📚 Citation & Acknowledgments

This project is part of the research paper:

**Chen, J., Chen, S., Jiang, Z., Que, Y., Chen, Y., & Wang, J. (2026). Edge-Deployable Crack Detection: Synergizing GAN-Based Synthesis with MCANet-Tiny for Real-Time Inspection. *Expert Systems With Applications* (under review).**

### CRediT Author Contribution Statement

| Author                                     | Contribution                                                                                       |
|--------------------------------------------|----------------------------------------------------------------------------------------------------|
| Jia Chen                                   | Analysis, Visualization, Writing-Original Draft, Writing-Review & Editing                          |
| Shuyang Chen                               | Conception, Analysis, Methodology, Visualization, Writing-Original Draft, Writing-Review & Editing |
| **Zhenliang Jiang (Corresponding Author)** | Conception, Analysis, Supervision, Visualization, Writing-Original Draft, Writing-Review & Editing |
| **Yun Que (Corresponding Author)**         | Funding acquisition, Resources, Supervision, Writing-Review & Editing                              |
| Yining Chen                                | Analysis, Methodology, Visualization, Writing-Original Draft                                       |
| Jingwen Wang                               | Analysis, Methodology, Visualization, Writing-Original Draft                                       |

### Institutional Affiliation
- **Jia Chen** — College of Mathematics and Computer Science, Fuzhou University
- **Shuyang Chen, Yining Chen, Jingwen Wang, Yun Que** — College of Civil Engineering, Fuzhou University
- **Zhenliang Jiang** — School of Transportation Science and Engineering, Harbin Institute of Technology / Department of Civil and Environmental Engineering, Hong Kong University of Science and Technology

### Funding
This work was supported by Grant No. 41772297 from the **National Natural Science Foundation of China**.

---

## 🔗 Links

| Resource                         | URL                                                                                                                            |
|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| GitHub Repository                | [https://github.com/Winckedy/RoadCrackDetectionAPP](https://github.com/Winckedy/RoadCrackDetectionAPP)                         |
| Research Article (ScienceDirect) | [https://www.sciencedirect.com/.../pii/S0957417426002925](https://www.sciencedirect.com/science/article/pii/S0957417426002925) |

---

## 🌟 Star History

If you find this project helpful, please consider giving it a ⭐ on GitHub!

[![Star History Chart](https://api.star-history.com/svg?repos=Winckedy/RoadCrackDetectionAPP&type=Date)](https://star-history.com/#Winckedy/RoadCrackDetectionAPP&Date)

---

<p style="text-align: center;">
  Made with ❤️ by the RoadCrackDetectionAPP Team<br>
  © 2025-2026 | GPL-3.0 LICENSE
</p>
