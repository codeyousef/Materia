#version 450

// Inputs from vertex shader
layout(location = 0) in vec3 vColor;
layout(location = 1) in float vSize;
layout(location = 2) in vec4 vExtra;

// Output
layout(location = 0) out vec4 outColor;

void main() {
    // Use gl_PointCoord for soft circular points
    vec2 pc = gl_PointCoord * 2.0 - 1.0;  // -1 to 1
    float dist = dot(pc, pc);
    
    // Soft edge falloff
    float alpha = 1.0 - smoothstep(0.5, 1.0, dist);
    
    if (alpha < 0.01) {
        discard;
    }
    
    // Add glow based on vExtra.x
    float glow = clamp(vExtra.x, 0.0, 1.0);
    vec3 finalColor = vColor * (1.0 + glow * 0.5);
    
    outColor = vec4(finalColor, alpha);
}
