package me.ling.horizons2.neoforge.gui;

import me.ling.horizons2.engine.storage.StorageEngineFactory.EngineType;
import me.ling.horizons2.neoforge.config.LingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Modern In-Game GUI Configuration Screen for LING Horizons 2.0.
 */
public class LingConfigScreen extends Screen {
    private final Screen parent;

    public LingConfigScreen(Screen parent) {
        super(Component.literal("LING Horizons 2.0 Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 48;
        int btnWidth = 220;
        int btnHeight = 20;
        int spacing = 26;

        // 1. Toggle LOD Engine Enabled
        addRenderableWidget(Button.builder(
            getEnabledText(),
            btn -> {
                boolean next = !LingConfig.CLIENT.enabled.get();
                LingConfig.CLIENT.enabled.set(next);
                btn.setMessage(getEnabledText());
            }
        ).bounds(centerX - btnWidth / 2, startY, btnWidth, btnHeight).build());

        // 2. Slider: Render Distance
        addRenderableWidget(new RenderDistanceSlider(centerX - btnWidth / 2, startY + spacing, btnWidth, btnHeight));

        // 3. Slider: Planetary Curvature
        addRenderableWidget(new CurvatureSlider(centerX - btnWidth / 2, startY + spacing * 2, btnWidth, btnHeight));

        // 4. Toggle Storage Engine (SQLite / RocksDB)
        addRenderableWidget(Button.builder(
            getStorageEngineText(),
            btn -> {
                EngineType current = LingConfig.CLIENT.storageEngine.get();
                EngineType next = (current == EngineType.SQLITE) ? EngineType.ROCKSDB : EngineType.SQLITE;
                LingConfig.CLIENT.storageEngine.set(next);
                btn.setMessage(getStorageEngineText());
            }
        ).bounds(centerX - btnWidth / 2, startY + spacing * 3, btnWidth, btnHeight).build());

        // 5. Toggle Hi-Z Occlusion Culling
        addRenderableWidget(Button.builder(
            getHiZText(),
            btn -> {
                boolean next = !LingConfig.CLIENT.enableHiZCulling.get();
                LingConfig.CLIENT.enableHiZCulling.set(next);
                btn.setMessage(getHiZText());
            }
        ).bounds(centerX - btnWidth / 2, startY + spacing * 4, btnWidth, btnHeight).build());

        // 6. Done Button
        addRenderableWidget(Button.builder(
            CommonComponents.GUI_DONE,
            btn -> {
                LingConfig.CLIENT_SPEC.save();
                if (minecraft != null) {
                    minecraft.setScreen(parent);
                }
            }
        ).bounds(centerX - btnWidth / 2, startY + spacing * 5 + 8, btnWidth, btnHeight).build());
    }

    private Component getEnabledText() {
        boolean enabled = LingConfig.CLIENT.enabled.get();
        return Component.literal("LOD Engine: " + (enabled ? "§aENABLED" : "§cDISABLED"));
    }

    private Component getStorageEngineText() {
        return Component.literal("Storage Backend: §e" + LingConfig.CLIENT.storageEngine.get().name());
    }

    private Component getHiZText() {
        boolean hiz = LingConfig.CLIENT.enableHiZCulling.get();
        return Component.literal("GPU Hi-Z Culling: " + (hiz ? "§aENABLED" : "§cDISABLED"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("§7Independent GPU-Driven Multi-Draw Indirect Voxel Engine"), centerX, 28, 0x88AAFF);
    }

    @Override
    public void onClose() {
        LingConfig.CLIENT_SPEC.save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    // Slider for Render Distance: 16 to 256 chunks
    private static class RenderDistanceSlider extends AbstractSliderButton {
        public RenderDistanceSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (LingConfig.CLIENT.renderDistance.get() - 16) / 240.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int chunks = 16 + (int) Math.round(this.value * 240.0);
            setMessage(Component.literal("LOD Render Distance: §b" + chunks + " Chunks (" + (chunks * 16) + "m)"));
        }

        @Override
        protected void applyValue() {
            int chunks = 16 + (int) Math.round(this.value * 240.0);
            LingConfig.CLIENT.renderDistance.set(chunks);
        }
    }

    // Slider for Planetary Curvature: 0 to 100,000 blocks
    private static class CurvatureSlider extends AbstractSliderButton {
        public CurvatureSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), LingConfig.CLIENT.curvatureRadius.get() / 100000.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double radius = this.value * 100000.0;
            if (radius < 200.0) {
                setMessage(Component.literal("Planetary Curvature: §7Flat Horizon"));
            } else {
                setMessage(Component.literal(String.format("Planetary Curvature: §6R = %.0f blocks", radius)));
            }
        }

        @Override
        protected void applyValue() {
            double radius = this.value * 100000.0;
            if (radius < 200.0) radius = 0.0;
            LingConfig.CLIENT.curvatureRadius.set(radius);
        }
    }
}
