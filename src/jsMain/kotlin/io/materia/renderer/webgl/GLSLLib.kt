/**
 * GLSLLib - Library of reusable GLSL shader code snippets for WebGL
 *
 * Provides common shader functions for procedural generation, noise,
 * color manipulation, math utilities, and SDF primitives.
 *
 * This is the WebGL/GLSL equivalent of WGSLLib for WebGPU/WGSL.
 *
 * Usage:
 * ```kotlin
 * val fragmentShader = """
 *     precision mediump float;
 *     varying vec2 vUv;
 *
 *     ${GLSLLib.Hash.HASH_22}
 *     ${GLSLLib.Noise.VALUE_2D}
 *     ${GLSLLib.Fractal.FBM}
 *     ${GLSLLib.Color.COSINE_PALETTE}
 *
 *     void main() {
 *         float n = fbm(vUv * 10.0, 6);
 *         vec3 color = cosinePalette(n, palette.a, palette.b, palette.c, palette.d);
 *         gl_FragColor = vec4(color, 1.0);
 *     }
 * """
 * ```
 */
package io.materia.renderer.webgl

/**
 * Library of reusable GLSL shader code snippets
 */
object GLSLLib {

    /**
     * Hash functions for procedural generation
     */
    object Hash {
        /**
         * Hash function: vec2 -> float
         * Returns a pseudo-random value in [0, 1] from a 2D input
         */
        const val HASH_21 = """
float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}"""

        /**
         * Hash function: vec2 -> vec2
         * Returns a pseudo-random 2D vector from a 2D input
         */
        const val HASH_22 = """
vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.x, p.y, p.x) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}"""

        /**
         * Hash function: vec3 -> float
         * Returns a pseudo-random value in [0, 1] from a 3D input
         */
        const val HASH_31 = """
float hash31(vec3 p) {
    vec3 p3 = fract(p * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}"""

        /**
         * Hash function: vec3 -> vec3
         * Returns a pseudo-random 3D vector from a 3D input
         */
        const val HASH_33 = """
vec3 hash33(vec3 p) {
    vec3 p3 = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}"""
    }

    /**
     * Noise functions for procedural generation
     */
    object Noise {
        /**
         * 2D Value noise
         * Returns smooth noise in approximately [-1, 1] range
         * Requires: Hash.HASH_21
         */
        const val VALUE_2D = """
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    
    // Cubic interpolation
    vec2 u = f * f * (3.0 - 2.0 * f);
    
    // Four corners
    float a = hash21(i + vec2(0.0, 0.0));
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    
    // Bilinear interpolation
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y) * 2.0 - 1.0;
}"""

        /**
         * 2D Perlin-like gradient noise
         * Returns smooth noise in approximately [-1, 1] range
         * Requires: Hash.HASH_22
         */
        const val PERLIN_2D = """
float perlinNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    
    // Quintic interpolation for smoother results
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    
    // Gradient vectors
    vec2 g00 = hash22(i + vec2(0.0, 0.0)) * 2.0 - 1.0;
    vec2 g10 = hash22(i + vec2(1.0, 0.0)) * 2.0 - 1.0;
    vec2 g01 = hash22(i + vec2(0.0, 1.0)) * 2.0 - 1.0;
    vec2 g11 = hash22(i + vec2(1.0, 1.0)) * 2.0 - 1.0;
    
    // Dot products with distance vectors
    float d00 = dot(g00, f - vec2(0.0, 0.0));
    float d10 = dot(g10, f - vec2(1.0, 0.0));
    float d01 = dot(g01, f - vec2(0.0, 1.0));
    float d11 = dot(g11, f - vec2(1.0, 1.0));
    
    return mix(mix(d00, d10, u.x), mix(d01, d11, u.x), u.y);
}"""

        /**
         * 2D Simplex noise
         * Returns smooth noise in approximately [-1, 1] range
         * Requires: Hash.HASH_22
         */
        const val SIMPLEX_2D = """
float simplexNoise(vec2 p) {
    const float K1 = 0.366025404; // (sqrt(3)-1)/2
    const float K2 = 0.211324865; // (3-sqrt(3))/6
    
    vec2 i = floor(p + (p.x + p.y) * K1);
    vec2 a = p - i + (i.x + i.y) * K2;
    vec2 o = (a.x > a.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec2 b = a - o + K2;
    vec2 c = a - 1.0 + 2.0 * K2;
    
    vec3 h = max(0.5 - vec3(dot(a, a), dot(b, b), dot(c, c)), 0.0);
    vec3 n = h * h * h * h * vec3(
        dot(a, hash22(i) * 2.0 - 1.0),
        dot(b, hash22(i + o) * 2.0 - 1.0),
        dot(c, hash22(i + 1.0) * 2.0 - 1.0)
    );
    
    return dot(n, vec3(70.0));
}"""

