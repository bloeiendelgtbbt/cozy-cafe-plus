package io.github.chakyl.cozycafe.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves customer skins on the client.
 * <p>
 * Minecraft downloads skin textures in the background and shows Steve until the download finishes,
 * which made customers visibly swap skins right after spawning. Instead, a customer is not rendered
 * until its skin texture is actually loaded (cached skins load immediately). If a skin can't be
 * loaded within {@link #LOAD_TIMEOUT_MS}, the default skin is used so the customer never stays invisible.
 */
public class CustomerSkinUtils {
    private static final long LOAD_TIMEOUT_MS = 5000L;
    private static final Map<UUID, CustomerSkinInfo> SKIN_CACHE = new HashMap<>();
    private static final Map<UUID, Long> PENDING_SINCE = new HashMap<>();
    private static final CustomerSkinInfo SKIN_DEFAULT = new CustomerSkinInfo(true, DefaultPlayerSkin.getDefaultSkin());

    /**
     * @return the customer's skin, or null while its texture is still loading (the caller should skip rendering).
     */
    @Nullable
    public static CustomerSkinInfo getCustomerSkinInfo(@Nullable GameProfile profile) {
        if (profile == null || profile.getId() == null) {
            return SKIN_DEFAULT;
        }
        UUID id = profile.getId();
        CustomerSkinInfo cached = SKIN_CACHE.get(id);
        if (cached != null) {
            return cached;
        }

        Long since = PENDING_SINCE.get(id);
        if (since == null) {
            requestSkin(profile);
            // A skin already on disk or in memory can complete synchronously.
            return SKIN_CACHE.get(id);
        }
        if (Util.getMillis() - since > LOAD_TIMEOUT_MS) {
            // Still not loaded (no network, bad skin URL): fall back so the customer is visible.
            // If the download finishes later, the callback replaces this with the real skin.
            SKIN_CACHE.put(id, SKIN_DEFAULT);
            return SKIN_DEFAULT;
        }
        return null;
    }

    private static void requestSkin(GameProfile profile) {
        UUID id = profile.getId();
        SkinManager skinManager = Minecraft.getInstance().getSkinManager();
        MinecraftProfileTexture skin = skinManager.getInsecureSkinInformation(profile).get(MinecraftProfileTexture.Type.SKIN);
        if (skin == null) {
            // The profile carries no skin, so there is nothing to wait for.
            SKIN_CACHE.put(id, SKIN_DEFAULT);
            return;
        }
        PENDING_SINCE.put(id, Util.getMillis());
        // The callback runs on the render thread once the texture is uploaded and usable.
        skinManager.registerSkins(profile, (type, location, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) {
                boolean isSlim = "slim".equals(texture.getMetadata("model"));
                SKIN_CACHE.put(id, new CustomerSkinInfo(isSlim, location));
                PENDING_SINCE.remove(id);
            }
        }, false);
    }
}
