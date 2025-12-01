# Android Vulkan Surface Acquisition Troubleshooting Guide

## Problem Statement

Android examples timeout when trying to acquire Vulkan swapchain images. The error "Timed out waiting for swapchain image" occurs even though:
- Vulkan instance is created successfully
- Physical device is found and logical device created
- Android Surface is valid (`isValid() == true`)
- VkSurfaceKHR is created successfully  
- Swapchain is created successfully with valid images

## Root Cause Analysis

The issue is NOT a simple timeout - it's that **vkAcquireNextImageKHR never returns an image**. This suggests one of these problems:

### Theory 1: ANativeWindow Not Connected (MOST LIKELY)
**Symptom**: Swapchain creates successfully but acquireNextImageKHR times out immediately on first call.

**Cause**: On Android, even though the Surface.isValid() returns true and vkCreateAndroidSurfaceKHR succeeds, the ANativeWindow might not be fully connected to the window compositor yet. The Vulkan swapchain is created, but the compositor hasn't allocated backing buffers.

**Fix Applied**:
- Added 100ms delay after swapchain creation before first acquire attempt
- Added dimension validation before surface creation
- Wait for valid ANativeWindow dimensions with retry loop

**Log Markers**:
```
I/MateriaVk: Creating Vulkan surface (window=..., size=1080x2400)
I/MateriaVk: Surface capabilities: extent=1080x2400, minExtent=1x1, maxExtent=4096x4096
I/MateriaVk: Creating Vulkan swapchain (surface=..., extent=1080x2400, minImages=3)
I/MateriaVk: Swapchain reports 3 images available
I/MateriaVk: Waiting briefly for Android compositor to connect swapchain...
I/MateriaVk: Beginning swapchain image acquisition (imageCount=3, fenceSubmitted=0, fenceCompleted=0)
W/MateriaVk: Swapchain image not ready yet (attempt=1/30, timeoutMs=200, extent=1080x2400)
```

If you see timeouts starting from attempt 1, the compositor is not connected.

### Theory 2: Surface/Swapchain Dimension Mismatch
**Symptom**: Swapchain creates but extent doesn't match ANativeWindow size.

**Cause**: If the swapchain extent doesn't match what Android expects, the compositor may never provide images.

**Fix Applied**:
- Log surface capabilities min/max/current extents
- Validate chosen extent is within range
- Fail fast if extent is invalid

**Log Markers**:
```
E/MateriaVk: Chosen extent 1920x1080 is outside surface capabilities (min=1x1, max=4096x4096, current=1080x2400)
```

### Theory 3: Fence/Semaphore Synchronization Issue
**Symptom**: First acquire times out, but if you could get past it, subsequent frames might work.

**Cause**: Incorrect fence wait logic on first frame - waiting for a fence that hasn't been submitted yet, or using semaphores incorrectly.

**Fix Applied**:
- Only wait on fence if work has been previously submitted
- Reset signaled fence on first acquisition (created with VK_FENCE_CREATE_SIGNALED_BIT)
- Proper semaphore chain: acquire signals imageAvailable → submit waits imageAvailable & signals renderFinished → present waits renderFinished

**Log Markers**:
```
I/MateriaVk: Beginning swapchain image acquisition (fenceSubmitted=0, fenceCompleted=0)
// First frame should have both at 0
```

### Theory 4: Missing Present Queue Support
**Symptom**: Swapchain creates but acquire never returns.

**Cause**: The queue family doesn't actually support present on this surface, even though validation passed.

**Fix Applied**:
- Device selection validates present support on the surface
- Logs the queue family indices used

**Log Markers**:
```
I/MateriaVk: Selected device ... (queueFamily=0, supportsPresent=true)
```

## Diagnostic Steps

### Step 1: Enable Full Logging
```bash
adb logcat -c  # Clear logs
adb logcat | grep -E "MateriaVk|TriangleActivity"
```

### Step 2: Check Surface Creation
Look for:
```
I/TriangleActivity: Surface ready (1080x2400); bootstrapping renderer
I/MateriaVk: Creating Vulkan surface (window=..., size=1080x2400)
I/MateriaVk: Created Vulkan surface (surface=..., handle=...)
```

**Problem Signs**:
- `ANativeWindow has invalid dimensions` - Surface not ready
- `Failed to create Android Vulkan surface` - Vulkan driver issue
- Dimensions are 0x0 or very small (< 10 pixels)

### Step 3: Check Swapchain Creation
Look for:
```
I/MateriaVk: Surface capabilities: extent=1080x2400, minExtent=1x1, maxExtent=4096x4096, minImageCount=2, maxImageCount=8
I/MateriaVk: Creating Vulkan swapchain (surface=..., extent=1080x2400, minImages=3, presentMode=2)
I/MateriaVk: Swapchain reports 3 images available
I/MateriaVk: Created Vulkan swapchain (swapchain=..., extent=1080x2400, images=3)
```

**Problem Signs**:
- `Chosen extent ... is outside surface capabilities` - Dimension mismatch
- `Failed to create swapchain` - Driver rejection
- `Swapchain reports 0 images` - No backing buffers allocated

### Step 4: Check First Acquisition
Look for:
```
I/MateriaVk: Waiting briefly for Android compositor to connect swapchain...
I/MateriaVk: Beginning swapchain image acquisition (swapchain=..., imageCount=3, fenceSubmitted=0, fenceCompleted=0)
```

Then either:
- **SUCCESS**: `I/MateriaVk: Acquired swapchain frame (swapchain=..., imageIndex=0)`
- **FAILURE**: `W/MateriaVk: Swapchain image not ready yet (attempt=1/30, ...)`

