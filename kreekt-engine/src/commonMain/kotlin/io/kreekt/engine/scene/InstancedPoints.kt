package io.kreekt.engine.scene

import io.kreekt.engine.material.UnlitPointsMaterial

class InstancedPoints(
    name: String,
    val instanceData: FloatArray,
    val componentsPerInstance: Int,
    val material: UnlitPointsMaterial
) : Node(name)
