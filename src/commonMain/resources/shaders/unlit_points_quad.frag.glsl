#version 450

// Inputs from vertex shader
layout(location = 0) in vec3 vColor;
layout(location = 1) in float vSize;
layout(location = 2) in vec4 vExtra;
layout(location = 3) in vec2 vPointCoord;

// Output
layout(location = 0) out vec4 outColor;

void main() {
    // DEBUG: Simple output
    outColor = vec4(vColor, 1.0);
}
