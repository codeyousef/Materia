#version 450

// Instance attributes
layout(location = 0) in vec3 instancePosition;
layout(location = 1) in vec3 instanceColor;
layout(location = 2) in float instanceSize;
layout(location = 3) in vec4 instanceExtra;

// Uniform
layout(binding = 0) uniform UniformBlock {
    mat4 uModelViewProjection;
};

// Outputs
layout(location = 0) out vec3 vColor;
layout(location = 1) out float vSize;
layout(location = 2) out vec4 vExtra;

void main() {
    gl_Position = uModelViewProjection * vec4(instancePosition, 1.0);
    
    // Set point size - REQUIRED for Vulkan point primitives to be visible
    // Scale up to be visible, minimum 2.0 pixels
    gl_PointSize = max(instanceSize * 8.0, 2.0);
    
    float glow = clamp(instanceExtra.x, 0.0, 1.0);
    float sizeFactor = clamp(instanceSize, 0.0, 10.0);
    
    vColor = instanceColor * (1.0 + glow * 0.3) * clamp(sizeFactor, 0.2, 1.5);
    vSize = instanceSize;
    vExtra = instanceExtra;
}
