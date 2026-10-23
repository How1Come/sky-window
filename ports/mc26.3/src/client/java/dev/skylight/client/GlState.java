package dev.skylight.client;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

final class GlState implements AutoCloseable {
    private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean polygonOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final float[] clearColor = new float[4];
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final int[] viewport = new int[4];
    private final int[] polygonMode = new int[2];
    private final double[] depthRange = new double[2];
    private final int[] textures = new int[5];
    private final int[] samplerBindings = new int[5];
    private final ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
    private final boolean samplers = GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_sampler_objects;

    GlState() {
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, polygonMode);
        GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, depthRange);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        for (int unit = 0; unit < textures.length; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if (samplers) {
                samplerBindings[unit] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit);
                GL33.glBindSampler(unit, 0);
            }
        }
        GL13.glActiveTexture(activeTexture);
    }

    @Override
    public void close() {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        for (int unit = 0; unit < textures.length; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[unit]);
            if (samplers) {
                GL33.glBindSampler(unit, samplerBindings[unit]);
            }
        }
        GL13.glActiveTexture(activeTexture);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL11.glDepthFunc(depthFunction);
        GL11.glDepthMask(depthWrite);
        GL11.glDepthRange(depthRange[0], depthRange[1]);
        GL11.glClearDepth(clearDepth);
        GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygonMode[0]);
        GL11.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                colorMask.get(2) != 0, colorMask.get(3) != 0);
        enabled(GL11.GL_DEPTH_TEST, depthTest);
        enabled(GL11.GL_SCISSOR_TEST, scissor);
        enabled(GL11.GL_STENCIL_TEST, stencil);
        enabled(GL11.GL_CULL_FACE, cull);
        enabled(GL11.GL_POLYGON_OFFSET_FILL, polygonOffset);
        enabled(GL11.GL_BLEND, blend);
    }

    private static void enabled(int capability, boolean value) {
        if (value) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }
}
