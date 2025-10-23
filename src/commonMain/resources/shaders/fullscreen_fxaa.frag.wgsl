struct FragmentInput {
    @location(0) uv : vec2<f32>,
};

struct FragmentOutput {
    @location(0) color : vec4<f32>,
};

@group(0) @binding(0)
var uColorTexture : texture_2d<f32>;

@group(0) @binding(1)
var uColorSampler : sampler;

@fragment
fn main(input : FragmentInput) -> FragmentOutput {
    var output : FragmentOutput;

    // Placeholder: sample the source texture directly. Real FXAA logic can be
    // layered on later without changing bindings or IO structures.
    let uv = input.uv * 0.5;
    output.color = textureSample(uColorTexture, uColorSampler, uv);
    return output;
}