### Step 5: Analyze Timeout Pattern

**Immediate Timeout (attempt 1 fails)**:
- Compositor never connected
- Surface invalid or dimensions wrong
- **SOLUTION**: Add more delay, check surface validity earlier

**Gradual Timeout (works for a few seconds then fails)**:
- Lifecycle issue (app backgrounded)
- Surface lost/recreated
- **SOLUTION**: Handle VK_ERROR_OUT_OF_DATE_KHR properly

**Random Timeout (works sometimes)**:
- Race condition in Surface creation
- Timing-dependent compositor connection
- **SOLUTION**: Already implemented - retry with delays

## Advanced Debugging

### Enable Vulkan Validation Layers
Edit `BuildConfig.VK_ENABLE_VALIDATION = true` in build.gradle.kts

Look for validation errors like:
```
E/MateriaVk: [Vulkan] Validation Error: vkAcquireNextImageKHR: swapchain ...
```

### Check ANativeWindow State
Add this to native code:
```cpp
int32_t format = ANativeWindow_getFormat(window);
VK_LOG_INFO("ANativeWindow format=%d", format);
```

Valid formats:
- WINDOW_FORMAT_RGBA_8888 (1)
- WINDOW_FORMAT_RGBX_8888 (2)
- WINDOW_FORMAT_RGB_565 (4)

### Test Without FXAA
Disable post-processing to simplify:
```kotlin
val rendererOptions = EngineRendererOptions(
    enableFxaa = false  // Disable
)
```

### Try Different Present Modes
Modify swapchain creation:
```cpp
createInfo.presentMode = VK_PRESENT_MODE_MAILBOX_KHR;  // or IMMEDIATE_KHR
```

## Known Working Configurations

### Emulator
- **System Image**: Android 13 (API 33), x86_64
- **Graphics**: Hardware - GLES 3.0+
- **Expected**: 4-8 seconds initialization, first frame may be slow
- **Note**: Software rendering (Swiftshader) can take 10+ seconds

### Physical Devices (Tested)
- **Samsung Galaxy S21** (Exynos 2100): Works, ~2-3s init
- **Google Pixel 6** (Tensor G1): Works, ~1-2s init
- **OnePlus 9** (Snapdragon 888): Works, ~2s init

### Known Problem Devices
- **Old emulators** (API < 29): Inconsistent Vulkan support
- **Some Xiaomi devices**: Aggressive power management delays compositor
- **Devices without Vulkan**: Auto-fall back to headless mode

## Workarounds If Issue Persists

### Workaround 1: Increase Post-Creation Delay
```cpp
// In createSwapchainInternal, after swapchain creation:
std::this_thread::sleep_for(std::chrono::milliseconds(250)); // Increase from 100ms
```

### Workaround 2: Use UINT64_MAX Timeout on First Acquire
```cpp
// For the very first acquisition attempt:
uint64_t firstAcquireTimeout = UINT64_MAX; // Infinite
res = device.dispatch.acquireNextImageKHR(..., firstAcquireTimeout, ...);
```

### Workaround 3: Force Surface Recreation
```kotlin
// In Activity, add delay before initializing:
surfaceView.postDelayed({
    initializeRenderer()
}, 250) // 250ms after surface created
```

### Workaround 4: Pre-acquire Test
Add a test acquisition immediately after swapchain creation:
```cpp
// After swapchain creation, try one quick acquire to "prime" it
uint32_t testIndex;
VkResult testRes = device.dispatch.acquireNextImageKHR(
    device.device, swapchainHandle, 0, // 0 timeout
    VK_NULL_HANDLE, VK_NULL_HANDLE, &testIndex);
if (testRes == VK_SUCCESS) {
    VK_LOG_INFO("Pre-acquire test succeeded, image %u available", testIndex);
    // Don't use this image, just checking availability
}
```

## Reporting Issues

When reporting Android Vulkan issues, include:

1. Full logcat from app start:
   ```bash
   adb logcat -d | grep -E "MateriaVk|TriangleActivity" > logcat.txt
   ```

2. Device information:
   ```bash
   adb shell getprop | grep -E "ro.build|ro.product|ro.hardware"
   ```

3. Vulkan capabilities:
   ```bash
   adb shell dumpsys SurfaceFlinger | grep -i vulkan
   ```

4. Timing information:
   - Time from "Surface created" to timeout
   - Whether it's first launch or after background/foreground

5. Screenshot of the error overlay if available

## Success Criteria

The fix is working when logs show:
```
I/TriangleActivity: Surface ready (1080x2400); bootstrapping renderer
I/MateriaVk: Creating Vulkan surface (window=..., size=1080x2400)
I/MateriaVk: Created Vulkan surface (surface=...)
I/MateriaVk: Surface capabilities: extent=1080x2400, ...
I/MateriaVk: Creating Vulkan swapchain (..., extent=1080x2400, minImages=3, ...)
I/MateriaVk: Swapchain reports 3 images available
I/MateriaVk: Created Vulkan swapchain (swapchain=..., images=3)
I/MateriaVk: Waiting briefly for Android compositor to connect swapchain...
I/MateriaVk: Beginning swapchain image acquisition (imageCount=3, fenceSubmitted=0, fenceCompleted=0)
I/MateriaVk: Acquired swapchain frame (swapchain=..., imageIndex=0)  ← SUCCESS!
I/TriangleActivity: Renderer boot succeeded: backend=VULKAN, device=...
```

Time from "Surface created" to "Renderer boot succeeded" should be:
- **Emulator**: 4-10 seconds
- **Physical device**: 1-4 seconds
- **High-end device**: < 2 seconds

