#version 450

// Quad-based point rendering fallback for platforms without native point primitives
// Each point is rendered as 6 vertices (2 triangles forming a quad)

// Instance attributes (per-instance data from the point cloud)
layout(location = 0) in vec3 instancePosition;
layout(location = 1) in vec3 instanceColor;
layout(location = 2) in float instanceSize;
layout(location = 3) in vec4 instanceExtra;

// Uniform block with MVP matrix
layout(binding = 0) uniform UniformBlock {
    mat4 uModelViewProjection;
};

// Outputs to fragment shader
layout(location = 0) out vec3 vColor;

void main() {
    // Which vertex within this quad (0-5 for the 6 vertices of 2 triangles)
    int vertexInQuad = gl_VertexIndex % 6;
    
    // Quad corner offsets (two triangles)
    vec2 quadOffsets[6] = vec2[6](
        vec2(-1.0, -1.0),
        vec2( 1.0, -1.0),
        vec2(-1.0,  1.0),
        vec2(-1.0,  1.0),
        vec2( 1.0, -1.0),
        vec2( 1.0,  1.0)
    );
    
    // Transform point center to clip space
    vec4 clipPos = uModelViewProjection * vec4(instancePosition, 1.0);
    
    // Get the quad corner offset
    vec2 offset = quadOffsets[vertexInQuad];
    
    // Scale point size in clip space
    float pointSize = max(instanceSize * 0.012, 0.004);
    
    // Apply offset in clip space (multiply by w for perspective-correct sizing)
    vec4 finalPos = clipPos;
    finalPos.x = finalPos.x + offset.x * pointSize * clipPos.w;
    finalPos.y = finalPos.y + offset.y * pointSize * clipPos.w;
    
    gl_Position = finalPos;
    vColor = instanceColor;
}
