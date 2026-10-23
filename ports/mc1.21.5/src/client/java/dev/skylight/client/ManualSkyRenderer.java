package dev.skylight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;

import dev.skylight.settings.SkyWindowSettings;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ManualSkyRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("skylight-presets");
    private static final Map<SkyWindowSettings, FloatArrayList> GROUPS = new LinkedHashMap<>();
    private static final Identifier[] TEXTURES = {
            Identifier.ofVanilla("textures/environment/sun.png"),
            Identifier.ofVanilla("textures/environment/moon_phases.png"),
            Identifier.ofVanilla("textures/environment/clouds.png"),
            Identifier.ofVanilla("textures/environment/rain.png"),
            Identifier.ofVanilla("textures/environment/snow.png")
    };
    private static final String[] SAMPLERS = {"Sun", "Moon", "Clouds", "Rain", "Snow"};
    private static final Matrix4f TRANSFORM = new Matrix4f();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final String VERTEX = """
            #version 150
            in vec3 Position;
            uniform mat4 Transform;
            out vec3 Ray;
            invariant gl_Position;
            void main() {
                Ray = Position;
                gl_Position = Transform * vec4(Position, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 150
            in vec3 Ray;
            out vec4 FragColor;
            uniform sampler2D Sun;
            uniform sampler2D Moon;
            uniform sampler2D Clouds;
            uniform sampler2D Rain;
            uniform sampler2D Snow;
            uniform vec3 BiomeSky;
            uniform vec2 Eye;
            uniform float Angle;
            uniform float Animation;
            uniform float CloudHeight;
            uniform int Weather;
            uniform int MoonPhase;
            uniform int ShowClouds;
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
                        uv = (uv + vec2(MoonPhase % 4, MoonPhase / 4)) / vec2(4.0, 2.0);
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
            """;
    private static FloatBuffer vertices = BufferUtils.createFloatBuffer(18 * 64);
    private static Vec3d eye = Vec3d.ZERO;
    private static int program;
    private static int vao;
    private static int vbo;
    private static boolean failed;

    private ManualSkyRenderer() {
    }

    public static void begin(Camera camera, Matrix4f view, Matrix4f projection) {
        GROUPS.clear();
        eye = camera.getPos();
        TRANSFORM.set(projection).mul(view);
    }

    public static void add(SkyWindowSettings settings, float x, float y, float z) {
        if (settings.followWorld() || failed) {
            return;
        }
        FloatArrayList group = GROUPS.computeIfAbsent(settings, ignored -> new FloatArrayList());
        group.addElements(group.size(), new float[]{
                x, y, z, x + 1, y, z, x + 1, y, z + 1,
                x, y, z, x + 1, y, z + 1, x, y, z + 1
        });
    }

    public static void render(float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (failed || GROUPS.isEmpty() || client.world == null || !SkyWindowRenderer.prepareManualDepth()) {
            return;
        }
        try {
            int[] textureIds = new int[TEXTURES.length];
            for (int i = 0; i < TEXTURES.length; i++) {
                textureIds[i] = ((GlTexture) client.getTextureManager().getTexture(TEXTURES[i]).getGlTexture()).getGlId();
            }
            try (GlState ignored = new GlState()) {
                initialize();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ((GlTexture) client.getFramebuffer().getColorAttachment()).getOrCreateFramebuffer(
                        ((GlBackend) RenderSystem.getDevice()).getFramebufferManager(), client.getFramebuffer().getDepthAttachment()));
                GL11.glViewport(0, 0, client.getFramebuffer().textureWidth, client.getFramebuffer().textureHeight);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_EQUAL);
                GL11.glDepthMask(false);
                GL11.glDepthRange(0, 1);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
                GL11.glColorMask(true, true, true, true);
                GL20.glUseProgram(program);
                GL30.glBindVertexArray(vao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                MATRIX.clear();
                TRANSFORM.get(MATRIX);
                GL20.glUniformMatrix4fv(uniform("Transform"), false, MATRIX);
                int color = client.world.getBiome(BlockPos.ofFloored(eye)).value().getSkyColor();
                GL20.glUniform3f(uniform("BiomeSky"), (color >> 16 & 255) / 255.0f,
                        (color >> 8 & 255) / 255.0f, (color & 255) / 255.0f);
                GL20.glUniform2f(uniform("Eye"), (float) (eye.x % 3072.0), (float) (eye.z % 3072.0));
                GL20.glUniform1f(uniform("Animation"), client.world.getTime() % 240000L + tickDelta);
                GL20.glUniform1f(uniform("CloudHeight"), Math.max(32.0f,
                        client.world.getDimensionEffects().getCloudsHeight() - (float) eye.y));
                GL20.glUniform1i(uniform("MoonPhase"), client.world.getMoonPhase());
                GL20.glUniform1i(uniform("ShowClouds"), client.options.getCloudRenderModeValue() == CloudRenderMode.OFF ? 0 : 1);
                for (int i = 0; i < textureIds.length; i++) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureIds[i]);
                }
                for (Map.Entry<SkyWindowSettings, FloatArrayList> group : GROUPS.entrySet()) {
                    SkyWindowSettings settings = group.getKey();
                    FloatArrayList positions = group.getValue();
                    if (vertices.capacity() < positions.size()) {
                        vertices = BufferUtils.createFloatBuffer(positions.size());
                    }
                    vertices.clear();
                    vertices.put(positions.elements(), 0, positions.size()).flip();
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);
                    GL20.glUniform1f(uniform("Angle"), client.world.getDimension().getSkyAngle(settings.time()) * (float) (Math.PI * 2.0));
                    GL20.glUniform1i(uniform("Weather"), settings.weather().ordinal());
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, positions.size() / 3);
                }
            }
        } catch (RuntimeException exception) {
            failed = true;
            LOGGER.error("Manual sky rendering was disabled after a graphics failure", exception);
        } finally {
            SkyWindowRenderer.finishManualDepth();
            GROUPS.clear();
        }
    }

    private static int uniform(String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    private static void initialize() {
        if (program != 0) {
            return;
        }
        program = SkyWindowRenderer.program(VERTEX, FRAGMENT);
        GL20.glUseProgram(program);
        for (int i = 0; i < SAMPLERS.length; i++) {
            GL20.glUniform1i(uniform(SAMPLERS[i]), i);
        }
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
    }

    public static void reset() {
        GROUPS.clear();
    }

    public static void close() {
        reset();
        GL30.glDeleteVertexArrays(vao);
        GL15.glDeleteBuffers(vbo);
        if (program != 0) {
            GL20.glDeleteProgram(program);
        }
        program = 0;
        vao = 0;
        vbo = 0;
    }
}
