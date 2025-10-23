struct FragmentInput {
    @location(0) color : vec3<f32>,
    @location(1) size : f32,
    @location(2) extra : vec4<f32>,
};

struct FragmentOutput {
    @location(0) color : vec4<f32>,
};

@fragment
fn main(input : FragmentInput) -> FragmentOutput {
    var output : FragmentOutput;
    var alpha = clamp(input.extra.w, 0.0, 1.0);
    if (alpha == 0.0) {
        alpha = 1.0;
    }

    let glow = clamp(input.extra.x, 0.0, 1.0);
    let intensity = clamp(input.size * 0.5, 0.2, 1.5);
    let finalColor = input.color * (1.0 + glow * 0.5) * intensity;

    output.color = vec4<f32>(finalColor, alpha);
    return output;
}
