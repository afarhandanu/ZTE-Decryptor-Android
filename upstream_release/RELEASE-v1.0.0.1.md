# Upstream reference — ZTE Config Tool v1.0.0.1

Project basis:

- Repository: `MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6`
- Release tag: `v1.0.0.1`
- Release date shown by GitHub: 2026-07-15
- Upstream license: MIT

Release assets listed by GitHub:

| Asset | SHA-256 |
| --- | --- |
| `Proyek-ZTE-F6600P-v1.0.0.1.rar` | `85943cad478959056b3e7887d9b295b5292ef0acf51a6e4293c0c0ed9ea8da11` |
| `Proyek-ZTE-F670L-v1.0.0.1.rar` | `fb71486e34a4fea8c6c8b7ee5183b249232c6e49b8dfeddae884d6f29d079e2f` |
| `Proyek-ZTE-F672Y-v1.0.0.1.rar` | `399fc1fe5a2b062fbce556972d5ab22b1963b2585e2d6de4d1eb941ccd916484` |
| `Proyek-ZTE-F679D-v1.0.0.1.rar` | `e7247a6421856bd9f36612b12094f6e3d34871fe69eb2717765c3244c255baf3` |

Upstream release behavior mirrored by the Android application:

- Type-6 `config.bin` decrypt to XML.
- Re-encrypt edited XML into a new Type-6 BIN using the original BIN as template/header source.
- Serial Number + MAC Address based workflow.
- PPPoE/`PPPIF` extraction.
- Active `DevAuthInfo` extraction.
- Tested-device references: F6600P, F670L, F672Y, F679D.

Android-specific additions in this project:

- Serial Number and MAC input fields replace `_sn.txt` and `_mac.txt`.
- In-app XML viewer/editor.
- Case-insensitive search with quick targets for `PPPIF`, `WANCPPP`, `DevAuthInfo`, username and password fields.
- XML well-formedness validation before editor-based encrypt.
- Export of `pppif_extracted.txt` and `devauthinfo_extracted.txt`.
- Direct editor → `config_new.bin` flow without leaving the app.
- No Python runtime or Internet permission is required by the APK.

## Direct release asset URLs

- F6600P: `https://github.com/MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6/releases/download/v1.0.0.1/Proyek-ZTE-F6600P-v1.0.0.1.rar`
- F670L: `https://github.com/MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6/releases/download/v1.0.0.1/Proyek-ZTE-F670L-v1.0.0.1.rar`
- F672Y: `https://github.com/MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6/releases/download/v1.0.0.1/Proyek-ZTE-F672Y-v1.0.0.1.rar`
- F679D: `https://github.com/MichaelJorky/ZTE-Config.bin-Decryptor-Encryptor-Type-6/releases/download/v1.0.0.1/Proyek-ZTE-F679D-v1.0.0.1.rar`
