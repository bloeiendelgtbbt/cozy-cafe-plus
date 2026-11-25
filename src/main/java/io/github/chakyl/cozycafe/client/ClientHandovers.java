package io.github.chakyl.cozycafe.client;

import io.github.chakyl.cozycafe.data.CafeHandovers;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** This player's own bills not handed in yet, per cafe (sent by the server whenever they change). */
@Environment(EnvType.CLIENT)
public final class ClientHandovers {
    private static List<CafeHandovers.Summary> bills = List.of();

    private ClientHandovers() {
    }

    public static void receive(List<CafeHandovers.Summary> received, boolean open) {
        bills = List.copyOf(received);
        Minecraft minecraft = Minecraft.getInstance();
        if (open) {
            minecraft.setScreen(new io.github.chakyl.cozycafe.gui.MyBillsScreen());
        }
    }

    public static List<CafeHandovers.Summary> all() {
        return bills;
    }

    /** What this player owes the cafe whose manager stands here, if anything. */
    @Nullable
    public static CafeHandovers.Summary at(ResourceLocation dimension, BlockPos pos) {
        for (CafeHandovers.Summary summary : bills) {
            if (summary.pos().equals(pos) && summary.dimension().equals(dimension)) return summary;
        }
        return null;
    }

    public static void clear() {
        bills = List.of();
    }
}
