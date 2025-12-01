# Android Vulkan Testing Guide

## Prerequisites

- Android SDK API 24+ (target: API 34)
- Android device with Vulkan 1.1+ support OR
- Android emulator with Vulkan graphics enabled (x86_64 recommended)

## Building the Examples

```bash
# Build and install Triangle example
./gradlew :examples:triangle-android:assembleDebug
./gradlew :examples:triangle-android:installDebug

# Build and install Embedding Galaxy example
./gradlew :examples:embedding-galaxy-android:assembleDebug
./gradlew :examples:embedding-galaxy-android:installDebug

# Build and install Force Graph example
./gradlew :examples:force-graph-android:assembleDebug
./gradlew :examples:force-graph-android:installDebug
```

## Manual Testing

### 1. Cold Start Test
- Uninstall the app completely
- Install fresh
- Launch and verify it starts within 10 seconds
- Check logcat for "Surface ready" message

### 2. Lifecycle Test
- Launch app
- Press Home button (background)
- Return to app (foreground)
- Repeat 5 times - should not crash or timeout

### 3. Rotation Test
- Launch app
- Rotate device multiple times
- Verify surface recreates successfully
- Check for smooth rendering after each rotation

### 4. Emulator Test (Most Likely to Fail Previously)
**AVD Configuration:**
- System: x86_64 Android 13+ (API 33+)
- Graphics: Hardware - GLES 3.0+
- OR Graphics: Swiftshader (software Vulkan)

**Test:**
- Launch app
- Wait for initialization (may take 4-8 seconds on emulator)
- Verify "Surface ready" appears before "Booting..."
- Check rendering starts without timeout

## Logcat Monitoring

```bash
# Filter for Materia Vulkan logs
adb logcat | grep -E "MateriaVk|TriangleActivity|EmbeddingGalaxy|ForceGraph"
```

### Expected Log Sequence

**Successful initialization:**
```
I/TriangleActivity: Surface created; waiting for dimensions before initializing
I/TriangleActivity: Surface ready (1080x2400); bootstrapping renderer
I/MateriaVk: Creating Vulkan surface (window=..., size=1080x2400)
I/MateriaVk: Created Vulkan surface (surface=...)
I/MateriaVk: Creating Vulkan swapchain (surface=..., extent=1080x2400, minImages=3)
I/TriangleActivity: Renderer boot succeeded: backend=VULKAN, device=...
```

**If timeout occurs (should be rare now):**
```
W/MateriaVk: Swapchain image not ready yet (attempt=5/30, timeoutMs=200, extent=1080x2400)
W/MateriaVk: Swapchain image not ready yet (attempt=10/30, timeoutMs=200, extent=1080x2400)
...
E/MateriaVk: Failed to acquire swapchain image after 30 attempts (swapchain=..., extent=1080x2400, totalWaitMs=6000)
E/TriangleActivity: Renderer bootstrap failed
```

## Known Issues

### Emulator Specific
- First frame may take 1-2 seconds after initialization (shader compilation)
- Software rendering (Swiftshader) may be slower but should still work
- Older emulators (API < 29) may have inconsistent Vulkan support

### Physical Device Specific
- Some devices (particularly < API 28) may have driver quirks
- Samsung devices may show brief black screen during surface creation (normal)
- Xiaomi/Oppo devices with aggressive power management may delay compositor

## Troubleshooting

### App shows "Vulkan support not advertised"
- Device/emulator doesn't support Vulkan
- App automatically falls back to headless mode (expected behavior)
- Solution: Use different device/emulator with Vulkan support

### Timeout after "Preparing surface..."
- Surface dimensions never became valid
- Check: `adb shell dumpsys SurfaceFlinger | grep "visible"`
- Solution: Restart app or device

### Timeout after "Booting..." message
- Swapchain creation succeeded but image acquisition timed out
- Rare with new fixes, but possible on heavily loaded systems
- Solution: Close other apps to free up GPU resources

### Black screen after successful boot
- Normal for first 1-2 frames while shaders compile
- If persists > 3 seconds, check logcat for rendering errors

## Performance Expectations

### Triangle Example
- Initialization: 2-6 seconds
- Frame time: 1-3ms (333+ FPS cap)
- Memory: ~50MB

### Embedding Galaxy Example
- Initialization: 3-8 seconds (loading 20k points)
- Frame time: 16-33ms (30-60 FPS)
- Memory: ~150MB

### Force Graph Example
- Initialization: 3-7 seconds (loading graph data)
- Frame time: 16-33ms (30-60 FPS)
- Memory: ~120MB

## Automated Testing (Future)

```bash
# Run connected device tests (when implemented)
./gradlew :examples:triangle-android:connectedAndroidTest

# UI Automator test script example
adb shell am instrument -w -r \
  -e class io.materia.examples.triangle.android.SmokeTest \
  io.materia.examples.triangle.android.test/androidx.test.runner.AndroidJUnitRunner
```

## Reporting Issues

When reporting Android Vulkan issues, include:
1. Full logcat output (filtered as shown above)
2. Device/emulator specifications
3. Android version and API level
4. Graphics mode (hardware/software)
5. Steps to reproduce
6. Time from "Surface created" to timeout/success

