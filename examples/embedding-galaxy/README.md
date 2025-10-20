# Embedding Galaxy Example

The Embedding Galaxy demo renders 20k synthetic embeddings as an animated point cloud. Every few seconds a new query vector triggers a shockwave that highlights the nearest neighbours.

## Run Commands

- JVM: `./gradlew :examples:embedding-galaxy:run`
- JS (browser): `./gradlew :examples:embedding-galaxy:jsBrowserRun`

### Controls

- `Q` – trigger a new query shockwave
- `R` – reset the animation loop
- `P` – pause/resume updates

Running the JVM target saves a capture to `examples/_captures/embedding-galaxy.png` once the scene has settled.