        /**
         * 2D Worley (cellular) noise
         * Returns distance to nearest feature point
         * Requires: Hash.HASH_22
         */
        const val WORLEY_2D = """
float worleyNoise(vec2 p) {
    vec2 n = floor(p);
    vec2 f = fract(p);
    
    float minDist = 1.0;
    
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 neighbor = vec2(float(i), float(j));
            vec2 point = hash22(n + neighbor);
            vec2 diff = neighbor + point - f;
            float dist = length(diff);
            minDist = min(minDist, dist);
        }
    }
    
    return minDist;
}"""
    }

    /**
     * Fractal noise functions
     */
    object Fractal {
        /**
         * Fractal Brownian Motion (fBm)
         * Sums multiple octaves of noise with decreasing amplitude
         * Requires: Noise.VALUE_2D (which requires Hash.HASH_21)
         */
        const val FBM = """
float fbm(vec2 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    vec2 pos = p;
    
    for (int i = 0; i < 16; i++) {
        if (i >= octaves) break;
        value += amplitude * valueNoise(pos * frequency);
        amplitude *= 0.5;
        frequency *= 2.0;
    }
    
    return value;
}"""

        /**
         * Turbulence noise
         * Like fBm but uses absolute value for a more turbulent look
         * Requires: Noise.VALUE_2D (which requires Hash.HASH_21)
         */
        const val TURBULENCE = """
float turbulence(vec2 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    vec2 pos = p;
    
    for (int i = 0; i < 16; i++) {
        if (i >= octaves) break;
        value += amplitude * abs(valueNoise(pos * frequency));
        amplitude *= 0.5;
        frequency *= 2.0;
    }
    
    return value;
}"""

        /**
         * Ridged multifractal noise
         * Creates ridge-like features
         * Requires: Noise.VALUE_2D (which requires Hash.HASH_21)
         */
        const val RIDGED = """
float ridgedNoise(vec2 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    float weight = 1.0;
    vec2 pos = p;
    
    for (int i = 0; i < 16; i++) {
        if (i >= octaves) break;
        float n = 1.0 - abs(valueNoise(pos * frequency));
        n = n * n * weight;
        weight = clamp(n * 2.0, 0.0, 1.0);
        value += amplitude * n;
        amplitude *= 0.5;
        frequency *= 2.0;
    }
    
    return value;
}"""
    }

    /**
     * Color utility functions
     */
    object Color {
        /**
         * Cosine color palette
         * Creates smooth color gradients using cosine functions
         * Based on Inigo Quilez's technique
         */
        const val COSINE_PALETTE = """
vec3 cosinePalette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a + b * cos(6.28318 * (c * t + d));
}"""

        /**
         * HSV to RGB color conversion
         */
        const val HSV_TO_RGB = """
vec3 hsvToRgb(vec3 hsv) {
    float h = hsv.x;
    float s = hsv.y;
    float v = hsv.z;
    
    float c = v * s;
    float x = c * (1.0 - abs(mod(h * 6.0, 2.0) - 1.0));
    float m = v - c;
    
    vec3 rgb;
    int hi = int(mod(h * 6.0, 6.0));
    
    if (hi == 0) rgb = vec3(c, x, 0.0);
    else if (hi == 1) rgb = vec3(x, c, 0.0);
    else if (hi == 2) rgb = vec3(0.0, c, x);
    else if (hi == 3) rgb = vec3(0.0, x, c);
    else if (hi == 4) rgb = vec3(x, 0.0, c);
    else rgb = vec3(c, 0.0, x);
    
    return rgb + m;
}"""

        /**
         * RGB to HSV color conversion
         */
        const val RGB_TO_HSV = """
vec3 rgbToHsv(vec3 rgb) {
    float cmax = max(rgb.r, max(rgb.g, rgb.b));
    float cmin = min(rgb.r, min(rgb.g, rgb.b));
    float delta = cmax - cmin;
    
    float h = 0.0;
    if (delta > 0.0) {
        if (cmax == rgb.r) {
            h = mod((rgb.g - rgb.b) / delta, 6.0);
        } else if (cmax == rgb.g) {
            h = (rgb.b - rgb.r) / delta + 2.0;
        } else {
            h = (rgb.r - rgb.g) / delta + 4.0;
        }
        h /= 6.0;
        if (h < 0.0) h += 1.0;
    }
    
    float s = (cmax > 0.0) ? delta / cmax : 0.0;
    float v = cmax;
    
    return vec3(h, s, v);
}"""

        /**
         * sRGB to linear color space conversion
         */
        const val SRGB_TO_LINEAR = """
vec3 srgbToLinear(vec3 c) {
    vec3 linear = c / 12.92;
    vec3 gamma = pow((c + 0.055) / 1.055, vec3(2.4));
    return mix(gamma, linear, step(c, vec3(0.04045)));
}"""

        /**
         * Linear to sRGB color space conversion
         */
        const val LINEAR_TO_SRGB = """
vec3 linearToSrgb(vec3 c) {
    vec3 linear = c * 12.92;
    vec3 gamma = 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055;
    return mix(gamma, linear, step(c, vec3(0.0031308)));
}"""
    }

