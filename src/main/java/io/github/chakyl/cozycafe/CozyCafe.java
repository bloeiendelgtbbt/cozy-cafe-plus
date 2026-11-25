package io.github.chakyl.cozycafe;

import io.github.chakyl.cozycafe.event.CommonEvents;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CozyCafe implements ModInitializer {
    public static final String MODID = "cozyrestaurant";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final CozyConfig CONFIG = CozyConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MODID + "-common.toml"));
    @Override
    public void onInitialize() {
        CozyRegistry.register();
        EvilPacketsIHateThem.registerServerReceivers();
        CommonEvents.register();
    }
}
