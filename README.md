# StroboScope Pro (Android Stroboscope & Optical Tachometer)

High-precision Android LED stroboscope and rotational speed tachometer featuring a **4-stage vertical slider console (`1–200 Hz` + sub-hertz `.01 Hz` precision)**, **`0.5×` / `1.0×` / `2.0×` harmonic multipliers**, and **Non-Root**, **KernelSU / Root Sysfs**, and **LSPosed Module** hardware drivers (with dedicated support for **OnePlus 5T `dumpling` / Qualcomm PMI8998**).

---

## One-Time Setup for Permanent Automated APK Builds on GitHub

To prevent you from ever having to rename files again when syncing with AI Studio:

1. The actual build script lives in **`ci-build-apk.sh`** (which AI Studio can freely update and push on every sync without triggering GitHub's `.github/workflows` permission block).
2. On GitHub.com, copy the contents of **`github-workflow-build-apk.yml`** into **`.github/workflows/build-apk.yml`** (do **not** delete `github-workflow-build-apk.yml`—keep both files).
3. Because `.github/workflows/build-apk.yml` simply calls `./ci-build-apk.sh` and never needs to be modified by AI Studio again, every future **Sync** from AI Studio will push cleanly and automatically trigger a new APK release on your **Releases** page!
