#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec3 Position;
layout(location = 0) out vec3 Ray;
invariant gl_Position;
layout(std140) uniform SkyParameters {
    mat4 Transform;
    vec4 SkyAngle;
    vec4 EyeAnimationCloud;
    ivec4 Options;
};
void main() {
    Ray = Position;
    gl_Position = Transform * vec4(Position, 1.0);
}
