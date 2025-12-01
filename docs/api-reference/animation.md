# Animation API Reference

The animation module provides a complete system for keyframe, skeletal, and morph target animations.

## Overview

```kotlin
import io.materia.animation.*
```

---

## AnimationMixer

Controls playback of animations on a scene graph.

### Constructor

```kotlin
class AnimationMixer(root: Object3D)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `time` | `Float` | Global mixer time (seconds) |
| `timeScale` | `Float` | Playback speed multiplier |

### Methods

```kotlin
// Create an action from a clip
fun clipAction(
    clip: AnimationClip,
    root: Object3D = this.root,
    blendMode: BlendMode = BlendMode.NORMAL
): AnimationAction

// Get existing action for clip
fun existingAction(clip: AnimationClip, root: Object3D = this.root): AnimationAction?

// Stop all actions
fun stopAllAction(): AnimationMixer

// Update animations (call each frame)
fun update(deltaTime: Float): AnimationMixer

// Set global time
fun setTime(time: Float): AnimationMixer

// Get root object
fun getRoot(): Object3D

// Uncache clip/action/root
fun uncacheClip(clip: AnimationClip)
fun uncacheAction(clip: AnimationClip, root: Object3D? = null)
fun uncacheRoot(root: Object3D)
```

### Events

```kotlin
// Animation events
mixer.addEventListener("loop") { event ->
    val action = event.action
    val loopDelta = event.loopDelta
}

mixer.addEventListener("finished") { event ->
    val action = event.action
    val direction = event.direction
}
```

### Example

```kotlin
// Load model with animations
gltfLoader.load("models/character.glb") { gltf ->
    val model = gltf.scene
    scene.add(model)
    
    // Create mixer
    val mixer = AnimationMixer(model)
    
    // Play first animation
    val action = mixer.clipAction(gltf.animations[0])
    action.play()
    
    // In render loop
    fun animate(deltaTime: Float) {
        mixer.update(deltaTime)
        renderer.render(scene, camera)
    }
}
```

---

## AnimationAction

Controls a single animation clip on a mixer.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `true` | Action is active |
| `loop` | `LoopMode` | `REPEAT` | Loop behavior |
| `repetitions` | `Int` | `Infinity` | Number of loops |
| `paused` | `Boolean` | `false` | Pause playback |
| `time` | `Float` | `0` | Current local time |
| `timeScale` | `Float` | `1` | Playback speed |
| `weight` | `Float` | `1` | Blend weight |
| `clampWhenFinished` | `Boolean` | `false` | Hold last frame |
| `zeroSlopeAtStart` | `Boolean` | `true` | Smooth start |
| `zeroSlopeAtEnd` | `Boolean` | `true` | Smooth end |

### Loop Modes

```kotlin
enum class LoopMode {
    ONCE,        // Play once, then stop
    REPEAT,      // Loop continuously
    PING_PONG    // Play forward, then backward
}
```

### Methods

```kotlin
// Playback control
fun play(): AnimationAction
fun stop(): AnimationAction
fun reset(): AnimationAction

// Pause control
fun pause(): AnimationAction
fun unpause(): AnimationAction

// Time control
fun setTime(time: Float): AnimationAction
fun setDuration(duration: Float): AnimationAction

// Weight/blending
fun setEffectiveWeight(weight: Float): AnimationAction
fun getEffectiveWeight(): Float
fun fadeIn(duration: Float): AnimationAction
fun fadeOut(duration: Float): AnimationAction
fun crossFadeFrom(fadeOutAction: AnimationAction, duration: Float, warp: Boolean = false): AnimationAction
fun crossFadeTo(fadeInAction: AnimationAction, duration: Float, warp: Boolean = false): AnimationAction

// Time scale
fun setEffectiveTimeScale(scale: Float): AnimationAction
fun getEffectiveTimeScale(): Float
fun warp(startScale: Float, endScale: Float, duration: Float): AnimationAction

// Sync
fun syncWith(action: AnimationAction): AnimationAction
fun halt(duration: Float): AnimationAction

