<p align="center">
  <img src="icon/weblab.png" alt="Weblab" width="128" height="128">
</p>

<h1 align="center">Weblab</h1>

<p align="center">
  Editor kode HTML, CSS, dan JavaScript untuk Android, dibuat ringan dan tanpa dependensi pihak ketiga.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/versi-0.32-blue" alt="Versi 0.32">
  <img src="https://img.shields.io/badge/versionCode-9864-blue" alt="versionCode 9864">
  <img src="https://img.shields.io/badge/min%20Android-5.0%20(API%2021)-green" alt="Min Android 5.0">
  <img src="https://img.shields.io/badge/dependensi-0-success" alt="Dependensi 0">
</p>

---

## Label Versi

| Label | Nilai |
|---|---|
| Nama versi (`versionName`) | `0.32` |
| Kode versi (`versionCode`) | `9864` |
| Application ID | `ru.iqwanoino.weblab` |
| Namespace | `ru.iqwanoino.weblab` |

## Ringan dan Tanpa Dependensi

- **Tanpa AndroidX dan tanpa AppCompat.** Aplikasi hanya memakai API Android bawaan (`android.*`) dan library standar Java.
- **Tanpa library eksternal.** Blok `dependencies` hanya berisi `fileTree` untuk folder `libs`, yang saat ini kosong.
- **Editor sendiri.** Komponen editor kode, color picker, slider, dan ikon dibuat di dalam proyek, bukan diambil dari library luar.
- **Ukuran kecil.** Tidak ada R8 atau ProGuard minify pada build release, sehingga build cepat dan mudah di-debug.

## Spesifikasi APK

| Spek | Nilai |
|---|---|
| Versi Android minimum | Android 5.0 Lollipop (API 21) |
| Target SDK | 28 (Android 9) |
| Compile SDK | 33 |
| Bahasa | Java |
| Build tool | Android Gradle Plugin 8.13.1 |
| Gradle | 8.13 |
| JDK untuk build | 17 |
| Orientasi layar | Portrait |
| Minify (R8) | Nonaktif |
| Penandatanganan release | Keystore `weblab.jks` (alias `weblab`) |

### Izin yang Diminta

| Izin | Fungsi |
|---|---|
| `INTERNET` | Akses jaringan |
| `READ_EXTERNAL_STORAGE` | Membaca file proyek |
| `WRITE_EXTERNAL_STORAGE` | Menyimpan file proyek |
| `MANAGE_EXTERNAL_STORAGE` | Akses penyimpanan penuh untuk folder proyek |

## Build

Build otomatis berjalan lewat GitHub Actions (`.github/workflows/build.yml`) setiap push ke `main` atau `master`. Hasilnya:

- Artefak `weblab-debug` dan `weblab-release` di tab **Actions**
- GitHub Release baru dengan APK terlampir

Untuk build lokal:

```bash
gradle assembleDebug
gradle assembleRelease
```

## Lisensi

Belum ditentukan.
