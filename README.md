> **v1.2.3:** GPON SN / ONT SN + perbaikan scroll XML Viewer/Editor.

# ZTE Type 6 Tool — Android v1.2.3

## Stable signature & nama APK

Mulai v1.2.3, build debug dan release menggunakan signing key stabil yang sama dari folder `signing/`. Jangan hapus atau regenerasi file `signing/zte-decryptor-stable.jks` bila ingin APK versi berikutnya dapat dipasang sebagai update.

GitHub Actions menghasilkan file APK bernama langsung:

```text
ZTE-Decryptor-Android-v1.2.3.apk
```

Karena build sebelum v1.2.3 dibuat oleh ephemeral debug key GitHub runner, instalasi lama perlu di-uninstall satu kali sebelum memasang v1.2.3. Setelah itu build berikutnya tetap kompatibel selama signing key ini dipertahankan.

> Catatan: signing key ini disertakan di source untuk kemudahan build pribadi. Jangan gunakan key yang sama untuk distribusi publik/Play Store. Untuk distribusi publik, simpan release key privat di GitHub Secrets.

Android source project untuk decrypt/encrypt `config.bin` ZTE **Payload Type 6** dan mengedit XML langsung di Android.

Basis workflow: release upstream **ZTE Config Tool v1.0.0.1** dari `MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6`.

> Catatan implementasi: upstream menaruh `_decrypt.py` / `_encrypt.py` di asset `.rar` release, bukan di tree source repository. Project Android ini adalah port native Java yang mempertahankan workflow/format Type 6 dan fitur release, bukan menjalankan Python di dalam APK.

## Fitur

### Workflow release v1.0.0.1

- `config.bin` Type 6 → XML.
- XML → `config_new.bin` Type 6.
- GPON SN / ONT SN dan MAC Address sebagai input key material.
- Original `config.bin` dipakai sebagai template header/preamble ketika encrypt.
- Ekstraksi PPPoE / `PPPIF` ke `pppif_extracted.txt`.
- Ekstraksi `DevAuthInfo` aktif ke `devauthinfo_extracted.txt`.
- Referensi perangkat upstream: ZTE F6600P, F670L, F672Y, F679D.

### Tambahan Android v1.2.3

- Input **GPON SN / ONT SN** dan MAC langsung di aplikasi; tidak perlu `_sn.txt` / `_mac.txt`. D-SN diberi peringatan agar tidak salah digunakan.
- Pemilih model router yang wajib dipilih sebelum decrypt: **F6600P, F670L, F672Y, F679D**.
- Model terikat ke template BIN selama sesi; aplikasi menolak encrypt bila model yang dipilih berbeda dari model template hasil decrypt.
- XML Viewer / Editor di dalam APK, termasuk perbaikan nested scrolling agar isi XML dapat digulir langsung di dalam editor.
- Quick Find:
  - `PPPIF`
  - `WANCPPP`
  - `DevAuthInfo`
  - `name="User"`
  - `name="Pass"`
- Search Previous / Next case-insensitive.
- Validasi XML well-formed sebelum encrypt dari editor.
- XML hasil decrypt langsung dibuka di editor.
- BIN yang didecrypt otomatis menjadi template encrypt.
- Edit → Encrypt → simpan `config_new.bin` tanpa keluar aplikasi.
- Tetap tersedia mode memilih XML dan template BIN secara terpisah.
- Round-trip verification setelah encrypt: BIN hasil didecrypt ulang dan dibandingkan byte-for-byte dengan XML sumber.
- Deteksi/preservasi BOM dan charset dasar XML (`UTF-8`, `UTF-16LE`, `UTF-16BE`, atau encoding declaration yang dikenali).
- Storage Access Framework; tidak meminta akses storage legacy.
- Tidak ada Internet permission.
- Native Java, tanpa Python/Chaquopy/Termux/pip.

## Alur paling cepat

1. Buka tab **Decrypt**.
2. Pilih model router: **F6600P / F670L / F672Y / F679D**.
3. Isi **GPON SN / ONT SN** dan **MAC Address**. Jangan gunakan D-SN.
4. Pilih `config.bin` asli dari router.
5. Tekan **Decrypt & buka XML Editor**.
6. Gunakan Quick Find untuk PPPoE, `DevAuthInfo`, username atau password.
7. Edit XML bila diperlukan.
8. Tekan **Validasi XML**.
9. Tekan **Encrypt XML editor → config_new.bin**.
10. Pilih lokasi simpan hasil BIN.