// Status
fun isRunning(): Boolean
fun isScheduled(): Boolean

// Get clip
fun getClip(): AnimationClip
fun getMixer(): AnimationMixer
fun getRoot(): Object3D
```

### Example

```kotlin
// Single animation
val idleAction = mixer.clipAction(idleClip)
idleAction.play()

// Crossfade between animations
val runAction = mixer.clipAction(runClip)
idleAction.crossFadeTo(runAction, 0.5f)
runAction.play()

// One-shot animation
val jumpAction = mixer.clipAction(jumpClip)
jumpAction.loop = LoopMode.ONCE
jumpAction.clampWhenFinished = true
jumpAction.play()
```

---

## AnimationClip

Container for keyframe tracks that define an animation.

### Constructor

```kotlin
class AnimationClip(
    name: String = "",
    duration: Float = -1f,
    tracks: List<KeyframeTrack> = emptyList(),
    blendMode: BlendMode = BlendMode.NORMAL
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `name` | `String` | Clip name |
| `duration` | `Float` | Duration in seconds (-1 = auto) |
| `tracks` | `List<KeyframeTrack>` | Keyframe tracks |
| `uuid` | `String` | Unique identifier |
| `blendMode` | `BlendMode` | Blending behavior |

### Static Methods

```kotlin
// Find clip by name in array
fun findByName(clips: List<AnimationClip>, name: String): AnimationClip?

// Create clips from morph target sequence
fun CreateFromMorphTargetSequence(
    name: String,
    morphTargets: List<MorphTarget>,
    fps: Float,
    noLoop: Boolean = false
): AnimationClip

// Create clips from skeleton data
fun CreateFromBoneAnimation(
    name: String,
    skeleton: Skeleton,
    boneAnimation: BoneAnimation
): AnimationClip

// Parse JSON animation data
fun parse(json: JsonObject): AnimationClip

// Parse array of clips
fun parseAnimation(json: JsonObject, bones: List<Bone>): List<AnimationClip>
```

### Methods

```kotlin
// Reset clip duration from tracks
fun resetDuration(): AnimationClip

// Trim/extend clip
fun trim(): AnimationClip

// Validate tracks
fun validate(): Boolean

// Optimize tracks (remove redundant keyframes)
fun optimize(): AnimationClip

// Clone clip
fun clone(): AnimationClip
```

---

## KeyframeTrack

Base class for animation tracks. Interpolates values over time.

### Subclasses

```kotlin
// Number tracks
class NumberKeyframeTrack(
    name: String,
    times: FloatArray,
    values: FloatArray,
    interpolation: Interpolation = Interpolation.LINEAR
)

// Vector tracks
class VectorKeyframeTrack(
    name: String,
    times: FloatArray,
    values: FloatArray,  // Flattened vector components
    interpolation: Interpolation = Interpolation.LINEAR
)

// Quaternion tracks (spherical interpolation)
class QuaternionKeyframeTrack(
    name: String,
    times: FloatArray,
    values: FloatArray  // x, y, z, w per keyframe
)

// Color tracks
class ColorKeyframeTrack(
    name: String,
    times: FloatArray,
    values: FloatArray  // r, g, b per keyframe
)

// Boolean tracks (step interpolation)
class BooleanKeyframeTrack(
    name: String,
    times: FloatArray,
    values: BooleanArray
)

// String tracks (step interpolation)
class StringKeyframeTrack(
    name: String,
    times: FloatArray,
    values: Array<String>
)
```

### Track Name Format

Track names follow the pattern: `objectName.propertyName[.subProperty]`

```kotlin
// Position track
"mesh.position"

// Single axis
"mesh.position[x]"

// Rotation
"mesh.quaternion"

// Material property
"mesh.material.opacity"

// Morph target
"mesh.morphTargetInfluences[smile]"

// Bone transform
"skeleton.bones[LeftArm].quaternion"
```

### Interpolation Modes

```kotlin
enum class Interpolation {
    DISCRETE,   // Step/hold
    LINEAR,     // Linear interpolation
    SMOOTH      // Smooth (cubic) interpolation
}
```

### Example

```kotlin
// Create position animation
val positionTrack = VectorKeyframeTrack(
    name = "mesh.position",
    times = floatArrayOf(0f, 1f, 2f),
    values = floatArrayOf(
        0f, 0f, 0f,    // t=0
        0f, 2f, 0f,    // t=1
        0f, 0f, 0f     // t=2
    )
)

// Create rotation animation
val rotationTrack = QuaternionKeyframeTrack(
    name = "mesh.quaternion",
    times = floatArrayOf(0f, 2f),
    values = floatArrayOf(
        0f, 0f, 0f, 1f,                    // t=0 (identity)
        0f, 0.707f, 0f, 0.707f             // t=2 (90° around Y)
    )
)

// Create opacity animation
val opacityTrack = NumberKeyframeTrack(
    name = "mesh.material.opacity",
    times = floatArrayOf(0f, 0.5f, 1f),
    values = floatArrayOf(1f, 0f, 1f)
)

// Combine into clip
val clip = AnimationClip(
    name = "bounce",
    duration = 2f,
    tracks = listOf(positionTrack, rotationTrack, opacityTrack)
)
```

---

## PropertyBinding

Binds a track to a property on an object.

### Constructor

```kotlin
class PropertyBinding(
    rootNode: Object3D,
    path: String,
    parsedPath: ParsedPath? = null
)
```

### Static Methods

```kotlin
// Parse track path
fun parseTrackName(trackName: String): ParsedPath

// Find node by name
fun findNode(root: Object3D, nodeName: String): Object3D?
```

---

## AnimationObjectGroup

Groups objects for synchronized animation.

### Constructor

```kotlin
class AnimationObjectGroup(vararg objects: Object3D)
```

### Methods

```kotlin
// Add objects to group
fun add(vararg objects: Object3D)

// Remove objects from group
fun remove(vararg objects: Object3D)

// Uncache objects
fun uncache(vararg objects: Object3D)
```

---

## Skeletal Animation

### Skeleton

Manages a hierarchy of bones.

```kotlin
class Skeleton(
    bones: List<Bone> = emptyList(),
    boneInverses: List<Matrix4>? = null
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `bones` | `List<Bone>` | Bone hierarchy |
| `boneInverses` | `List<Matrix4>` | Inverse bind matrices |
| `boneMatrices` | `FloatArray` | Flattened bone matrices |
| `boneTexture` | `DataTexture?` | Bone data for GPU |

### Methods

```kotlin
// Calculate bone matrices from current pose
fun update()

// Pose skeleton from clip
fun pose()

// Calculate inverse matrices
fun calculateInverses()

// Get bone by name
fun getBoneByName(name: String): Bone?

// Clone skeleton
fun clone(): Skeleton

// Dispose resources
fun dispose()
```

### Bone

A single bone in the skeleton hierarchy.

```kotlin
class Bone : Object3D() {
    val type: String = "Bone"
}
```

### SkinnedMesh

Mesh with skeletal animation support.

```kotlin
class SkinnedMesh(
    geometry: BufferGeometry,
    material: Material
) : Mesh(geometry, material) {
    
    var bindMode: BindMode = BindMode.ATTACHED
    var bindMatrix: Matrix4 = Matrix4()
    var bindMatrixInverse: Matrix4 = Matrix4()
    var skeleton: Skeleton? = null
    
    fun bind(skeleton: Skeleton, bindMatrix: Matrix4? = null)
    fun pose()
    fun normalizeSkinWeights()
}
```

### Example

```kotlin
// Load skinned model
gltfLoader.load("models/character.glb") { gltf ->
    val model = gltf.scene
    scene.add(model)
    
    // Find skinned mesh
    var skinnedMesh: SkinnedMesh? = null
    model.traverse { obj ->
        if (obj is SkinnedMesh) {
            skinnedMesh = obj
        }
    }
    
    // Get skeleton
    val skeleton = skinnedMesh?.skeleton
    
    // Visualize skeleton
    val helper = SkeletonHelper(model)
    scene.add(helper)
    
    // Play animation
    val mixer = AnimationMixer(model)
    val action = mixer.clipAction(gltf.animations[0])
    action.play()
}
```

---

## Morph Targets

### MorphTarget

Defines a blend shape deformation.

### Usage on Geometry

```kotlin
// Geometry with morph targets
val geometry = BoxGeometry(1f, 1f, 1f)

// Create morph target (flattened positions)
val smilePositions = floatArrayOf(
    // Modified vertex positions for "smile" shape
    // ...
)

geometry.morphAttributes["position"] = listOf(
    Float32BufferAttribute(smilePositions, 3)
)

// Create mesh
val mesh = Mesh(geometry, material)
mesh.morphTargetInfluences[0] = 0.5f  // 50% smile
```

### Animation with Morph Targets

```kotlin
// Create morph animation
val morphTrack = NumberKeyframeTrack(
    name = "mesh.morphTargetInfluences[smile]",
    times = floatArrayOf(0f, 1f, 2f),
    values = floatArrayOf(0f, 1f, 0f)
)

val clip = AnimationClip("smile", 2f, listOf(morphTrack))
val action = mixer.clipAction(clip)
action.play()
```

---

## Animation Utilities

### AnimationUtils

Static utility functions.

```kotlin
object AnimationUtils {
    // Convert seconds to frames
    fun convertSecondsToFrames(seconds: Float, fps: Float): Int
    
    // Get all keyframe times
    fun getKeyframeTimes(clip: AnimationClip): FloatArray
    
    // Merge clips
    fun mergeClips(clips: List<AnimationClip>): AnimationClip
    
    // Retarget clip to different skeleton
    fun retargetClip(
        clip: AnimationClip,
        sourceRoot: Object3D,
        targetRoot: Object3D
    ): AnimationClip
    
    // Create pose clip (single frame)
    fun createPoseClip(
        name: String,
        root: Object3D
    ): AnimationClip
}
```

---

## Complete Animation Example

```kotlin
class AnimatedCharacter(private val scene: Scene) {
    
    private lateinit var model: Object3D
    private lateinit var mixer: AnimationMixer
    private val actions = mutableMapOf<String, AnimationAction>()
    private var currentAction: AnimationAction? = null
    
    fun load(onLoaded: () -> Unit) {
        GLTFLoader().load("models/character.glb") { gltf ->
            model = gltf.scene
            scene.add(model)
            
            // Setup mixer
            mixer = AnimationMixer(model)
            
            // Create actions for all clips
            for (clip in gltf.animations) {
                val action = mixer.clipAction(clip)
                actions[clip.name] = action
                
                // Prepare all actions
                action.enabled = true
                action.setEffectiveWeight(0f)
            }
            
            // Start with idle
            playAnimation("idle")
            
            onLoaded()
        }
    }
    
    fun playAnimation(name: String, fadeTime: Float = 0.3f) {
        val newAction = actions[name] ?: return
        
        if (currentAction == newAction) return
        
        newAction.reset()
        newAction.setEffectiveWeight(1f)
        newAction.play()
        
        currentAction?.let { old ->
            old.crossFadeTo(newAction, fadeTime)
        }
        
        currentAction = newAction
    }
    
    fun update(deltaTime: Float) {
        mixer.update(deltaTime)
    }
}

// Usage
val character = AnimatedCharacter(scene)
character.load {
    println("Character loaded!")
}

// On input
fun onKeyDown(key: String) {
    when (key) {
        "w" -> character.playAnimation("walk")
        "shift+w" -> character.playAnimation("run")
        else -> character.playAnimation("idle")
    }
}

// In render loop
fun animate(deltaTime: Float) {
    character.update(deltaTime)
    renderer.render(scene, camera)
}
```

---

## See Also

- [Loader API](loader.md) - Loading animated models
- [Core API](core.md) - Vector3, Quaternion for transforms
- [Controls API](controls.md) - Character controllers
