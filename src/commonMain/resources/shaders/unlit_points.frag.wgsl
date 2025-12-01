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
    // DEBUG: Output bright magenta to verify points are rendering
    output.color = vec4<f32>(1.0, 0.0, 1.0, 1.0);
    return output;
}
