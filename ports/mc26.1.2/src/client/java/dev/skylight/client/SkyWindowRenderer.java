package dev.skylight.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.FilterMode;
import dev.skylight.SkylightMod;
import dev.skylight.block.SkyWindowBlockEntity;
import dev.skylight.settings.SkyWindowSettings;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.ByteBuffer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalDouble;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

public final class SkyWindowRenderer {
    private static final LongOpenHashSet WINDOWS = new LongOpenHashSet();
    private static final RenderPipeline LIVE = pipeline("live_sky", "SkyCapture");
    private static final RenderPipeline MANUAL = pipeline("manual_sky", "Sun", "Moon", "Clouds", "Rain", "Snow");
    private static ClientLevel trackedLevel;
    private static volatile List<Window> extracted = List.of();
    private static final List<Batch> batches = new ArrayList<>();
    private static final Map<String, AbstractTexture> frameTextures = new LinkedHashMap<>();
    private static final Matrix4f worldProjection = new Matrix4f();
    private static TextureTarget capture;
    private static boolean captured;
    private static boolean capturingClouds;
    private static String moon = "full_moon";
    private static final Method IRIS_ACTIVE = irisMethod();
    private static boolean irisFrame;
    private static volatile int manualSkyColor = EnvironmentAttributes.SKY_COLOR.defaultValue();

    private SkyWindowRenderer() {
    }

