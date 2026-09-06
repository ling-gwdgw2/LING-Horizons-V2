package me.ling.horizons2.neoforge.gui;

import com.mojang.blaze3d.platform.InputConstants;
import me.ling.horizons2.LingHorizons2;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Keybindings for LING Horizons 2.0.
 * Default key: F8 to open settings menu in-game.
 */
public class LingKeyBindings {
    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.ling_horizons2.config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.ling_horizons2"
    );

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY);
    }
}
