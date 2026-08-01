# Persistent development signing

The CI and local debug/release builds use the stable development keystore encoded in `file-editor-dev.jks.b64`.

This prevents every GitHub Actions runner from generating a different debug certificate, so APKs produced from version 1.3.0 onward can update one another without uninstalling the app.

## Security boundary

This repository is public, so this key is intentionally a **development and sideloading key**, not a private production or Play Store release key. Anyone with the repository can reproduce it and sign compatible APKs.

Before publishing through an app store, replace this configuration with a private release keystore supplied through encrypted CI secrets or Play App Signing. Never commit a private production signing key.

Persistent development certificate SHA-256:

`C1:18:17:F2:02:CD:E5:A4:2C:0C:03:B7:67:82:A9:F0:95:96:28:DF:82:B3:5F:16:05:E0:71:D9:4C:DE:C8:E8`
