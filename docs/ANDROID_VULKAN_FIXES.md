# Android Vulkan Surface Acquisition Timeout Fixes

## Problem

Android examples were experiencing timeout failures when trying to acquire Vulkan swapchain images, particularly on:
- Physical devices (slower initialization)
- Android emulators (compositor delays)
- Devices with rapid lifecycle changes

The error manifested as:
```
Timed out waiting for swapchain image
```

## Root Causes

1. **Surface Readiness Race Condition**: The `SurfaceView` was being passed to Vulkan initialization before it was fully ready (dimensions not yet available)
2. **Insufficient Timeout**: The native code had 10 attempts × 500ms = 5 seconds total, which wasn't enough for slower devices
3. **No Dimension Validation**: The ANativeWindow dimensions weren't validated before creating the Vulkan surface
4. **Kotlin-side Timeout Mismatch**: The Kotlin timeout (5s) was shorter than the potential native timeout

## Solutions Implemented

### 1. Native Layer (vulkan_bridge.cpp)

#### Surface Creation Improvements
- **Added dimension validation** with retry logic for ANativeWindow
- Wait up to 1 second (20 attempts × 50ms) for valid dimensions before failing
- Log clear errors when dimensions remain invalid
- Added VkResult error logging for better debugging

```cpp
// Validate dimensions - if zero, the surface might not be ready yet
if (windowWidth <= 0 || windowHeight <= 0) {
    VK_LOG_WARN("ANativeWindow has invalid dimensions; waiting for valid size");
    
    int retries = 20;
    while ((windowWidth <= 0 || windowHeight <= 0) && retries-- > 0) {
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
        windowWidth = ANativeWindow_getWidth(window);
        windowHeight = ANativeWindow_getHeight(window);
    }
}
```

#### Swapchain Acquisition Improvements
- **Reduced timeout per attempt**: 500ms → 200ms (more responsive to transient delays)
- **Increased total attempts**: 10 → 30 (total timeout: 6 seconds vs previous 5 seconds)
- **Added sleep between attempts**: Brief 10ms yield to avoid tight spinning
- **Reduced log spam**: Only log every 5th attempt or last attempt
- **Enhanced error messages**: Include extent dimensions and total wait time

```cpp
constexpr uint64_t acquireTimeoutNs = 200'000'000ull; // 200 ms per attempt
constexpr int maxAcquireAttempts = 30; // Total 6 seconds
```

### 2. Kotlin/Android Layer (Activity Classes)

#### Surface Readiness Check
All Android activities now wait for valid surface dimensions before initializing Vulkan:

```kotlin
private val holderCallback = object : SurfaceHolder.Callback {
    override fun surfaceCreated(holder: SurfaceHolder) {
        overlayView.text = "Preparing surface…"
        
        // Post initialization to ensure surface is fully ready
        surfaceView.post {
            val width = surfaceView.width
            val height = surfaceView.height
            if (width > 0 && height > 0) {
                initializeRenderer()
            } else {
                // Retry with 100ms delay if dimensions not ready
                surfaceView.postDelayed({
                    initializeRenderer()
                }, 100)
            }
        }
    }
}
```

#### Increased Kotlin-side Timeouts
- **Triangle example**: 5s → 10s
- **Force Graph example**: 5s → 10s
- **Embedding Galaxy example**: Already had 12s (kept)

## Files Modified

### Native Layer
- `materia-gpu-android-native/src/main/cpp/vulkan_bridge.cpp`
  - Added `<chrono>` and `<thread>` headers
  - Enhanced `createSurfaceInternal()` with dimension validation
  - Improved `acquireNextImageKHR` retry logic

### Kotlin Activities
- `examples/triangle-android/src/main/java/.../TriangleActivity.kt`
  - Added surface readiness check
  - Increased timeout to 10s

- `examples/embedding-galaxy-android/src/main/java/.../EmbeddingGalaxyActivity.kt`
  - Added surface readiness check
  - Kept existing 12s timeout

- `examples/force-graph-android/src/main/java/.../ForceGraphActivity.kt`
  - Added surface readiness check
  - Increased timeout to 10s

## Testing Recommendations

1. **Physical Devices**: Test on slower/older devices (API 24-28)
2. **Emulators**: Test on both x86_64 and arm64 emulators with different graphics modes
3. **Lifecycle**: Rapidly rotate device, background/foreground the app
4. **Cold Start**: Test first launch after install when compositor may be slower

## Expected Behavior

- Initial surface preparation message appears briefly
- Vulkan initialization completes within 2-6 seconds on most devices
- Clear error messages if timeout still occurs (with device/extent info in logcat)
- Automatic fallback to headless mode on unsupported devices

## Debugging

If timeout issues persist, check logcat for:
```
VK_LOG_WARN: ANativeWindow has invalid dimensions
VK_LOG_WARN: Swapchain image not ready yet (attempt=X/30)
VK_LOG_ERROR: Failed to acquire swapchain image after 30 attempts
```

The logs now include:
- ANativeWindow dimensions during surface creation
- Swapchain extent during acquisition
- Total wait time in milliseconds
- VkResult error codes

## Future Improvements

1. Consider adaptive timeout based on device capabilities
2. Add telemetry to track acquisition times across devices
3. Implement exponential backoff for retry logic
4. Add surface validity checks before each frame acquisition

