package me.ling.horizons2.neoforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import me.ling.horizons2.neoforge.config.LingConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * In-game Administrative and Debug Commands for LING Horizons 2.0.
 */
public class LingCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ling2")
                .requires(s -> s.hasPermission(0))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("§b[LING Horizons 2.0] §fNext-Gen Ground-Up GPU Voxel Engine is active."), false);
                    return 1;
                })
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        boolean enabled = LingConfig.CLIENT.enabled.get();
                        int rd = LingConfig.CLIENT.renderDistance.get();
                        double curv = LingConfig.CLIENT.curvatureRadius.get();
                        String db = LingConfig.CLIENT.storageEngine.get().name();

                        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                            "§b[LING Horizons 2.0 Status]§r\n" +
                            " - Enabled: %s\n" +
                            " - LOD Distance: %d chunks\n" +
                            " - Curvature: %.1f\n" +
                            " - Storage Engine: %s",
                            enabled ? "§aYes" : "§cNo", rd, curv, db
                        )), false);
                        return 1;
                    })
                )
                .then(Commands.literal("curvature")
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.0, 500000.0))
                        .executes(ctx -> {
                            double radius = DoubleArgumentType.getDouble(ctx, "radius");
                            LingConfig.CLIENT.curvatureRadius.set(radius);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§b[LING Horizons 2.0] §fCurvature radius updated to: §e" + radius
                            ), true);
                            return 1;
                        })
                    )
                )
        );
    }
}
