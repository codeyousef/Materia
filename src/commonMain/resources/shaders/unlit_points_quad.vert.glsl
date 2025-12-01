#version 450

// Quad-based point rendering fallback for platforms without native point primitives
// Each point is rendered as 6 vertices (2 triangles forming a quad)

// Instance attributes (per-instance data from the point cloud)
// layout(location = 0) in vec3 instancePosition;
// layout(location = 1) in vec3 instanceColor;
// layout(location = 2) in float instanceSize;
// layout(location = 3) in vec4 instanceExtra;

// Uniform block with MVP matrix
layout(binding = 0) uniform UniformBlock {
    mat4 uModelViewProjection;
};

// Outputs to fragment shader
layout(location = 0) out vec3 vColor;
layout(location = 1) out float vSize;
layout(location = 2) out vec4 vExtra;
layout(location = 3) out vec2 vPointCoord;

void main() {
    // Which vertex within this quad (0-5 for the 6 vertices of 2 triangles)
    int vertexInQuad = gl_VertexIndex % 6;
    
    // Quad corner offsets (two triangles: 0-1-2 and 3-4-5)
    vec2 quadOffsets[6] = vec2[6](
        vec2(-1.0, -1.0),  // 0: bottom-left
        vec2( 1.0, -1.0),  // 1: bottom-right  
        vec2( 1.0,  1.0),  // 2: top-right
        vec2(-1.0, -1.0),  // 3: bottom-left (repeat)
        vec2( 1.0,  1.0),  // 4: top-right (repeat)
        vec2(-1.0,  1.0)   // 5: top-left
    );
    
    vec2 cornerOffset = quadOffsets[vertexInQuad];
    
    // DEBUG: Generate position from InstanceIndex
    float x = float(gl_InstanceIndex % 100) * 0.015 - 0.75;
    float y = float(gl_InstanceIndex / 100) * 0.015 - 0.75;
    vec3 debugPos = vec3(x, y, 0.5); // Z = 0.5 to avoid clipping
    
    // Force visible size
    vec2 sizeOffset = cornerOffset * 0.005;
    
    gl_Position = vec4(debugPos.xy + sizeOffset, 0.5, 1.0);
    
    // Pass data to fragment shader
    vPointCoord = cornerOffset;  // For circular point rendering
    vColor = vec3(1.0, 0.0, 1.0); // DEBUG: Force Magenta
    vSize = 1.0;
    vExtra = vec4(0.0, 0.0, 0.0, 1.0);
}
