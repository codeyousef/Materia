#version 450

// Input from vertex shader
layout(location = 0) in vec3 vColor;

// Output
layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(vColor, 1.0);
}
