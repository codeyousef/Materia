struct FragmentInput {
    @location(0) color : vec3<f32>,
};

struct FragmentOutput {
    @location(0) color : vec4<f32>,
};

@fragment
fn main(input : FragmentInput) -> FragmentOutput {
    var output : FragmentOutput;
    output.color = vec4<f32>(input.color, 1.0);
    return output;
}