    /**
     * Math utility functions
     */
    object Math {
        /**
         * Remap a value from one range to another
         */
        const val REMAP = """
float remap(float value, float inMin, float inMax, float outMin, float outMax) {
    return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
}"""

        /**
         * Cubic smoothstep (same as built-in smoothstep but explicit)
         */
        const val SMOOTHSTEP_CUBIC = """
float smoothstepCubic(float t) {
    return t * t * (3.0 - 2.0 * t);
}"""

        /**
         * Quintic smoothstep (smoother than cubic)
         */
        const val SMOOTHSTEP_QUINTIC = """
float smoothstepQuintic(float t) {
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}"""

        /**
         * 2D rotation matrix
         */
        const val ROTATION_2D = """
mat2 rotate2d(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, -s, s, c);
}"""

        /**
         * PI constant
         */
        const val PI = """
const float PI = 3.14159265359;"""

        /**
         * TAU constant (2 * PI)
         */
        const val TAU = """
const float TAU = 6.28318530718;"""
    }

    /**
     * Signed Distance Field (SDF) primitive functions
     */
    object SDF {
        /**
         * Circle SDF
         */
        const val CIRCLE = """
float sdCircle(vec2 p, float r) {
    return length(p) - r;
}"""

        /**
         * Box SDF
         */
        const val BOX = """
float sdBox(vec2 p, vec2 b) {
    vec2 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}"""

        /**
         * Rounded box SDF
         */
        const val ROUNDED_BOX = """
float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}"""

        /**
         * Line segment SDF
         */
        const val LINE = """
float sdLine(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}"""

        /**
         * Equilateral triangle SDF
         */
        const val TRIANGLE = """
float sdTriangle(vec2 p, float r) {
    const float k = sqrt(3.0);
    p.x = abs(p.x) - r;
    p.y = p.y + r / k;
    if (p.x + k * p.y > 0.0) p = vec2(p.x - k * p.y, -k * p.x - p.y) / 2.0;
    p.x -= clamp(p.x, -2.0 * r, 0.0);
    return -length(p) * sign(p.y);
}"""

        /**
         * Ring SDF
         */
        const val RING = """
float sdRing(vec2 p, float r, float thickness) {
    return abs(length(p) - r) - thickness;
}"""
    }

    /**
     * Common effect utilities
     */
    object Effects {
        /**
         * Vignette effect
         */
        const val VIGNETTE = """
float vignette(vec2 uv, float intensity, float smoothness) {
    vec2 center = uv - 0.5;
    float dist = length(center);
    return 1.0 - smoothstep(intensity - smoothness, intensity + smoothness, dist);
}"""

        /**
         * Film grain effect
         * Requires: Hash.HASH_21
         */
        const val FILM_GRAIN = """
float filmGrain(vec2 uv, float time, float intensity) {
    return (hash21(uv + time) - 0.5) * intensity;
}"""

        /**
         * Chromatic aberration helper
         */
        const val CHROMATIC_ABERRATION = """
vec3 chromaticAberration(sampler2D tex, vec2 uv, float amount) {
    vec2 offset = (uv - 0.5) * amount;
    float r = texture2D(tex, uv + offset).r;
    float g = texture2D(tex, uv).g;
    float b = texture2D(tex, uv - offset).b;
    return vec3(r, g, b);
}"""

        /**
         * Scanlines effect
         */
        const val SCANLINES = """
float scanlines(vec2 uv, float resolution, float intensity) {
    return 1.0 - intensity * abs(sin(uv.y * resolution * PI));
}"""
    }

    /**
     * Complete shader presets that combine multiple utilities
     */
    object Presets {
        /**
         * Standard fragment shader header with precision and varying
         */
        const val FRAGMENT_HEADER = """
precision mediump float;
varying vec2 vUv;
"""

        /**
         * Fragment shader header with standard uniforms
         */
        const val FRAGMENT_HEADER_WITH_UNIFORMS = """
precision mediump float;
varying vec2 vUv;

uniform float u_time;
uniform vec2 u_resolution;
uniform vec2 u_mouse;
"""

        /**
         * All hash functions combined
         */
        val ALL_HASH = """
${Hash.HASH_21}

${Hash.HASH_22}

${Hash.HASH_31}

${Hash.HASH_33}
"""

        /**
         * All noise functions (requires hash functions)
         */
        val ALL_NOISE = """
${ALL_HASH}

${Noise.VALUE_2D}

${Noise.PERLIN_2D}

${Noise.SIMPLEX_2D}

${Noise.WORLEY_2D}
"""

        /**
         * All color utilities combined
         */
        val ALL_COLOR = """
${Color.COSINE_PALETTE}

${Color.HSV_TO_RGB}

${Color.RGB_TO_HSV}

${Color.SRGB_TO_LINEAR}

${Color.LINEAR_TO_SRGB}
"""

        /**
         * All SDF primitives combined
         */
        val ALL_SDF = """
${SDF.CIRCLE}

${SDF.BOX}

${SDF.ROUNDED_BOX}

${SDF.LINE}

${SDF.TRIANGLE}

${SDF.RING}
"""
    }
}
