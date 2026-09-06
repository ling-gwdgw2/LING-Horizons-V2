package me.ling.horizons2;

import me.ling.horizons2.neoforge.command.LingCommands;
import me.ling.horizons2.neoforge.config.LingConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * LING Horizons 2.0
 * Next-Generation Ground-Up Independent GPU-Driven Voxel LOD Rendering Engine.
 * Built for Minecraft 1.21.1 (NeoForge).
 */
@Mod(LingHorizons2.MOD_ID)
public class LingHorizons2 {
    public static final String MOD_ID = "ling_horizons2";
    public static final Logger LOGGER = LogManager.getLogger("LING-Horizons-2");

    public LingHorizons2(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing LING Horizons 2.0 (Ground-Up Independent Voxel LOD Engine)");

        // Register Client Config
        modContainer.registerConfig(ModConfig.Type.CLIENT, LingConfig.CLIENT_SPEC);

        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            modEventBus.addListener(me.ling.horizons2.neoforge.gui.LingKeyBindings::registerKeyMappings);
            modContainer.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class, (container, parent) -> new me.ling.horizons2.neoforge.gui.LingConfigScreen(parent));
        }

        // Register In-Game Commands
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        // Register Client Event Handlers
        NeoForge.EVENT_BUS.register(me.ling.horizons2.neoforge.event.LingClientEvents.class);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        LingCommands.register(event.getDispatcher());
    }
}
