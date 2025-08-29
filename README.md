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
// OpenCV
implementation 'com.quickbirdstudios:opencv-contrib:3.4.1'

// TensorFlow Lite
implementation 'org.tensorflow:tensorflow-lite-task-vision:0.4.4'
implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
implementation 'org.tensorflow:tensorflow-lite-gpu:2.16.1'

// CameraX
implementation "androidx.camera:camera-core:1.5.0-alpha03"
implementation "androidx.camera:camera-camera2:1.5.0-alpha03"
implementation "androidx.camera:camera-lifecycle:1.5.0-alpha03"
```

## 📋 Persyaratan Sistem

### Perangkat
- **Android 7.0** (API level 24) atau lebih tinggi
- **Kamera**: Akses kamera diperlukan
- **RAM**: Minimum 3GB (disarankan 4GB+)
- **Storage**: ~100MB untuk aplikasi dan model

### Permissions
- `android.permission.CAMERA` - Akses kamera
- `android.permission.READ_EXTERNAL_STORAGE` - Membaca storage
- `android.permission.WRITE_EXTERNAL_STORAGE` - Menulis storage

## 🚀 Instalasi dan Build

### Prasyarat
- **Android Studio**: Arctic Fox atau lebih baru
- **JDK**: Java 8 atau lebih tinggi
- **Android SDK**: API level 24-34
- **Gradle**: 8.9+

### Langkah Instalasi

1. **Clone Repository**
   ```bash
   git clone https://github.com/AgungA1/CatfishCounter.git
   cd CatfishCounter
   ```

2. **Buka di Android Studio**
   - Buka Android Studio
   - Pilih "Open an existing project"
   - Navigate ke folder CatfishCounter

3. **Sync Project**
   - Tunggu Gradle sync selesai
   - Download dependencies yang diperlukan

4. **Build Project**
   ```bash
   ./gradlew assembleDebug
   ```

5. **Install ke Device**
   ```bash
   ./gradlew installDebug
   ```

### Build APK Release
```bash
./gradlew assembleRelease
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

### Issue Umum

#### 1. Aplikasi Crash saat Start
- **Solusi**: Pastikan izin kamera telah diberikan
- **Check**: Verifikasi model TensorFlow Lite tersedia di assets

#### 2. Performa Lambat
- **Solusi**: Ubah ke mode CPU jika GPU tidak stabil
- **Optimize**: Reduce max detections atau increase threshold

#### 3. Deteksi Tidak Akurat
- **Solusi**: Pastikan pencahayaan cukup
- **Check**: Verifikasi objek dalam frame dengan jelas

#### 4. Tracking Loss
- **Solusi**: Reset tracker dengan tombol Reset
- **Adjust**: Sesuaikan MAX_TRACKING_DISTANCE jika perlu

### Performance Tips
- Gunakan GPU acceleration untuk performa optimal (jika didukung)
- Monitor FPS dan latency untuk optimasi
- Pastikan device tidak dalam mode battery saver
- Close aplikasi lain untuk free up memory

## 📊 Model Information

Aplikasi menggunakan model TensorFlow Lite custom (`detect_metadata.tflite`) yang telah dilatih untuk mendeteksi ikan lele dengan klasifikasi grade. Model ini mendukung:
- Input resolution yang optimal untuk mobile device
- Multiple class detection (grade_a, grade_b, grade_c)
- Optimized inference untuk real-time processing

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