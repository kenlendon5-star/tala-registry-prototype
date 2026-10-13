---
name: android-debugging
description: Logcat, crashes, ANRs, performance investigations and memory diagnosis. Use when the task concerns this Android development area.
---

# Android Debugging & Performance Expert

## Apply when
Investigating crashes, ANRs, freezes, excessive resource use, or unexpected behavior.

## Process
1. Capture reproducible steps, device/OS/build variant, and relevant Logcat/stack traces.
2. Identify the first application-owned stack frame and distinguish symptom from root cause.
3. Form a narrow hypothesis, inspect the corresponding code, and apply one minimal fix.
4. Add regression coverage and reproduce again if an emulator/device is available.
5. Report confidence and any unverified assumptions.

## Performance
- Profile before optimizing: use Android Studio profiler, system traces, Layout Inspector, and Compose metrics when available.
- Investigate main-thread work, blocking calls, unnecessary recomposition, unbounded lists and image allocations.
- Avoid retaining Activity/Context references in long-lived objects; clean up callbacks and listeners.
- Check battery, network, disk, and startup performance using representative devices.
- Never log tokens, passwords, personal data, or production secrets.

## Useful commands
- `adb logcat -d`
- `adb shell dumpsys meminfo <package>`
- `./gradlew :app:assembleDebug`
