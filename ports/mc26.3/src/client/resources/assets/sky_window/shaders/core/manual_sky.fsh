#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec3 Ray;
layout(location = 0) out vec4 FragColor;
uniform sampler2D Sun;
uniform sampler2D Moon;
uniform sampler2D Clouds;
uniform sampler2D Rain;
uniform sampler2D Snow;
layout(std140) uniform SkyParameters {
    mat4 Transform;
    vec4 SkyAngle;
    vec4 EyeAnimationCloud;
    ivec4 Options;
};
#define BiomeSky SkyAngle.xyz
#define Angle SkyAngle.w
#define Eye EyeAnimationCloud.xy
#define Animation EyeAnimationCloud.z
#define CloudHeight EyeAnimationCloud.w
#define Weather Options.x
#define ShowClouds Options.y
const float PI = 3.141592653589793;
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}
void main() {
    vec3 ray = normalize(Ray);
    float daylight = clamp(cos(Angle) * 2.0 + 0.5, 0.0, 1.0);
    float wet = Weather == 0 ? 0.0 : 1.0;
    float storm = Weather == 2 ? 1.0 : 0.0;
    vec3 color = BiomeSky * daylight;
    float grey = dot(color, vec3(0.3, 0.59, 0.11)) * 0.6;
    color = mix(color, vec3(grey), wet * 0.75);
    color = mix(color, vec3(dot(color, vec3(0.3, 0.59, 0.11)) * 0.2), storm * 0.75);
    float horizon = pow(1.0 - max(ray.y, 0.0), 5.0);
    color = mix(color, mix(vec3(0.015, 0.019, 0.035), vec3(0.72, 0.78, 0.85), daylight), horizon * 0.35 * (1.0 - wet));
    float dusk = (1.0 - abs(daylight * 2.0 - 1.0)) * (1.0 - wet);
    color += vec3(0.65, 0.19, 0.055) * horizon * dusk;
    vec3 celestial = vec3(ray.z, cos(Angle) * ray.y - sin(Angle) * ray.x,
            -sin(Angle) * ray.y - cos(Angle) * ray.x);
    vec2 starPlane = vec2(atan(celestial.x, celestial.z) / (2.0 * PI) + 0.5,
            asin(clamp(celestial.y, -1.0, 1.0)) / PI + 0.5) * vec2(240.0, 120.0);
    vec2 starCell = floor(starPlane);
    float starSeed = hash(starCell);
    vec2 starOffset = vec2(hash(starCell + 3.0), hash(starCell + 8.0));
    float starDistance = length(fract(starPlane) - starOffset);
    float starEdge = max(length(fwidth(starPlane)), 0.03);
    float star = (1.0 - smoothstep(0.018, 0.018 + starEdge, starDistance)) * step(0.985, starSeed);
    color += vec3(star * (1.0 - daylight) * (1.0 - wet) * 0.7);
    if (celestial.y > 0.0) {
        vec2 uv = celestial.xz / celestial.y / 0.6 + 0.5;
        if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) {
            vec4 sun = texture(Sun, uv);
            color += sun.rgb * sun.a * (1.0 - wet);
        }
    }
    if (celestial.y < 0.0) {
        vec2 uv = vec2(-celestial.x, celestial.z) / -celestial.y / 0.4 + 0.5;
        if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) {

            vec4 moon = texture(Moon, uv);
            color += moon.rgb * moon.a * (1.0 - wet);
        }
    }
    vec2 cloudPoint = Eye + ray.xz * CloudHeight / max(ray.y, 0.015);
    vec2 cloudUv = (cloudPoint + vec2(Animation * 0.03, 4.0)) / 3072.0;
    if (ShowClouds != 0) {
        vec4 cloud = texture(Clouds, fract(cloudUv));
        vec3 cloudColor = mix(vec3(0.09, 0.11, 0.16), vec3(0.86), daylight);
        cloudColor *= mix(1.0, 0.53, wet) * mix(1.0, 0.48, storm);
        color = mix(color, cloud.rgb * cloudColor, cloud.a * 0.86);
    }
    if (wet > 0.0) {
        vec2 field = ray.xz / max(ray.y + 0.35, 0.35);
        float snow = Weather == 3 ? 1.0 : 0.0;
        for (int layer = 0; layer < 3; layer++) {
            float scale = 3.0 + float(layer) * 2.7;
            vec2 uv = field * scale + vec2(float(layer) * 0.371);
            uv.y += Animation * mix(0.028, 0.0028, snow) * (1.0 + float(layer) * 0.24);
            uv.x += sin(Animation * 0.009 + float(layer)) * 0.045 * snow;
            vec4 precipitation = snow > 0.5 ? texture(Snow, fract(uv)) : texture(Rain, fract(uv));
            float opacity = precipitation.a * mix(0.16, 0.4, snow);
            vec3 precipitationColor = precipitation.rgb * mix(vec3(0.34, 0.4, 0.53), vec3(0.88), daylight);
            color = mix(color, precipitationColor, opacity);
        }
    }
    float lightning = storm * (1.0 - smoothstep(0.0, 2.0, mod(Animation, 173.0)));
    color = mix(color, vec3(0.65, 0.68, 0.8), lightning * 0.7);
    FragColor = vec4(color, 1.0);
}
