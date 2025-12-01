struct VertexOutput {
    @builtin(position) position : vec4<f32>,
    @location(0) uv : vec2<f32>,
};

const FULLSCREEN_POSITIONS : array<vec2<f32>, 3> = array<vec2<f32>, 3>(
    vec2<f32>(-1.0, -3.0),
    vec2<f32>(-1.0, 1.0),
    vec2<f32>(3.0, 1.0),
);

// UVs go 0-1 within the visible part of the fullscreen triangle
const FULLSCREEN_UVS : array<vec2<f32>, 3> = array<vec2<f32>, 3>(
    vec2<f32>(0.0, 2.0),
    vec2<f32>(0.0, 0.0),
    vec2<f32>(2.0, 0.0),
);

@vertex
fn main(@builtin(vertex_index) vertexIndex : u32) -> VertexOutput {
    var output : VertexOutput;
    let pos = FULLSCREEN_POSITIONS[vertexIndex];
    output.position = vec4<f32>(pos, 0.0, 1.0);
    // Scale UVs to 0-1 range (they're designed to be 0-2 and multiplied by 0.5)
    output.uv = FULLSCREEN_UVS[vertexIndex] * 0.5;
    return output;
}
