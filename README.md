# CatfishCounter

Aplikasi Android untuk mendeteksi dan menghitung ikan lele secara real-time menggunakan computer vision dengan klasifikasi berdasarkan grade kualitas.

## 📱 Tentang Aplikasi

CatfishCounter adalah aplikasi Android yang menggunakan teknologi computer vision untuk:
- Mendeteksi ikan lele secara real-time melalui kamera
- Mengklasifikasikan ikan lele berdasarkan grade kualitas (A, B, C)
- Melacak dan menghitung objek yang melewati garis penghitung
- Menstabilkan klasifikasi grade menggunakan sistem prioritas

## ✨ Fitur Utama

### 🎯 Deteksi dan Tracking
- **Real-time Detection**: Deteksi ikan lele menggunakan model TensorFlow Lite
- **Object Tracking**: Pelacakan objek menggunakan algoritma MedianFlow dengan Kalman Filter
- **Bounding Box Visual**: Tampilan kotak pembatas dengan warna berbeda untuk setiap grade

### 📊 Sistem Klasifikasi Grade
- **Grade A**: Kualitas terendah (prioritas 1)
- **Grade B**: Kualitas menengah (prioritas 2) 
- **Grade C**: Kualitas tertinggi (prioritas 3)
- **Grade Stabilizer**: Sistem yang mengunci grade tertinggi yang pernah terdeteksi

### 🔢 Sistem Penghitung
- Penghitung otomatis ketika objek melewati garis virtual
- Penghitung terpisah untuk setiap grade
- Total penghitung keseluruhan
- Fungsi reset untuk mengulang penghitungan

### ⚡ Akselerasi Hardware
- **CPU**: Mode default dengan 4 thread
- **GPU**: Akselerasi GPU (jika didukung perangkat)
- **NNAPI**: Neural Network API untuk optimasi hardware

### 📈 Monitoring Performa
- **FPS Counter**: Menampilkan frame rate real-time
- **Latency**: Waktu pemrosesan per frame dalam milidetik
- **Processing Stats**: Statistik performa pemrosesan

## 🛠️ Teknologi yang Digunakan

### Framework & Library
- **Kotlin**: Bahasa pemrograman utama
- **Android SDK**: Target API 34, Minimum API 24
- **CameraX**: Untuk akses kamera dan preview
- **OpenCV 3.4.1**: Library computer vision
- **TensorFlow Lite**: Machine learning inference
- **Coroutines**: Asynchronous programming

### Dependencies Utama
```gradle
// OpenCV untuk Computer Vision
implementation 'com.quickbirdstudios:opencv-contrib:3.4.1'

// TensorFlow Lite untuk Machine Learning
implementation 'org.tensorflow:tensorflow-lite-task-vision:0.4.4'
implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
implementation 'org.tensorflow:tensorflow-lite-gpu:2.16.1'

// Kotlin Coroutines untuk Asynchronous Programming
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0'

// CameraX untuk Camera Management
implementation "androidx.camera:camera-core:1.5.0-alpha03"
implementation "androidx.camera:camera-camera2:1.5.0-alpha03"
implementation "androidx.camera:camera-lifecycle:1.5.0-alpha03"
implementation "androidx.camera:camera-video:1.5.0-alpha03"
implementation "androidx.camera:camera-view:1.5.0-alpha03"

// Android Support Libraries
implementation 'androidx.core:core-ktx:1.15.0'
implementation 'androidx.appcompat:appcompat:1.7.0'
implementation 'com.google.android.material:material:1.12.0'
implementation 'androidx.activity:activity:1.10.0'
implementation 'androidx.constraintlayout:constraintlayout:2.2.0'
```

### Detailed Version Information
| Component | Version | Purpose |
|-----------|---------|---------|
| **Compile SDK** | 35 | Target Android API untuk kompilasi |
| **Target SDK** | 34 | Target Android version untuk deployment |
| **Min SDK** | 24 | Minimum Android version yang didukung |
| **Kotlin** | 1.9.24 | Bahasa pemrograman utama |
| **AGP** | 8.7.0 | Android Gradle Plugin |
| **Gradle** | 8.9 | Build system |
| **JVM Target** | 1.8 | Java bytecode compatibility |

