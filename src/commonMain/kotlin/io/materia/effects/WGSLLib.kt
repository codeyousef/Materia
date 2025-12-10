/**
 * WGSLLib - Library of reusable WGSL shader code snippets
 * 
 * Provides common shader functions for procedural generation, noise,
 * color manipulation, math utilities, and SDF primitives.
 * 
 * Usage:
 * ```kotlin
 * val fragmentShader = """
 *     ${WGSLLib.Hash.HASH_22}
 *     ${WGSLLib.Noise.VALUE_2D}
 *     ${WGSLLib.Fractal.FBM}
 *     ${WGSLLib.Color.COSINE_PALETTE}
 *     
 *     @fragment
 *     fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
 *         let n = fbm(uv * 10.0, 6);
 *         let color = cosinePalette(n, palette.a, palette.b, palette.c, palette.d);
 *         return vec4<f32>(color, 1.0);
 *     }
 * """
 * ```
 */
package io.materia.effects

/**
 * Library of reusable WGSL shader code snippets
 */
object WGSLLib {
    
    /**
     * Hash functions for procedural generation
     */
    object Hash {
        /**
         * Hash function: vec2 -> f32
         * Returns a pseudo-random value in [0, 1] from a 2D input
         */
        const val HASH_21 = """
fn hash21(p: vec2<f32>) -> f32 {
    var p3 = fract(vec3<f32>(p.x, p.y, p.x) * 0.1031);
    p3 = p3 + dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}"""

        /**
         * Hash function: vec2 -> vec2
         * Returns a pseudo-random 2D vector from a 2D input
         */
        const val HASH_22 = """
fn hash22(p: vec2<f32>) -> vec2<f32> {
    var p3 = fract(vec3<f32>(p.x, p.y, p.x) * vec3<f32>(0.1031, 0.1030, 0.0973));
    p3 = p3 + dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}"""

        /**
         * Hash function: vec3 -> f32
         * Returns a pseudo-random value in [0, 1] from a 3D input
         */
        const val HASH_31 = """
fn hash31(p: vec3<f32>) -> f32 {
    var p3 = fract(p * 0.1031);
    p3 = p3 + dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}"""

