# Force Graph Re-rank Example

This example visualises a mid-sized knowledge graph and lets you toggle between TF-IDF and semantic edge weighting.
Positions tween over 500 ms so you can see how the re-rank changes cluster density.

## Run Commands

- JVM: `./gradlew :examples:force-graph-rerank:run`
- JS (browser): `./gradlew :examples:force-graph-rerank:jsBrowserRun`

### Controls

- `T` – switch to TF-IDF mode
- `S` – switch to semantic mode
- `Space` – toggle between the two modes

The JVM build writes a thumbnail to `examples/_captures/force-graph-rerank.png` after the layout stabilises.
