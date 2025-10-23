struct VertexOutput {
    @builtin(position) position : vec4<f32>,
};

@vertex
fn main(@location(0) position : vec3<f32>) -> VertexOutput {
    var output : VertexOutput;
    output.position = vec4<f32>(position, 1.0);
    return output;
}