    private static RenderPipeline pipeline(String name, String... textures) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("sky_window", "pipeline/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath("sky_window", "core/sky_window"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("sky_window", "core/" + name))
                .withUniform("SkyParameters", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION, com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
                .withColorTargetState(ColorTargetState.DEFAULT).withCull(false);
        for (String texture : textures) {
            builder.withSampler(texture);
        }
        return builder.build();
    }

    public static synchronized void add(ClientLevel level, BlockPos pos) {
        if (trackedLevel != level) {
            reset();
            trackedLevel = level;
        }
        WINDOWS.add(pos.asLong());
    }

    public static synchronized void remove(ClientLevel level, BlockPos pos) {
        if (trackedLevel == level) {
            WINDOWS.remove(pos.asLong());
        }
    }

    public static synchronized void extract(Minecraft client) {
        if (client.level == null || trackedLevel != client.level) {
            extracted = List.of();
            return;
        }
        if (client.getCameraEntity() != null) {
            int dimensionColor = client.level.dimensionType().attributes()
                    .applyModifier(EnvironmentAttributes.SKY_COLOR, EnvironmentAttributes.SKY_COLOR.defaultValue());
            manualSkyColor = client.level.getBiome(client.getCameraEntity().blockPosition()).value()
                    .getAttributes().applyModifier(EnvironmentAttributes.SKY_COLOR, dimensionColor);
        }
        List<Window> result = new ArrayList<>(WINDOWS.size());
        for (long packed : WINDOWS) {
            BlockPos pos = BlockPos.of(packed);
            if (client.level.getBlockEntity(pos) instanceof SkyWindowBlockEntity entity
                    && client.level.getBlockState(pos).is(SkylightMod.SKY_WINDOW)) {
                result.add(new Window(pos, entity.getSettings()));
            }
        }
        extracted = List.copyOf(result);
    }

    public static void setWorldProjection(Matrix4f projection) {
        worldProjection.set(projection);
    }

    public static void begin(LevelRenderState level, OptionsRenderState options) {
        batches.forEach(Batch::close);
        batches.clear();
        frameTextures.clear();
        captured = false;
        irisFrame = false;
        CameraRenderState camera = level.cameraRenderState;
        if (extracted.isEmpty() || level.skyRenderState.skybox != DimensionType.Skybox.OVERWORLD
                || camera.fogType == FogType.LAVA || camera.fogType == FogType.POWDER_SNOW
                || camera.entityRenderState.doesMobEffectBlockSky) {
            return;
        }
        Vec3 eye = camera.pos;
        double limit = (options.renderDistance + 1) * 16.0;
        Map<SkyWindowSettings, List<BlockPos>> groups = new LinkedHashMap<>();
        for (Window window : extracted) {
            BlockPos pos = window.pos();
            if (eye.y >= pos.getY() - 0.002 || Math.abs(pos.getX() + 0.5 - eye.x) > limit
                    || Math.abs(pos.getZ() + 0.5 - eye.z) > limit) {
                continue;
            }
            groups.computeIfAbsent(window.settings(), ignored -> new ArrayList<>()).add(pos);
        }
        if (groups.isEmpty()) {
            return;
        }
        moon = level.skyRenderState.moonPhase.getSerializedName();
        if (groups.keySet().stream().anyMatch(settings -> !settings.followWorld())) {
            preload("Sun", "celestial/sun");
            preload("Moon", "celestial/moon/" + moon);
            preload("Clouds", "clouds");
            preload("Rain", "rain");
            preload("Snow", "snow");
        }
        Matrix4f transform = new Matrix4f(worldProjection).mul(camera.viewRotationMatrix);
        irisFrame = isIrisActive();
        if (irisFrame) {
            List<BlockPos> live = new ArrayList<>();
            List<BlockPos> manual = new ArrayList<>();
            groups.forEach((settings, positions) -> (settings.followWorld() ? live : manual).addAll(positions));
            IrisSkyMask.prepare(transform, live, manual, eye);
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (var entry : groups.entrySet()) {
            ByteBuffer vertices = MemoryUtil.memAlloc(entry.getValue().size() * 72);
            ByteBuffer uniforms = MemoryUtil.memAlloc(112);
            try {
                for (BlockPos pos : entry.getValue()) {
                    float x = (float) (pos.getX() - eye.x);
                    float y = (float) (pos.getY() - eye.y - 0.002);
                    float z = (float) (pos.getZ() - eye.z);
                    vertex(vertices, x, y, z);
                    vertex(vertices, x + 1, y, z);
                    vertex(vertices, x + 1, y, z + 1);
                    vertex(vertices, x, y, z);
                    vertex(vertices, x + 1, y, z + 1);
                    vertex(vertices, x, y, z + 1);
                }
                vertices.flip();
                transform.get(0, uniforms);
                uniforms.position(64);
                int skyColor = manualSkyColor;
                uniforms.putFloat(((skyColor >> 16) & 255) / 255.0f)
                        .putFloat(((skyColor >> 8) & 255) / 255.0f).putFloat((skyColor & 255) / 255.0f)
                        .putFloat(skyAngle(entry.getKey().time()));
                uniforms.putFloat((float) eye.x).putFloat((float) eye.z)
                        .putFloat((float) ((level.gameTime % 1000000L) + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)))
                        .putFloat(Math.max(32.0f, level.cloudHeight - (float) eye.y));
                uniforms.putInt(entry.getKey().weather().ordinal()).putInt(options.cloudStatus == CloudStatus.OFF ? 0 : 1)
                        .putInt(0).putInt(0).flip();
                GpuBuffer vertexData = RenderSystem.getDevice().createBuffer(() -> "Sky Window vertices", GpuBuffer.USAGE_VERTEX, vertices);
                GpuBuffer uniformData = RenderSystem.getDevice().createBuffer(() -> "Sky Window parameters", GpuBuffer.USAGE_UNIFORM, uniforms);
                batches.add(new Batch(entry.getKey().followWorld(), vertexData, uniformData, entry.getValue().size() * 6));
            } finally {
                MemoryUtil.memFree(vertices);
                MemoryUtil.memFree(uniforms);
            }
        }
    }

    public static void captureSky(RenderTarget source) {
        if (irisFrame) {
            IrisSkyMask.preserve(source);
            return;
        }
        if (batches.stream().noneMatch(Batch::live)) {
            return;
        }
        if (capture == null || capture.width != source.width || capture.height != source.height
                || capture.getColorTexture().getFormat() != source.getColorTexture().getFormat()) {
            if (capture != null) {
                capture.destroyBuffers();
            }
            capture = new TextureTarget("Sky Window sky capture", source.width, source.height,
                    true);
        }
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source.getColorTexture(),
                capture.getColorTexture(), 0, 0, 0, 0, 0, source.width, source.height);
        captured = true;
    }

