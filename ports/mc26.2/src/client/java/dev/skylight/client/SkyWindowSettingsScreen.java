package dev.skylight.client;

import dev.skylight.network.ApplySkySettingsPayload;
import dev.skylight.network.SkySettingsResultPayload;
import dev.skylight.settings.SkyWindowSettings;
import dev.skylight.settings.SkyWindowSettings.Weather;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

public final class SkyWindowSettingsScreen extends Screen {
    private final BlockPos pos;
    private boolean followWorld;
    private int time;
    private Weather weather;
    private CycleButton<Boolean> mode;
    private AbstractSliderButton timeSlider;
    private CycleButton<Weather> weatherButton;
    private Button applyButton;
    private Component status = Component.empty();
    private boolean waiting;
    private int waitTicks;

    public SkyWindowSettingsScreen(BlockPos pos, SkyWindowSettings settings) {
        super(Component.translatable("screen.sky_window.title"));
        this.pos = pos.immutable();
        followWorld = settings.followWorld();
        time = settings.time();
        weather = settings.weather();
        var world = net.minecraft.client.Minecraft.getInstance().level;
        if (followWorld && world != null) {
            time = (int) Math.floorMod(world.getOverworldClockTime(), 24000L);
            weather = world.isThundering() ? Weather.THUNDER : world.isRaining() ? Weather.RAIN : Weather.CLEAR;
        }
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(320, width - 24);
        int x = (width - panelWidth) / 2;
        int y = (height - 260) / 2;
        mode = addRenderableWidget(CycleButton.onOffBuilder(followWorld)
                .create(x + 16, y + 56, panelWidth - 32, 20, Component.translatable("screen.sky_window.follow_world"),
                        (button, value) -> {
                            followWorld = value;
                            updateEnabled();
                        }));
        mode.setTooltip(Tooltip.create(Component.translatable("screen.sky_window.mode_hint")));
        timeSlider = addRenderableWidget(new AbstractSliderButton(x + 16, y + 84, panelWidth - 32, 20,
                timeLabel(), time / 23999.0) {
            @Override
            protected void updateMessage() {
                setMessage(timeLabel());
            }

            @Override
            protected void applyValue() {
                time = (int) Math.round(value * 23999);
            }
        });
        weatherButton = addRenderableWidget(CycleButton.<Weather>builder(value ->
                        Component.translatable("weather.sky_window." + value.name().toLowerCase(Locale.ROOT)), weather)
                .withValues(Weather.values())
                .create(x + 16, y + 112, panelWidth - 32, 20, Component.translatable("screen.sky_window.weather"),
                        (button, value) -> weather = value));
        applyButton = addRenderableWidget(Button.builder(Component.translatable("screen.sky_window.apply"),
                        button -> applySettings()).bounds(x + 16, y + 222, (panelWidth - 40) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(x + 24 + (panelWidth - 40) / 2, y + 222, (panelWidth - 40) / 2, 20).build());
        updateEnabled();
    }

    private Component timeLabel() {
        int minutes = (time * 60 / 1000 + 360) % 1440;
        return Component.translatable("screen.sky_window.time", String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60));
    }

    private void updateEnabled() {
        mode.active = !waiting;
        timeSlider.active = !waiting && !followWorld;
        weatherButton.active = !waiting && !followWorld;
        applyButton.active = !waiting;
    }

    public SkyWindowSettings getDraftSettings() {
        return new SkyWindowSettings(followWorld, time, weather);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public void applySettings() {
        if (waiting || minecraft == null || minecraft.level == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(ApplySkySettingsPayload.ID)) {
            status = Component.translatable("message.sky_window.server_unavailable");
            return;
        }
        waiting = true;
        waitTicks = 0;
        status = Component.translatable("screen.sky_window.applying");
        updateEnabled();
        ClientPlayNetworking.send(new ApplySkySettingsPayload(pos, getDraftSettings()));
    }

    public void receiveResult(SkySettingsResultPayload result) {
        if (!waiting) {
            return;
        }
        waiting = false;
        if (result.success()) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.translatable(result.messageKey(), result.count()));
            }
            onClose();
        } else {
            status = Component.translatable(result.messageKey(), result.count());
            updateEnabled();
        }
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            onClose();
        } else if (waiting && ++waitTicks >= 200) {
            waiting = false;
            status = Component.translatable("message.sky_window.timeout");
            updateEnabled();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int panelWidth = Math.min(320, width - 24);
        int x = (width - panelWidth) / 2;
        int y = (height - 260) / 2;
        context.fill(0, 0, width, height, 0x99070d19);
        context.fill(x, y, x + panelWidth, y + 260, 0xf0182639);
        context.fill(x, y, x + panelWidth, y + 2, 0xff83c9ff);
        context.centeredText(font, title, width / 2, y + 17, 0xffe4f3ff);
        context.centeredText(font, Component.translatable("screen.sky_window.scope"), width / 2, y + 34, 0xffa5bcd2);
        context.textWithWordWrap(font, Component.translatable("screen.sky_window.appearance_hint"), x + 16, y + 144, panelWidth - 32, 0xffb6cddd);
        context.textWithWordWrap(font, status, x + 16, y + 184, panelWidth - 32, 0xffffd18c);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
