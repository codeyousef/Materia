/**
 * Type aliases for backward compatibility
 * Maps io.materia.light.* to io.materia.lighting.* implementations
 */
package io.materia.light

import io.materia.lighting.DirectionalLightImpl
import io.materia.lighting.SpotLightImpl
import io.materia.lighting.PointLightImpl
import io.materia.lighting.HemisphereLightImpl
import io.materia.lighting.AmbientLightImpl
import io.materia.lighting.AreaLightImpl
import io.materia.lighting.RectAreaLightImpl

// Type aliases for light implementations
typealias DirectionalLight = DirectionalLightImpl
typealias SpotLight = SpotLightImpl
typealias PointLight = PointLightImpl
typealias HemisphereLight = HemisphereLightImpl
typealias AmbientLight = AmbientLightImpl
typealias AreaLight = AreaLightImpl
typealias RectAreaLight = RectAreaLightImpl
