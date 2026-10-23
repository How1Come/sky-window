package dev.skylight.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlTexture;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class IrisSkyMask {
    private static final Logger LOGGER = LoggerFactory.getLogger("sky-window-iris");
    private static final Matrix4f TRANSFORM = new Matrix4f();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final String MASK_VERTEX = """
            #version 150
            in vec3 Position;
            uniform mat4 Transform;
            invariant gl_Position;
            void main() {
                gl_Position = Transform * vec4(Position, 1.0);
            }
            """;
    private static final String MASK_FRAGMENT = """
            #version 150
            void main() {
            }
            """;
    private static final String RELEASE_VERTEX = """
            #version 150
            void main() {
                vec2 position = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String RELEASE_FRAGMENT = """
            #version 150
            uniform sampler2D SceneDepth;
            uniform sampler2D WindowDepth;
            void main() {
                ivec2 pixel = ivec2(gl_FragCoord.xy);
                float windowDepth = texelFetch(WindowDepth, pixel, 0).r;
                float sceneDepth = texelFetch(SceneDepth, pixel, 0).r;
                if (windowDepth <= 0.0 || sceneDepth != windowDepth) {
                    discard;
                }
                gl_FragDepth = 0.0;
            }
            """;
    private static FloatBuffer vertices = BufferUtils.createFloatBuffer(18 * 64);
    private static int totalCount;
    private static int liveCount;
    private static int maskProgram;
    private static int releaseProgram;
    private static int vao;
    private static int vbo;
    private static int windowDepth;
    private static int sceneDepth;
    private static int windowFramebuffer;
    private static int sceneFramebuffer;
    private static int worldFramebuffer;
    private static int width;
    private static int height;
    private static int depthFormat;
    private static boolean protectedSky;
    private static boolean failed;

    private IrisSkyMask() {
    }

    static void prepare(Matrix4f transform, List<BlockPos> live, List<BlockPos> manual, Vec3 eye) {
        totalCount = (live.size() + manual.size()) * 6;
        liveCount = live.size() * 6;
        protectedSky = false;
        TRANSFORM.set(transform);
        int required = totalCount * 3;
        if (vertices.capacity() < required) {
            vertices = BufferUtils.createFloatBuffer(required);
        }
        vertices.clear();
        append(live, eye);
        append(manual, eye);
        vertices.flip();
    }

    private static void append(List<BlockPos> positions, Vec3 eye) {
        for (BlockPos pos : positions) {
            float x = (float) (pos.getX() - eye.x);
            float y = (float) (pos.getY() - eye.y - 0.002);
            float z = (float) (pos.getZ() - eye.z);
            vertices.put(x).put(y).put(z);
            vertices.put(x + 1).put(y).put(z);
            vertices.put(x + 1).put(y).put(z + 1);
            vertices.put(x).put(y).put(z);
            vertices.put(x + 1).put(y).put(z + 1);
            vertices.put(x).put(y).put(z + 1);
        }
    }

    static void preserve(RenderTarget target) {
        if (failed || totalCount == 0 || !(target.getDepthTexture() instanceof GlTexture texture)) {
            return;
        }
        execute(() -> {
            initialize();
            resize(texture.glId());
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);
            prepareDepthPass();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, windowFramebuffer);
            GL11.glClearDepth(0.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            drawWindows(liveCount);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, worldFramebuffer);
            drawWindows(totalCount);
            protectedSky = liveCount > 0;
        });
    }

    static void reveal() {
        if (failed || !protectedSky || worldFramebuffer == 0) {
            return;
        }
        execute(() -> {
            prepareDepthPass();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, worldFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneFramebuffer);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, worldFramebuffer);
            GL20.glUseProgram(releaseProgram);
            GL30.glBindVertexArray(vao);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneDepth);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, windowDepth);
            GL11.glDepthFunc(GL11.GL_ALWAYS);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            protectedSky = false;
        });
    }

    static void protect() {
        if (failed || protectedSky || totalCount == 0 || liveCount == 0 || worldFramebuffer == 0) {
            return;
        }
        execute(() -> {
            prepareDepthPass();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, worldFramebuffer);
            drawWindows(liveCount);
            protectedSky = true;
        });
    }

    private static void drawWindows(int count) {
        GL20.glUseProgram(maskProgram);
        MATRIX.clear();
        TRANSFORM.get(MATRIX);
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(maskProgram, "Transform"), false, MATRIX);
        GL30.glBindVertexArray(vao);
        GL11.glDepthFunc(GL11.GL_GEQUAL);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
    }

    private static void prepareDepthPass() {
        GL11.glViewport(0, 0, width, height);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthRange(0, 1);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
    }

    private static void initialize() {
        if (maskProgram != 0) {
            return;
        }
        maskProgram = program(MASK_VERTEX, MASK_FRAGMENT);
        releaseProgram = program(RELEASE_VERTEX, RELEASE_FRAGMENT);
        GL20.glUseProgram(releaseProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(releaseProgram, "SceneDepth"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(releaseProgram, "WindowDepth"), 1);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
        worldFramebuffer = GL30.glGenFramebuffers();
    }

    private static int program(String vertexSource, String fragmentSource) {
        int vertex = shader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = 0;
        int result = 0;
        try {
            fragment = shader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
            result = GL20.glCreateProgram();
            GL20.glAttachShader(result, vertex);
            GL20.glAttachShader(result, fragment);
            GL20.glBindAttribLocation(result, 0, "Position");
            GL20.glLinkProgram(result);
            if (GL20.glGetProgrami(result, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(result));
            }
            return result;
        } catch (RuntimeException failure) {
            if (result != 0) {
                GL20.glDeleteProgram(result);
            }
            throw failure;
        } finally {
            GL20.glDeleteShader(vertex);
            if (fragment != 0) {
                GL20.glDeleteShader(fragment);
            }
        }
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }

    private static void resize(int currentDepth) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentDepth);
        int currentWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int currentHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        int currentFormat = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        if (currentWidth <= 0 || currentHeight <= 0) {
            throw new IllegalStateException("The main target has no usable depth texture");
        }
        if (currentWidth != width || currentHeight != height || currentFormat != depthFormat) {
            releaseTextures();
            width = currentWidth;
            height = currentHeight;
            depthFormat = currentFormat;
            windowDepth = depthTexture();
            sceneDepth = depthTexture();
            windowFramebuffer = depthFramebuffer(windowDepth);
            sceneFramebuffer = depthFramebuffer(sceneDepth);
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, worldFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, currentDepth, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        requireComplete();
    }

    private static int depthTexture() {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        boolean stencil = depthFormat == GL30.GL_DEPTH24_STENCIL8 || depthFormat == GL30.GL_DEPTH32F_STENCIL8;
        int format = stencil ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT;
        int type = depthFormat == GL30.GL_DEPTH32F_STENCIL8 ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV
                : stencil ? GL30.GL_UNSIGNED_INT_24_8 : GL11.GL_FLOAT;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, depthFormat, width, height, 0, format, type, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        return texture;
    }

    private static int depthFramebuffer(int texture) {
        int framebuffer = GL30.glGenFramebuffers();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, texture, 0);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            requireComplete();
            return framebuffer;
        } catch (RuntimeException failure) {
            GL30.glDeleteFramebuffers(framebuffer);
            throw failure;
        }
    }

    private static void requireComplete() {
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete sky-window depth framebuffer: " + status);
        }
    }

    private static void execute(Runnable operation) {
        try (GlState ignored = new GlState()) {
            operation.run();
        } catch (RuntimeException failure) {
            failed = true;
            protectedSky = false;
            LOGGER.error("Sky Window shader integration was disabled", failure);
        }
    }

    private static void releaseTextures() {
        GL30.glDeleteFramebuffers(windowFramebuffer);
        GL30.glDeleteFramebuffers(sceneFramebuffer);
        GL11.glDeleteTextures(windowDepth);
        GL11.glDeleteTextures(sceneDepth);
        windowFramebuffer = 0;
        sceneFramebuffer = 0;
        windowDepth = 0;
        sceneDepth = 0;
    }

    static void close() {
        if (worldFramebuffer == 0 && maskProgram == 0) {
            return;
        }
        releaseTextures();
        GL30.glDeleteFramebuffers(worldFramebuffer);
        GL30.glDeleteVertexArrays(vao);
        GL15.glDeleteBuffers(vbo);
        GL20.glDeleteProgram(maskProgram);
        GL20.glDeleteProgram(releaseProgram);
        worldFramebuffer = 0;
        vao = 0;
        vbo = 0;
        maskProgram = 0;
        releaseProgram = 0;
        width = 0;
        height = 0;
        failed = false;
        totalCount = 0;
        liveCount = 0;
        protectedSky = false;
    }
}
