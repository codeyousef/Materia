struct VertexInput {
    @location(0) instancePosition : vec3<f32>,
    @location(1) instanceColor : vec3<f32>,
    @location(2) instanceSize : f32,
    @location(3) instanceExtra : vec4<f32>,
};

struct VertexOutput {
    @builtin(position) position : vec4<f32>,
    @location(0) color : vec3<f32>,
    @location(1) size : f32,
    @location(2) extra : vec4<f32>,
};

@group(0) @binding(0)
var<uniform> uModelViewProjection : mat4x4<f32>;

@vertex
fn main(input : VertexInput) -> VertexOutput {
    var output : VertexOutput;
    output.position = uModelViewProjection * vec4<f32>(input.instancePosition, 1.0);

    let glow = clamp(input.instanceExtra.x, 0.0, 1.0);
    let sizeFactor = clamp(input.instanceSize, 0.0, 10.0);

    output.color = input.instanceColor * (1.0 + glow * 0.3) * clamp(sizeFactor, 0.2, 1.5);
    output.size = input.instanceSize;
    output.extra = input.instanceExtra;
    return output;
}
