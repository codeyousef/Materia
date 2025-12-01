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

    // DEBUG: Sample the source texture - just pass through for now
    output.color = textureSample(uColorTexture, uColorSampler, input.uv);
    return output;
}
