package io.github.chakyl.cozycafe.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.slarrties.privit.common.region.Color;
import dev.slarrties.privit.common.region.RegionGroups;
import dev.slarrties.privit.server.region.Region;
import dev.slarrties.privit.server.world.WorldRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.UUID;

/**
 * Development-only helpers (never registered in a normal install): /cozydebug claim <radius>
 * creates a temporary Privit claim around the player, owned by them.
 */
public final class DevCommands {
    private DevCommands() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("cozydebug").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("claim").then(Commands.argument("radius", IntegerArgumentType.integer(1, 64)).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            int r = IntegerArgumentType.getInteger(context, "radius");
                            BlockPos p = player.blockPosition();
                            BoundingBox bounds = new BoundingBox(p.getX() - r, p.getY() - 8, p.getZ() - r, p.getX() + r, p.getY() + 16, p.getZ() + r);
                            Region region = new Region(UUID.randomUUID(), "debug cafe", bounds, p, Color.GOLD, RegionGroups.create(player.getUUID()));
                            WorldRegistry.get(player.serverLevel()).getRegionManager().loadRegion(region);
                            context.getSource().sendSuccess(() -> Component.literal("Debug claim " + bounds), false);
                            return 1;
                        })))));
    }
}
