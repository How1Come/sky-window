package dev.skylight.client;

import dev.skylight.network.ApplySkySettingsPayload;
import dev.skylight.network.SkySettingsResultPayload;
import dev.skylight.settings.SkyWindowSettings;
import dev.skylight.settings.SkyWindowSettings.Weather;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class SkyWindowSettingsScreen extends Screen {
    private final BlockPos pos;
    private boolean followWorld;
    private int time;
    private Weather weather;
    private CyclingButtonWidget<Boolean> mode;
    private SliderWidget timeSlider;
    private CyclingButtonWidget<Weather> weatherButton;
    private ButtonWidget applyButton;
    private Text status = Text.empty();
    private boolean waiting;
    private int waitTicks;

    public SkyWindowSettingsScreen(BlockPos pos, SkyWindowSettings settings) {
        super(Text.translatable("screen.sky_window.title"));
        this.pos = pos.toImmutable();
        followWorld = settings.followWorld();
        time = settings.time();
        weather = settings.weather();
        var world = net.minecraft.client.MinecraftClient.getInstance().world;
        if (followWorld && world != null) {
            time = (int) Math.floorMod(world.getTimeOfDay(), 24000L);
            weather = world.isThundering() ? Weather.THUNDER : world.isRaining() ? Weather.RAIN : Weather.CLEAR;
        }
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(320, width - 24);
        int x = (width - panelWidth) / 2;
        int y = (height - 260) / 2;
        mode = addDrawableChild(CyclingButtonWidget.onOffBuilder(followWorld)
                .build(x + 16, y + 56, panelWidth - 32, 20, Text.translatable("screen.sky_window.follow_world"),
                        (button, value) -> {
                            followWorld = value;
                            updateEnabled();
                        }));
        mode.setTooltip(Tooltip.of(Text.translatable("screen.sky_window.mode_hint")));
        timeSlider = addDrawableChild(new SliderWidget(x + 16, y + 84, panelWidth - 32, 20,
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
        weatherButton = addDrawableChild(CyclingButtonWidget.<Weather>builder(value ->
                        Text.translatable("weather.sky_window." + value.name().toLowerCase(Locale.ROOT)))
                .values(Weather.values()).initially(weather)
                .build(x + 16, y + 112, panelWidth - 32, 20, Text.translatable("screen.sky_window.weather"),
                        (button, value) -> weather = value));
        applyButton = addDrawableChild(ButtonWidget.builder(Text.translatable("screen.sky_window.apply"),
                        button -> applySettings()).dimensions(x + 16, y + 222, (panelWidth - 40) / 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(x + 24 + (panelWidth - 40) / 2, y + 222, (panelWidth - 40) / 2, 20).build());
        updateEnabled();
    }

    private Text timeLabel() {
        int minutes = (time * 60 / 1000 + 360) % 1440;
        return Text.translatable("screen.sky_window.time", String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60));
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
        if (waiting || client == null || client.world == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(ApplySkySettingsPayload.ID)) {
            status = Text.translatable("message.sky_window.server_unavailable");
            return;
        }
        waiting = true;
        waitTicks = 0;
        status = Text.translatable("screen.sky_window.applying");
        updateEnabled();
        PacketByteBuf buffer = PacketByteBufs.create();
        new ApplySkySettingsPayload(pos, getDraftSettings()).write(buffer);
        ClientPlayNetworking.send(ApplySkySettingsPayload.ID, buffer);
    }

    public void receiveResult(SkySettingsResultPayload result) {
        if (!waiting) {
            return;
        }
        waiting = false;
        if (result.success()) {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.translatable(result.messageKey(), result.count()), true);
            }
            close();
        } else {
            status = Text.translatable(result.messageKey(), result.count());
            updateEnabled();
        }
    }

    @Override
    public void tick() {
        if (client == null || client.world == null || client.player == null) {
            close();
        } else if (waiting && ++waitTicks >= 200) {
            waiting = false;
            status = Text.translatable("message.sky_window.timeout");
            updateEnabled();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelWidth = Math.min(320, width - 24);
        int x = (width - panelWidth) / 2;
        int y = (height - 260) / 2;
        context.fill(0, 0, width, height, 0x99070d19);
        context.fill(x, y, x + panelWidth, y + 260, 0xf0182639);
        context.fill(x, y, x + panelWidth, y + 2, 0xff83c9ff);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, y + 17, 0xffe4f3ff);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.sky_window.scope"), width / 2, y + 34, 0xffa5bcd2);
        context.drawTextWrapped(textRenderer, Text.translatable("screen.sky_window.appearance_hint"), x + 16, y + 144, panelWidth - 32, 0xffb6cddd);
        context.drawTextWrapped(textRenderer, status, x + 16, y + 184, panelWidth - 32, 0xffffd18c);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