### Required Model Files
- **detect_metadata.tflite** (19.8MB): Model TensorFlow Lite untuk deteksi ikan lele
- **Location**: `app/src/main/assets/detect_metadata.tflite`
- **Format**: TensorFlow Lite quantized model
- **Input**: 320x320 RGB image
- **Output**: Bounding boxes + confidence scores + class labels

## 📋 Persyaratan Sistem

### Persyaratan Perangkat (Runtime)
- **Android 7.0** (API level 24) atau lebih tinggi
- **Kamera**: Akses kamera diperlukan (rear camera)
- **RAM**: Minimum 3GB (disarankan 4GB+ untuk performa optimal)
- **Storage**: ~100MB untuk aplikasi dan model
- **CPU**: ARMv7 atau ARM64 architecture
- **GPU**: Optional - untuk hardware acceleration (Mali, Adreno, PowerVR)

### Persyaratan Development Environment
- **Operating System**: 
  - Windows 10/11 (64-bit)
  - macOS 10.14+ (Mojave atau lebih baru)
  - Linux (Ubuntu 18.04+, atau distribusi setara)
- **RAM**: Minimum 8GB (disarankan 16GB+)
- **Storage**: Minimum 4GB free space untuk Android Studio dan dependencies
- **Internet**: Koneksi internet untuk download dependencies

### Required Permissions
- `android.permission.CAMERA` - Akses kamera device
- `android.permission.READ_EXTERNAL_STORAGE` - Membaca file dari storage
- `android.permission.WRITE_EXTERNAL_STORAGE` - Menulis file ke storage (optional)

## 🚀 Instalasi dan Build

### 1. Prasyarat Development Environment