Tidak perlu menyimpan XML lebih dulu. Kalau ingin backup XML, gunakan tombol **Simpan XML**.

## Model router yang didukung

Pilihan model di aplikasi mengikuti empat asset resmi release `v1.0.0.1`:

| Model | Asset upstream |
| --- | --- |
| F6600P | `https://github.com/MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6/releases/download/v1.0.0.1/Proyek-ZTE-F6600P-v1.0.0.1.rar` |
| F670L | `https://github.com/MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6/releases/download/v1.0.0.1/Proyek-ZTE-F670L-v1.0.0.1.rar` |
| F672Y | `https://github.com/MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6/releases/download/v1.0.0.1/Proyek-ZTE-F672Y-v1.0.0.1.rar` |
| F679D | `https://github.com/MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6/releases/download/v1.0.0.1/Proyek-ZTE-F679D-v1.0.0.1.rar` |

Pemilih model berfungsi sebagai pengaman konteks sesi/template. Codec inti tetap memproses format **Payload Type 6**; aplikasi tidak mengunduh asset `.rar` saat runtime dan tetap bekerja offline.

## Ekstraksi seperti release

Dari tab **XML Editor**:

- **Simpan PPPoE .txt** → `pppif_extracted.txt`
- **Simpan DevAuthInfo .txt** → `devauthinfo_extracted.txt`

Ekstraksi PPPoE mencari tabel `PPPIF`; jika tidak ada, aplikasi mencoba `WANCPPP`. Untuk `DevAuthInfo`, baris `Enable=1` dipilih. Pada konfigurasi lama yang tidak memiliki field `Enable` pada tabel tersebut, seluruh row dipertahankan agar data tidak hilang.

## Build melalui GitHub Actions

1. Buat repository GitHub baru.
2. Upload seluruh isi project ini ke branch `main`.
3. Buka **Actions** → **Build Android APK**.
4. Tekan **Run workflow** atau push commit.
5. Unduh artifact:

```text
ZTE-Type6-Tool-v1.2.3-debug
└── app-debug.apk
```

Workflow menggunakan:

- JDK 17
- Android SDK 35
- Build Tools 35.0.0
- Gradle 8.10.2
- Android Gradle Plugin 8.7.3

## Build lokal

```bash
gradle :app:assembleDebug --stacktrace
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Implementasi Type 6

Codec native Java mengimplementasikan jalur Type 6 berikut:

- Key material/KP dari GPON SN / ONT SN + MAC.
- AES key = SHA-256(KP).
- AES-CBC NoPadding dengan zero padding payload.
- IV Type 6 berasal dari SHA-256 material IV ZTE dan memakai 16 byte pertama.
- Inner payload menggunakan ZLIB chunking dan header ZTE.
- Encrypt mempertahankan preamble/header dari `config.bin` asli.
- Hasil encrypt diverifikasi dengan decrypt ulang sebelum ditawarkan untuk disimpan.

## Struktur source penting

```text
app/src/main/java/com/sione/ztetype6/
├── MainActivity.java              # UI Decrypt / Editor / Encrypt
├── XmlConfigTools.java            # search, validasi, PPPIF/DevAuth extraction
└── crypto/
    └── ZteType6Codec.java         # Type-6 codec native Java
```

Metadata release upstream ada di:

```text
upstream_release/RELEASE-v1.0.0.1.md
```

## Keamanan penggunaan

Gunakan hanya pada router milik sendiri atau perangkat yang Anda memiliki izin untuk mengaudit. Selalu backup `config.bin` asli. Restore konfigurasi yang salah dapat membuat router kehilangan konfigurasi atau tidak dapat diakses.

## Lisensi

Project aplikasi ini menggunakan MIT License. Lihat `NOTICE.md` untuk atribusi proyek upstream/reference.


## v1.2.3 build fix

- Memperbaiki kompatibilitas kompilasi Android pada `XmlConfigTools`.
- `XMLConstants.ACCESS_EXTERNAL_DTD` / `ACCESS_EXTERNAL_SCHEMA` diganti dengan URI properti JAXP literal karena konstanta tersebut tidak tersedia pada Android SDK stub.
- Tidak ada perubahan pada algoritma Type-6, derivasi key, decrypt, encrypt, atau pilihan model router.