    public static void captureClouds(CloudRenderer clouds, LevelRenderState level, OptionsRenderState options) {
        if (!captured || options.cloudStatus == CloudStatus.OFF) {
            return;
        }
        capturingClouds = true;
        try {
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(capture.getDepthTexture(), 1.0);
            clouds.render(level.cloudColor, options.cloudStatus, level.cloudHeight, options.cloudRange,
                    level.cameraRenderState.pos, level.gameTime,
                    Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        } finally {
            capturingClouds = false;
        }
    }

    public static com.mojang.blaze3d.textures.GpuTextureView cloudColorView(RenderTarget target) {
        return (capturingClouds ? capture : target).getColorTextureView();
    }

    public static com.mojang.blaze3d.textures.GpuTextureView cloudDepthView(RenderTarget target) {
        return (capturingClouds ? capture : target).getDepthTextureView();
    }

    public static void drawToMainTarget() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Sky Window ceiling", target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            draw(pass);
        }
    }

    public static void draw(RenderPass pass) {
        if (batches.isEmpty() || irisFrame) {
            return;
        }
        drawBatches(pass, false);
    }

    private static void drawBatches(RenderPass pass, boolean manualOnly) {
        for (Batch batch : batches) {
            if (manualOnly && batch.live()) {
                continue;
            }
            if (batch.live() && !captured) {
                continue;
            }
            pass.setPipeline(batch.live() ? LIVE : MANUAL);
            pass.setUniform("SkyParameters", batch.parameters());
            if (batch.live()) {
                pass.bindTexture("SkyCapture", capture.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            } else {
                texture(pass, "Sun");
                texture(pass, "Moon");
                texture(pass, "Clouds");
                texture(pass, "Rain");
                texture(pass, "Snow");
            }
            pass.setVertexBuffer(0, batch.vertices());
            pass.draw(0, batch.count());
        }
    }

    public static boolean isIrisActive() {
        if (IRIS_ACTIVE == null) {
            return false;
        }
        try {
            return (boolean) IRIS_ACTIVE.invoke(null);
        } catch (ReflectiveOperationException failure) {
            return false;
        }
    }

    private static Method irisMethod() {
        try {
            return Class.forName("net.irisshaders.iris.Iris").getMethod("isPackInUseQuick");
        } catch (ReflectiveOperationException failure) {
            return null;
        }
    }

    public static void revealIrisSky() {
        if (irisFrame) {
            IrisSkyMask.reveal();
        }
    }

    public static void protectIrisSky() {
        if (irisFrame) {
            IrisSkyMask.protect();
        }
    }

    public static void drawManualAfterIris() {
        if (!irisFrame || batches.stream().allMatch(Batch::live)) {
            return;
        }
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        IrisSkyMask.protect();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Sky Window manual sky", target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            drawBatches(pass, true);
        } finally {
            IrisSkyMask.reveal();
        }
    }

    private static void preload(String name, String path) {
        frameTextures.put(name, Minecraft.getInstance().getTextureManager()
                .getTexture(Identifier.withDefaultNamespace("textures/environment/" + path + ".png")));
    }

    private static void texture(RenderPass pass, String name) {
        AbstractTexture texture = frameTextures.get(name);
        pass.bindTexture(name, texture.getTextureView(), texture.getSampler());
    }

    private static void vertex(ByteBuffer buffer, float x, float y, float z) {
        buffer.putFloat(x).putFloat(y).putFloat(z);
    }

    private static float skyAngle(int time) {
        double fraction = (time / 24000.0 - 0.25) % 1.0;
        if (fraction < 0) {
            fraction += 1;
        }
        double curved = 0.5 - Math.cos(fraction * Math.PI) / 2.0;
        return (float) ((fraction * 2.0 + curved) / 3.0 * Math.PI * 2.0);
    }

    public static synchronized void reset() {
        WINDOWS.clear();
        extracted = List.of();
        trackedLevel = null;
    }

    public static void close() {
        batches.forEach(Batch::close);
        batches.clear();
        frameTextures.clear();
        captured = false;
        irisFrame = false;
        IrisSkyMask.close();
        if (capture != null) {
            capture.destroyBuffers();
            capture = null;
        }
    }

    private record Window(BlockPos pos, SkyWindowSettings settings) {
    }

    private record Batch(boolean live, GpuBuffer vertices, GpuBuffer parameters, int count) {
        void close() {
            vertices.close();
            parameters.close();
        }
    }
}
