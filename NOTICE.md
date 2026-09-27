# Third-party notice / attribution

Android project ini dibuat untuk membawa workflow ZTE Type-6 ke aplikasi Android native.

## Upstream utama

### MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6

- Repository: https://github.com/MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6
- Release yang dijadikan basis workflow: `v1.0.0.1`
- License: MIT
- Release mendokumentasikan decrypt/encrypt Type 6, input SN + MAC, ekstraksi PPPIF dan DevAuthInfo, serta kompatibilitas F6600P/F670L/F672Y/F679D.

Release tersebut mendistribusikan skrip operasional di asset `.rar`; source tree repository publik utamanya berisi dokumentasi. Karena itu Android project ini tidak membundel Python runtime maupun mengklaim sebagai salinan baris-per-baris skrip `.py` release.

## Referensi format tambahan

- `MichaelJorky/Indihome-Decoder-Encoder-Utility` — MIT License.
- `mkst/zte-config-utility` — implementasi open-source format konfigurasi ZTE dan contoh XML.
- Implementasi Type-6 open-source lain digunakan untuk cross-check struktur key derivation, AES-CBC, compression, dan template-header behavior.

Android-specific UI/editor/export code pada project ini ditulis sebagai implementasi native Java.
