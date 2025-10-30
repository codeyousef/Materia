# Materia API Style Guide

This document captures the conventions we follow while building the public Materia API. The goals
are:

- Kotlin-first ergonomics with explicit intent.
- Predictable naming across multiplatform boundaries.
- Low allocation pressure in render-critical code.

The checklist below doubles as a review reference when adding APIs or porting code from legacy modules.

---

## Naming & Structure

- **Modules** use `kebab-case` (e.g. `materia-engine`, `materia-gpu`).
- **Packages** are `io.materia.<feature>` with two levels preferred (`io.materia.engine.camera`).
- **Types** are `UpperCamelCase`; **members** and **functions** use `lowerCamelCase`.
- **Constants** live inside a companion object or top-level object and use `UpperCamelCase`.
- Prefer *one* public entry point per feature (e.g. `RendererFactory.create`).

## Function Design

- Supply default arguments for what 80% of callers use.
- Favour named parameters at call site, especially for numeric literals.
- Use context receivers for scoped DSLs (e.g. `context(RenderPass) fun Mesh.draw()`).
- Mark escaping lambdas with contracts: `contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }`.
- Keep hot-path functions `inline` or `@JvmInline` value classes to avoid allocations.

## Data & Value Classes

- Prefer dedicated value classes for math types (`Vec3`, `Mat4`, `Color`) rather than primitive arrays.
- Expose copying helpers (`copy()`, `copyFrom`) and mutating setters on the same type.
- Avoid `!!`; surface nullable states explicitly (`Result` or sealed types where appropriate).

## Error Handling

- Use `Result<T>` for recoverable failures (adapter/device selection, IO).
- Throw only for programming errors (`IllegalArgumentException`, `IllegalStateException`).
- Report GPU or platform faults with domain-specific exceptions under `io.materia.renderer`.

## Concurrency

- Default to structured concurrency. Launch coroutines from the scope passed into the subsystem.
- Never allocate/collect inside the render loop; use `FrameArena` or `UniformRingBuffer`.
- On the Kotlin/Native side, avoid sharing mutable state across threads; pass immutable snapshots.

## KDoc Expectations

- Public types and functions require a one-line summary plus important parameter notes.
- Use fenced snippets (` ```kotlin `) for multi-line examples.
- Link related APIs with the `[Type]` syntax so Dokka produces navigable docs.

## Binary Compatibility

- Every public symbol lives behind `@PublishedApi` helpers or extension functions. Keep `internal` APIs for experimentation.
- Guard new inline classes with tests covering copying and conversion to avoid ABI drift.

Keeping to these rules makes Materia’s API predictable across Web, JVM, and native targets while
staying idiomatic to Kotlin developers.
