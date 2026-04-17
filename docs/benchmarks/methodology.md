# Benchmark Methodology

These README numbers are measured, hardware-specific samples generated from the repo's explicit benchmark tasks.

- Scenes: `Triangle`, `Embedding Galaxy`, and `Force Graph`
- Platforms: `JVM`, `Web`, and `Android emulator`
- Resolution: `1920x1080`
- Warmup: `120` frames
- Measured window: `600` frames
- Repeats: `3` per scene/platform
- Published metrics:
  - `boot_to_first_frame_ms`
  - `avg_fps`
  - `p95_frame_ms`
  - `peak_heap_delta_mb`
  - `workload_label`
  - `environment_label`

Notes:

- `boot_to_first_frame_ms` measures benchmark-mode startup through the first rendered frame for that platform runner.
- `avg_fps` and `p95_frame_ms` are derived from wall-clock frame intervals observed by the benchmark loop, not GPU timestamp queries.
- `peak_heap_delta_mb` is reported as a delta from the baseline heap captured before scene boot.
- Memory reporting is platform-specific by design:
  - JVM: JVM heap delta
  - Web: JS heap delta from `performance.memory` when Chrome exposes it
  - Android: app heap plus native heap estimate delta from the benchmark process
- Android rows are emulator results and must be treated as emulator / host-GPU-assisted measurements, not physical-device claims.
- The synthetic performance tests in the repo remain useful for validation guardrails, but they are not the source of the README benchmark table.
