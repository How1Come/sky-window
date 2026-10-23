#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec3 Ray;
layout(location = 0) out vec4 FragColor;
uniform sampler2D SkyCapture;
void main() {
    FragColor = texelFetch(SkyCapture, ivec2(gl_FragCoord.xy), 0);
}