        /**
         * Hash function: vec3 -> vec3
         * Returns a pseudo-random 3D vector from a 3D input
         */
        const val HASH_33 = """
fn hash33(p: vec3<f32>) -> vec3<f32> {
    var p3 = fract(p * vec3<f32>(0.1031, 0.1030, 0.0973));
    p3 = p3 + dot(p3, p3.yxz + 33.33);
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
         */
        const val VALUE_2D = """
fn valueNoise(p: vec2<f32>) -> f32 {
    let i = floor(p);
    let f = fract(p);
    
    // Cubic interpolation
    let u = f * f * (3.0 - 2.0 * f);
    
    // Four corners
    let a = hash21(i + vec2<f32>(0.0, 0.0));
    let b = hash21(i + vec2<f32>(1.0, 0.0));
    let c = hash21(i + vec2<f32>(0.0, 1.0));
    let d = hash21(i + vec2<f32>(1.0, 1.0));
    
    // Bilinear interpolation
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y) * 2.0 - 1.0;
}"""

        /**
         * 2D Perlin-like gradient noise
         * Returns smooth noise in approximately [-1, 1] range
         */
        const val PERLIN_2D = """
fn perlinNoise(p: vec2<f32>) -> f32 {
    let i = floor(p);
    let f = fract(p);
    
    // Quintic interpolation for smoother results
    let u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    
    // Gradient vectors
    let g00 = hash22(i + vec2<f32>(0.0, 0.0)) * 2.0 - 1.0;
    let g10 = hash22(i + vec2<f32>(1.0, 0.0)) * 2.0 - 1.0;
    let g01 = hash22(i + vec2<f32>(0.0, 1.0)) * 2.0 - 1.0;
    let g11 = hash22(i + vec2<f32>(1.0, 1.0)) * 2.0 - 1.0;
    
    // Dot products with distance vectors
    let d00 = dot(g00, f - vec2<f32>(0.0, 0.0));
    let d10 = dot(g10, f - vec2<f32>(1.0, 0.0));
    let d01 = dot(g01, f - vec2<f32>(0.0, 1.0));
    let d11 = dot(g11, f - vec2<f32>(1.0, 1.0));
    
    return mix(mix(d00, d10, u.x), mix(d01, d11, u.x), u.y);
}"""

        /**
         * 2D Simplex noise
         * Returns smooth noise in approximately [-1, 1] range
         */
        const val SIMPLEX_2D = """
fn simplexNoise(p: vec2<f32>) -> f32 {
    let K1 = 0.366025404; // (sqrt(3)-1)/2
    let K2 = 0.211324865; // (3-sqrt(3))/6
    
    let i = floor(p + (p.x + p.y) * K1);
    let a = p - i + (i.x + i.y) * K2;
    let o = select(vec2<f32>(0.0, 1.0), vec2<f32>(1.0, 0.0), a.x > a.y);
    let b = a - o + K2;
    let c = a - 1.0 + 2.0 * K2;
    
    let h = max(0.5 - vec3<f32>(dot(a, a), dot(b, b), dot(c, c)), vec3<f32>(0.0));
    let n = h * h * h * h * vec3<f32>(
        dot(a, hash22(i) * 2.0 - 1.0),
        dot(b, hash22(i + o) * 2.0 - 1.0),
        dot(c, hash22(i + 1.0) * 2.0 - 1.0)
    );
    
    return dot(n, vec3<f32>(70.0));
}"""

        /**
         * 2D Worley (cellular) noise
         * Returns distance to nearest feature point
         */
        const val WORLEY_2D = """
fn worleyNoise(p: vec2<f32>) -> f32 {
    let n = floor(p);
    let f = fract(p);
    
    var minDist = 1.0;
    
    for (var j = -1; j <= 1; j = j + 1) {
        for (var i = -1; i <= 1; i = i + 1) {
            let neighbor = vec2<f32>(f32(i), f32(j));
            let point = hash22(n + neighbor);
            let diff = neighbor + point - f;
            let dist = length(diff);
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
         */
        const val FBM = """
fn fbm(p: vec2<f32>, octaves: i32) -> f32 {
    var value = 0.0;
    var amplitude = 0.5;
    var frequency = 1.0;
    var pos = p;
    
    for (var i = 0; i < octaves; i = i + 1) {
        value = value + amplitude * valueNoise(pos * frequency);
        amplitude = amplitude * 0.5;
        frequency = frequency * 2.0;
    }
    
    return value;
}"""

        /**
         * Turbulence noise
         * Like fBm but uses absolute value for a more turbulent look
         */
        const val TURBULENCE = """
fn turbulence(p: vec2<f32>, octaves: i32) -> f32 {
    var value = 0.0;
    var amplitude = 0.5;
    var frequency = 1.0;
    var pos = p;
    
    for (var i = 0; i < octaves; i = i + 1) {
        value = value + amplitude * abs(valueNoise(pos * frequency));
        amplitude = amplitude * 0.5;
        frequency = frequency * 2.0;
    }
    
    return value;
}"""

        /**
         * Ridged multifractal noise
         * Creates ridge-like features
         */
        const val RIDGED = """
fn ridgedNoise(p: vec2<f32>, octaves: i32) -> f32 {
    var value = 0.0;
    var amplitude = 0.5;
    var frequency = 1.0;
    var weight = 1.0;
    var pos = p;
    
    for (var i = 0; i < octaves; i = i + 1) {
        var n = 1.0 - abs(valueNoise(pos * frequency));
        n = n * n * weight;
        weight = clamp(n * 2.0, 0.0, 1.0);
        value = value + amplitude * n;
        amplitude = amplitude * 0.5;
        frequency = frequency * 2.0;
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
fn cosinePalette(t: f32, a: vec3<f32>, b: vec3<f32>, c: vec3<f32>, d: vec3<f32>) -> vec3<f32> {
    return a + b * cos(6.28318 * (c * t + d));
}"""

        /**
         * HSV to RGB color conversion
         */
        const val HSV_TO_RGB = """
fn hsvToRgb(hsv: vec3<f32>) -> vec3<f32> {
    let h = hsv.x;
    let s = hsv.y;
    let v = hsv.z;
    
    let c = v * s;
    let x = c * (1.0 - abs(((h * 6.0) % 2.0) - 1.0));
    let m = v - c;
    
    var rgb: vec3<f32>;
    let hi = i32(h * 6.0) % 6;
    
    if (hi == 0) { rgb = vec3<f32>(c, x, 0.0); }
    else if (hi == 1) { rgb = vec3<f32>(x, c, 0.0); }
    else if (hi == 2) { rgb = vec3<f32>(0.0, c, x); }
    else if (hi == 3) { rgb = vec3<f32>(0.0, x, c); }
    else if (hi == 4) { rgb = vec3<f32>(x, 0.0, c); }
    else { rgb = vec3<f32>(c, 0.0, x); }
    
    return rgb + m;
}"""

        /**
         * RGB to HSV color conversion
         */
        const val RGB_TO_HSV = """
fn rgbToHsv(rgb: vec3<f32>) -> vec3<f32> {
    let cmax = max(rgb.r, max(rgb.g, rgb.b));
    let cmin = min(rgb.r, min(rgb.g, rgb.b));
    let delta = cmax - cmin;
    
    var h = 0.0;
    if (delta > 0.0) {
        if (cmax == rgb.r) {
            h = ((rgb.g - rgb.b) / delta) % 6.0;
        } else if (cmax == rgb.g) {
            h = (rgb.b - rgb.r) / delta + 2.0;
        } else {
            h = (rgb.r - rgb.g) / delta + 4.0;
        }
        h = h / 6.0;
        if (h < 0.0) { h = h + 1.0; }
    }
    
    let s = select(0.0, delta / cmax, cmax > 0.0);
    let v = cmax;
    
    return vec3<f32>(h, s, v);
}"""

        /**
         * sRGB to linear color space conversion
         */
        const val SRGB_TO_LINEAR = """
fn srgbToLinear(c: vec3<f32>) -> vec3<f32> {
    let cutoff = vec3<f32>(0.04045);
    let linear = c / 12.92;
    let gamma = pow((c + 0.055) / 1.055, vec3<f32>(2.4));
    return select(gamma, linear, c <= cutoff);
}"""

        /**
         * Linear to sRGB color space conversion
         */
        const val LINEAR_TO_SRGB = """
fn linearToSrgb(c: vec3<f32>) -> vec3<f32> {
    let cutoff = vec3<f32>(0.0031308);
    let linear = c * 12.92;
    let gamma = 1.055 * pow(c, vec3<f32>(1.0 / 2.4)) - 0.055;
    return select(gamma, linear, c <= cutoff);
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
fn remap(value: f32, inMin: f32, inMax: f32, outMin: f32, outMax: f32) -> f32 {
    return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
}"""

        /**
         * Cubic smoothstep (same as built-in smoothstep)
         */
        const val SMOOTHSTEP_CUBIC = """
fn smoothstepCubic(t: f32) -> f32 {
    return t * t * (3.0 - 2.0 * t);
}"""

        /**
         * Quintic smoothstep (smoother than cubic)
         */
        const val SMOOTHSTEP_QUINTIC = """
fn smoothstepQuintic(t: f32) -> f32 {
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}"""

        /**
         * 2D rotation matrix
         */
        const val ROTATION_2D = """
fn rotate2d(angle: f32) -> mat2x2<f32> {
    let c = cos(angle);
    let s = sin(angle);
    return mat2x2<f32>(c, -s, s, c);
}"""
    }
    
    /**
     * Signed Distance Field (SDF) primitive functions
     */
    object SDF {
        /**
         * Circle SDF
         */
        const val CIRCLE = """
fn sdCircle(p: vec2<f32>, r: f32) -> f32 {
    return length(p) - r;
}"""

        /**
         * Box SDF
         */
        const val BOX = """
fn sdBox(p: vec2<f32>, b: vec2<f32>) -> f32 {
    let d = abs(p) - b;
    return length(max(d, vec2<f32>(0.0))) + min(max(d.x, d.y), 0.0);
}"""

        /**
         * Rounded box SDF
         */
        const val ROUNDED_BOX = """
fn sdRoundedBox(p: vec2<f32>, b: vec2<f32>, r: f32) -> f32 {
    let q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2<f32>(0.0))) - r;
}"""

        /**
         * Line segment SDF
         */
        const val LINE = """
fn sdLine(p: vec2<f32>, a: vec2<f32>, b: vec2<f32>) -> f32 {
    let pa = p - a;
    let ba = b - a;
    let h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}"""
    }
}