#### Android Studio Installation
1. **Download Android Studio**
   - Download dari [developer.android.com](https://developer.android.com/studio)
   - Pilih versi stabil terbaru (Arctic Fox atau lebih baru)
   - Size: ~1GB (installer) + ~3GB (full installation)

2. **Install Android Studio**
   ```bash
   # Windows: Jalankan installer (.exe)
   # macOS: Drag ke Applications folder
   # Linux: Extract dan jalankan studio.sh
   ```

3. **Setup Android SDK**
   - Buka Android Studio
   - Go to: File → Settings → Appearance & Behavior → System Settings → Android SDK
   - Install:
     - Android API 24 (Android 7.0) - Minimum
     - Android API 34 (Android 14) - Target
     - Android API 35 - Compile SDK
     - Build Tools 34.0.0+

#### JDK Requirements
- **Java Development Kit 8** atau lebih tinggi
- **Recommended**: OpenJDK 11 atau Oracle JDK 11
- **Check Installation**: 
  ```bash
  java -version
  javac -version
  ```

#### Git Installation
```bash
# Windows (menggunakan chocolatey)
choco install git

# macOS (menggunakan homebrew)
brew install git

# Ubuntu/Debian
sudo apt update && sudo apt install git

# Verify installation
git --version
```

### 2. Clone dan Setup Project

#### Step 1: Clone Repository
```bash
# Clone project
git clone https://github.com/AgungA1/CatfishCounter.git

# Masuk ke directory project
cd CatfishCounter

# Check struktur project
ls -la
```

#### Step 2: Verifikasi File Project
Pastikan file-file berikut ada:
```
CatfishCounter/
├── app/
│   ├── build.gradle
│   └── src/main/assets/detect_metadata.tflite  # Model TensorFlow
├── build.gradle
├── gradle/
│   └── wrapper/gradle-wrapper.properties
├── gradlew        # Unix script
├── gradlew.bat    # Windows script
└── settings.gradle
```

#### Step 3: Setup Gradle Wrapper
```bash
# Berikan permission untuk gradlew (Linux/macOS)
chmod +x gradlew

# Test gradle wrapper
./gradlew --version  # Linux/macOS
gradlew.bat --version  # Windows
```

### 3. Import dan Sync Project

#### Step 1: Buka di Android Studio
1. Buka Android Studio
2. Pilih **"Open an Existing Project"**
3. Navigate ke folder `CatfishCounter`
4. Klik **"OK"**

#### Step 2: Gradle Sync
1. Tunggu Android Studio load project
2. Jika diminta, klik **"Sync Now"** untuk sync Gradle
3. Download dependencies akan dimulai otomatis
4. **Estimated time**: 5-15 menit (tergantung koneksi internet)

#### Step 3: Verifikasi Dependencies
Check di **"Build"** tab untuk memastikan tidak ada error:
```
> Configure project :app
> Task :app:preBuild
> BUILD SUCCESSFUL
```

### 4. Build Project

#### Debug Build
```bash
# Build debug APK
./gradlew assembleDebug

# Lokasi output: app/build/outputs/apk/debug/app-debug.apk
```

#### Release Build
```bash
# Build release APK
./gradlew assembleRelease

# Lokasi output: app/build/outputs/apk/release/app-release-unsigned.apk
```

#### Build All Variants
```bash
# Build semua variants
./gradlew build
```

### 5. Install ke Device

#### Via Android Studio
1. Connect device via USB
2. Enable **"Developer Options"** dan **"USB Debugging"**
3. Klik tombol **"Run"** (▶️) di Android Studio
4. Pilih target device

#### Via Command Line
```bash
# Install debug APK
./gradlew installDebug

# Atau manual install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 6. Verifikasi Installation

#### Check APK Info
```bash
# Check APK details
aapt dump badging app/build/outputs/apk/debug/app-debug.apk

# Check APK size
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

#### Test Run
1. Buka aplikasi di device
2. Grant camera permission
3. Verify camera preview muncul
4. Test detection dengan tap **"Start"**

### 7. Troubleshooting Installation

#### Problem: Gradle Sync Failed
**Error**: `Unable to resolve dependency`
**Solution**:
```bash
# Clear Gradle cache
./gradlew clean

# Delete .gradle folder dan rebuild
rm -rf .gradle
./gradlew build --refresh-dependencies
```

#### Problem: SDK Not Found
**Error**: `Android SDK not found`
**Solution**:
1. Open Android Studio → File → Project Structure
2. Set correct SDK location
3. Download missing SDK components

#### Problem: Build Failed - Out of Memory
**Error**: `OutOfMemoryError`
**Solution**:
Add to `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
android.enableJetifier=true
android.useAndroidX=true
```

#### Problem: Device Not Detected
**Error**: Device tidak muncul di Android Studio
**Solution**:
```bash
# Check ADB devices
adb devices

# Restart ADB server
adb kill-server
adb start-server

# Check USB debugging enabled
adb shell getprop ro.debuggable
```

#### Problem: Permission Denied (Linux/macOS)
**Error**: `Permission denied: ./gradlew`
**Solution**:
```bash
# Grant execute permission
chmod +x gradlew

# Or run with sh
sh gradlew assembleDebug
```

#### Problem: OpenCV Library Not Found
**Error**: `OpenCV library not found`
**Solution**:
1. Verify OpenCV dependency di `app/build.gradle`
2. Check internet connection untuk download
3. Clean dan rebuild project:
```bash
./gradlew clean
./gradlew assembleDebug
```

### 8. Environment Variables (Optional)

#### Set ANDROID_HOME
```bash
# Linux/macOS (.bashrc atau .zshrc)
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Windows (System Environment Variables)
ANDROID_HOME=C:\Users\YourName\AppData\Local\Android\Sdk
```

#### Set JAVA_HOME
```bash
# Linux/macOS
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk

# Windows
JAVA_HOME=C:\Program Files\Java\jdk-11.0.x
```

### 9. Alternative Installation Methods

#### Using Android Studio Terminal
```bash
# Open Terminal di Android Studio (View → Tool Windows → Terminal)
./gradlew assembleDebug
./gradlew installDebug
```

#### Using Bundled Gradle (tanpa gradlew)
```bash
# Jika gradle terinstall global
gradle assembleDebug
gradle installDebug
```

### 10. Development Tips

#### Speed Up Build
- Enable **"Offline work"** di Gradle settings
- Increase **heap size** di `gradle.properties`
- Use **"Build only current module"**

#### Debugging
- Enable **"USB Debugging"** 
- Use **"Wireless Debugging"** (Android 11+)
- Monitor logs dengan: `adb logcat | grep CatfishCounter`

#### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## 📱 Cara Penggunaan

### 1. Memulai Aplikasi
- Buka aplikasi CatfishCounter
- Berikan izin akses kamera ketika diminta
- Aplikasi akan menampilkan preview kamera

### 2. Memulai Deteksi
- Tekan tombol **"Start"** untuk memulai deteksi
- Status akan berubah menjadi "Detection Active" 
- Objek yang terdeteksi akan ditampilkan dengan bounding box berwarna

### 3. Monitoring
- **Counting Display**: Lihat jumlah objek per grade di bagian bawah
- **Performance Stats**: Monitor FPS dan latency di pojok kanan atas
- **Visual Feedback**: Bounding box dengan warna berbeda untuk setiap grade

### 4. Mengubah Akselerasi
- Tekan tombol **"CPU"** untuk memilih mode akselerasi
- Pilih antara CPU, GPU, atau NNAPI
- GPU/NNAPI hanya tersedia jika didukung perangkat

### 5. Reset Penghitung
- Tekan tombol **"Reset"** untuk mengulang penghitungan
- Semua counter akan dikembalikan ke 0

### 6. Menghentikan Deteksi
- Tekan tombol **"Stop"** untuk menghentikan deteksi
- Status akan berubah menjadi "Detection Inactive"

## 🏗️ Arsitektur Aplikasi

### Komponen Utama

#### 1. MainActivity
- Activity utama yang mengelola UI dan lifecycle
- Mengatur kamera, preview, dan event handling
- Koordinasi antara komponen detection dan tracking

#### 2. ObjectDetection
- Menangani inference model TensorFlow Lite
- Preprocessing frame kamera
- Post-processing hasil deteksi dengan NMS (Non-Maximum Suppression)
- Mendukung multiple acceleration type

#### 3. ObjectTracker
- Implementasi tracking menggunakan MedianFlow tracker
- Kalman Filter untuk prediksi posisi
- Hungarian Algorithm untuk assignment detection-tracker
- Dead zone management untuk menghindari false positive

#### 4. GradeStabilizer
- Sistem stabilisasi grade berdasarkan histori deteksi
- Prioritas grade: C > B > A
- Lock mechanism untuk grade tertinggi
- History window management

#### 5. GraphicOverlay
- Custom View untuk overlay graphics
- Rendering bounding boxes dengan warna berbeda
- Coordinate transformation untuk berbagai orientasi
- Performance indicator display

### Data Flow
```
Camera Frame → ObjectDetection → ObjectTracker → GradeStabilizer → UI Update
```

## 🎨 Kustomisasi Warna Grade

Aplikasi menggunakan skema warna yang berbeda untuk setiap grade:
- **Grade A**: Hijau (`#4CAF50`)
- **Grade B**: Biru (`#2196F3`) 
- **Grade C**: Merah (`#F44336`)
- **Unknown**: Abu-abu (`#9E9E9E`)

## ⚙️ Konfigurasi

### Detection Parameters
```kotlin
private val DETECTION_THRESHOLD = 0.7f  // Confidence threshold
private val MAX_DETECTIONS = 35         // Maximum detections per frame
var nmsIoUThreshold = 0.4               // NMS IoU threshold
```

### Tracking Parameters
```kotlin
private val MAX_TRACKING_DISTANCE = 150.0  // Maximum tracking distance
private val MAX_MISSING_FRAMES = 15        // Frames before removing tracker
```

### Stabilizer Parameters
```kotlin
private val MAX_HISTORY_SIZE = 9              // History window size
private val MIN_DETECTIONS_TO_LOCK = 3        // Minimum detections to lock grade
```

## 🔧 Troubleshooting

### Installation Issues

#### 1. Gradle Sync Failed
**Symptoms**: `Unable to resolve dependency` atau `Failed to resolve`
**Causes**: Network issues, corrupted cache, wrong repository
**Solutions**:
```bash
# Clear Gradle cache
./gradlew clean
rm -rf ~/.gradle/caches/

# Force refresh dependencies
./gradlew build --refresh-dependencies

# Check internet connection
ping google.com

# Retry sync dengan offline mode disabled
# Android Studio: File → Settings → Build → Gradle → Offline work (uncheck)
```

#### 2. Android SDK Issues
**Symptoms**: `Android SDK not found` atau `SDK location not found`
**Solutions**:
```bash
# Set ANDROID_HOME environment variable
export ANDROID_HOME=$HOME/Android/Sdk  # Linux/macOS
# Windows: Set via System Properties

# Verify SDK path di Android Studio
# File → Project Structure → SDK Location

# Install missing SDK components
# Tools → SDK Manager → Install required APIs
```

#### 3. Build Memory Issues
**Symptoms**: `OutOfMemoryError` atau build hang
**Solutions**:
Add to `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=true
org.gradle.caching=true
android.enableJetifier=true
android.useAndroidX=true
```

#### 4. OpenCV Library Issues
**Symptoms**: `OpenCV not found` atau native library errors
**Solutions**:
```bash
# Verify OpenCV dependency di build.gradle
grep -n "opencv" app/build.gradle

# Clean dan rebuild
./gradlew clean assembleDebug

# Check if using correct ABI
# Pastikan targetSdkVersion compatible
```

### Runtime Issues

#### 1. Application Crash pada Startup
**Symptoms**: App crash immediately atau black screen
**Diagnostic Steps**:
```bash
# Check logcat untuk error details
adb logcat | grep -E "(FATAL|ERROR|CrashHandler)"

# Check permissions
adb shell dumpsys package com.catfish.tfvision_opencv | grep permission

# Verify TFLite model exists
adb shell ls -la /data/data/com.catfish.tfvision_opencv/assets/
```
**Common Fixes**:
- Grant camera permission: Settings → Apps → CatfishCounter → Permissions
- Ensure TFLite model file di assets folder
- Check device compatibility (API 24+)

#### 2. Camera Permission Denied
**Symptoms**: Camera tidak muncul, permission dialog tidak muncul
**Solutions**:
```bash
# Grant permission manually
adb shell pm grant com.catfish.tfvision_opencv android.permission.CAMERA

# Reset app permissions
adb shell pm reset-permissions com.catfish.tfvision_opencv

# Check permission status
adb shell dumpsys package com.catfish.tfvision_opencv | grep CAMERA
```

#### 3. Poor Detection Performance
**Symptoms**: Low FPS, high latency, detection lag
**Performance Optimization**:
```kotlin
// Adjust di MainActivity.kt
private val DETECTION_THRESHOLD = 0.8f  // Increase threshold
private val MAX_DETECTIONS = 20         // Reduce max detections
var nmsIoUThreshold = 0.5               // Increase NMS threshold
```
**Hardware Solutions**:
- Switch to CPU mode jika GPU tidak stabil
- Close background apps
- Restart device untuk free memory
- Check device temperature (thermal throttling)

#### 4. Tracking Instability
**Symptoms**: Objects jumping, lost tracking, incorrect counting
**Solutions**:
```kotlin
// Adjust tracking parameters di ObjectTracker.kt
private val MAX_TRACKING_DISTANCE = 100.0  // Reduce untuk akurasi
private val MAX_MISSING_FRAMES = 10        // Reduce untuk responsivitas
```
**Environment Tips**:
- Ensure good lighting conditions
- Avoid camera shake
- Keep objects dalam frame boundaries
- Use consistent object movement speed

### Device Compatibility Issues

#### 1. GPU Acceleration Not Working
**Symptoms**: App crash pada GPU mode atau poor GPU performance
**Diagnostic**:
```bash
# Check GPU capabilities
adb shell dumpsys | grep -i gpu
adb shell getprop | grep -i gpu
```
**Solutions**:
- Fallback ke CPU mode
- Update device drivers jika memungkinkan
- Test pada device lain untuk comparison

#### 2. NNAPI Issues
**Symptoms**: Slow performance atau crash pada NNAPI mode
**Solutions**:
- Test each acceleration mode secara terpisah
- Check device NNAPI support:
```bash
adb shell getprop | grep -i nnapi
```
- Gunakan CPU mode as stable fallback

#### 3. Storage Issues
**Symptoms**: Installation failed, low storage warnings
**Solutions**:
```bash
# Check available storage
adb shell df /data

# Clear app data
adb shell pm clear com.catfish.tfvision_opencv

# Free up system storage
adb shell pm trim-caches 1000M
```

### Development Issues

#### 1. Build Variants Problems
**Error**: Build type tidak tersedia atau configuration errors
**Solutions**:
```bash
# List available build variants
./gradlew tasks --all | grep assemble

# Build specific variant
./gradlew assembleDebug
./gradlew assembleRelease

# Clean specific variant
./gradlew cleanDebug
```

#### 2. Signing Issues (Release Build)
**Error**: Failed to sign APK atau keystore problems
**Solutions**:
```gradle
// Di app/build.gradle, add signing config
android {
    signingConfigs {
        debug {
            // Debug signing handled automatically
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.debug  // Use debug signing for testing
            minifyEnabled false
        }
    }
}
```

#### 3. ProGuard/R8 Issues
**Error**: Obfuscation errors atau missing classes
**Solutions**:
```proguard
# Di proguard-rules.pro, add keep rules
-keep class org.opencv.** { *; }
-keep class org.tensorflow.** { *; }
-keepclassmembers class * {
    @org.tensorflow.lite.annotations.* <methods>;
}
```

### Performance Monitoring

#### Real-time Debugging
```bash
# Monitor app performance
adb shell top | grep catfish

# Monitor memory usage
adb shell dumpsys meminfo com.catfish.tfvision_opencv

# Monitor CPU usage
adb shell dumpsys cpuinfo | grep catfish

# Monitor GPU usage (jika didukung)
adb shell cat /sys/class/kgsl/kgsl-3d0/gpuload
```

#### Frame Rate Analysis
```bash
# Monitor frame drops
adb shell dumpsys gfxinfo com.catfish.tfvision_opencv

# Real-time frame rate
adb shell dumpsys SurfaceFlinger --latency-clear
```

### Getting Help

#### Collect Debug Information
```bash
# Generate comprehensive debug info
adb shell dumpsys package com.catfish.tfvision_opencv > package_info.txt
adb logcat -d > logcat_output.txt
adb shell getprop > device_props.txt

# Include:
# - Device model dan Android version
# - RAM dan storage info  
# - Error messages dari logcat
# - Steps to reproduce issue
```

#### Log Monitoring Commands
```bash
# Filter app-specific logs
adb logcat | grep "CatfishCounter"

# Filter by log level
adb logcat "*:E"  # Errors only
adb logcat "*:W"  # Warnings dan above

# Save logs to file
adb logcat > debug_log.txt
```

## 📊 Model Information

### TensorFlow Lite Model Details
Aplikasi menggunakan model TensorFlow Lite custom (`detect_metadata.tflite`) yang telah dilatih untuk mendeteksi ikan lele dengan klasifikasi grade.

#### Model Specifications
- **File Name**: `detect_metadata.tflite`
- **Size**: ~19.8MB
- **Format**: TensorFlow Lite quantized model (INT8)
- **Input Shape**: [1, 320, 320, 3] (Batch, Height, Width, Channels)
- **Input Type**: UINT8 normalized (0-255)
- **Output Classes**: 3 classes (grade_a, grade_b, grade_c)
- **Architecture**: Modified MobileNet-SSD
- **Quantization**: Post-training quantization untuk optimasi mobile

#### Model Performance
- **Inference Time**: 15-50ms (tergantung device dan acceleration mode)
- **mAP@0.5**: ~85% (pada test dataset)
- **Model Size**: Optimized untuk mobile deployment
- **Memory Footprint**: ~50MB saat inference

#### Training Details
- **Dataset**: Custom catfish dataset dengan grade annotation
- **Training Framework**: TensorFlow 2.x
- **Augmentation**: Rotation, scaling, brightness adjustment
- **Validation Split**: 80/20 train/validation
- **Export Format**: TensorFlow Lite dengan metadata

#### Supported Hardware Acceleration
- **CPU**: NEON optimization (ARM devices)
- **GPU**: OpenGL ES compute shaders (Mali, Adreno, PowerVR)
- **NNAPI**: Neural Network API (Android 8.1+)

#### Model Limitations
- **Optimal Lighting**: Requires adequate lighting untuk accurate detection
- **Object Size**: Minimum object size ~50x50 pixels
- **Overlap Handling**: May struggle dengan heavily overlapping objects
- **Background**: Performs best dengan contrasting backgrounds

## 🔍 Testing dan Validasi

### Pre-Installation Testing

#### Check System Compatibility
```bash
# Check Android version
adb shell getprop ro.build.version.release

# Check API level
adb shell getprop ro.build.version.sdk

# Check architecture
adb shell getprop ro.product.cpu.abi

# Check available RAM
adb shell cat /proc/meminfo | grep MemTotal

# Check available storage
adb shell df -h /data
```

#### Camera Hardware Testing
```bash
# List available cameras
adb shell dumpsys media.camera | grep "Camera"

# Check camera permissions support
adb shell pm list permissions | grep CAMERA

# Test camera access
adb shell am start -a android.media.action.IMAGE_CAPTURE
```

### Post-Installation Testing

#### Basic Functionality Test
1. **App Launch Test**
   - Install APK
   - Launch app dari home screen
   - Verify no crash dalam 30 detik
   - Check UI elements loaded properly

2. **Permission Test**
   - Verify camera permission request dialog
   - Grant permission
   - Check camera preview appears
   - Verify detection UI elements

3. **Detection Test**
   - Tap "Start" button
   - Point camera ke objek test
   - Verify bounding boxes appear
   - Check FPS counter working
   - Test grade classification

4. **Performance Test**
   ```bash
   # Monitor performance selama 5 menit
   adb shell top -d 1 -n 300 | grep catfish > performance_log.txt
   
   # Check memory leaks
   adb shell dumpsys meminfo com.catfish.tfvision_opencv
   ```

#### Hardware Acceleration Testing
```bash
# Test setiap mode acceleration
# 1. CPU Mode (default)
# 2. GPU Mode (jika tersedia)  
# 3. NNAPI Mode (jika tersedia)

# Monitor performance untuk setiap mode
adb shell dumpsys gfxinfo com.catfish.tfvision_opencv
```

### Benchmark Testing

#### Performance Metrics
- **Target FPS**: 15-30 FPS (tergantung device)
- **Max Latency**: <100ms per frame
- **Memory Usage**: <200MB steady state
- **Battery Impact**: <10% per hour pada moderate usage

#### Test Scenarios
1. **Stress Test**: Continuous operation selama 1 jam
2. **Memory Test**: Monitor memory usage dan leaks
3. **Thermal Test**: Performance under sustained load
4. **Battery Test**: Power consumption measurement

### Validation Checklist

#### ✅ Installation Validation
- [ ] APK berhasil diinstall tanpa error
- [ ] App icon muncul di launcher
- [ ] First launch tidak crash
- [ ] Permission request bekerja
- [ ] Camera preview ditampilkan

#### ✅ Core Functionality
- [ ] Object detection working
- [ ] Bounding boxes displayed correctly
- [ ] Grade classification accurate
- [ ] Counting system working
- [ ] Reset function working

#### ✅ UI/UX Validation  
- [ ] All buttons responsive
- [ ] Status messages displayed
- [ ] Performance stats visible
- [ ] Color coding correct untuk grades
- [ ] No UI freezing atau lag

#### ✅ Performance Validation
- [ ] FPS > 10 pada device minimum spec
- [ ] Memory usage stable (no leaks)
- [ ] No thermal throttling under normal use
- [ ] Battery drain acceptable

#### ✅ Error Handling
- [ ] Graceful handling camera permission denied
- [ ] Recovery dari low memory situations
- [ ] Proper error messages displayed
- [ ] App tidak crash pada network loss

### Common Test Issues dan Solutions

#### Issue: Low Performance pada Device Lama
**Test Command**:
```bash
adb shell getprop ro.product.model
adb shell cat /proc/cpuinfo | grep "model name"
```
**Solutions**:
- Reduce DETECTION_THRESHOLD ke 0.6
- Decrease MAX_DETECTIONS ke 15
- Force CPU mode
- Reduce camera resolution jika memungkinkan

#### Issue: Memory Leaks
**Test Command**:
```bash
# Monitor memory setiap 10 detik
for i in {1..60}; do
  adb shell dumpsys meminfo com.catfish.tfvision_opencv | grep "TOTAL"
  sleep 10
done
```
**Expected**: Memory usage should stabilize after initial ramp-up

#### Issue: Camera Compatibility
**Test Different Resolutions**:
```bash
adb shell dumpsys media.camera | grep -A 20 "Camera 0"
```
**Verify**: App handles different camera resolutions gracefully

## 🤝 Kontribusi

Untuk berkontribusi pada project ini:
1. Fork repository
2. Buat feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add some AmazingFeature'`)
4. Push ke branch (`git push origin feature/AmazingFeature`)
5. Buat Pull Request

## 📄 Lisensi

Project ini menggunakan lisensi MIT. Lihat file `LICENSE` untuk detail lengkap.

## 📞 Kontak

Untuk pertanyaan atau support, silakan hubungi:
- **GitHub**: [@AgungA1](https://github.com/AgungA1)
- **Repository**: [CatfishCounter](https://github.com/AgungA1/CatfishCounter)

## 🔄 Changelog

### Version 1.0
- Initial release
- Real-time catfish detection dan classification
- Object tracking dengan MedianFlow
- Grade stabilization system
- Hardware acceleration support
- Performance monitoring

---

**Note**: Aplikasi ini dikembangkan untuk tujuan penelitian dan dapat disesuaikan untuk kebutuhan spesifik industri perikanan.