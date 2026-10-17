# StroboScope Pro (Android Stroboscope & Optical Tachometer)

High-precision Android LED stroboscope and rotational speed tachometer featuring a **4-stage vertical slider console (`1–200 Hz` + sub-hertz `.01 Hz` precision)**, **`0.5×` / `1.0×` / `2.0×` harmonic multipliers**, and **Non-Root**, **KernelSU / Root Sysfs**, and **LSPosed Module** hardware drivers (with dedicated support for **OnePlus 5T `dumpling` / Qualcomm PMI8998**).

---

## How to Trigger the Automated APK Build on GitHub (In 15 Seconds on Mobile)

Because GitHub blocks external apps from pushing directly into `.github/workflows/` without elevated workflow permissions, the workflow file is saved in the root folder as **`github-workflow-build-apk.yml`**.

Once this repository is synced to your GitHub account:

1. Open your new repository on **GitHub.com** in your mobile browser.
2. Tap on the file **`github-workflow-build-apk.yml`**.
3. Tap the **Edit (Pencil icon)** and change the filename box at the top from:
   `github-workflow-build-apk.yml`
   to:
   `.github/workflows/build-apk.yml`
4. Tap **Commit changes**.
5. GitHub Actions will immediately start building your APK! After ~2–3 minutes, go to your repository's **Releases** page (`https://github.com/<your-username>/<your-repo>/releases`) and download **`StroboScope-Pro-debug.apk`**.

---

## Hardware Modes

- **Non-Root Camera2 API (`STANDARD`)**: Uses standard Android `CameraManager` Torch APIs (plus Android 13+ variable strength control where supported).
- **KernelSU / Root Sysfs (`ROOT / KSU`)**: Uses a persistent `su` root shell and `chmod 0666` direct kernel file descriptors on `/sys/class/leds/led:torch_0/brightness`, `led:torch_1/brightness`, and `led:switch_0/brightness` for sub-millisecond 1–200 Hz modulation on OnePlus 5T and rooted devices.
- **LSPosed Module (`LSPOSED`)**: Enable **StroboScope Pro** inside **LSPosed Manager** and select **System Framework (`android`)** and **SystemUI (`com.android.systemui`)** to unlock direct user-space sysfs writes.
