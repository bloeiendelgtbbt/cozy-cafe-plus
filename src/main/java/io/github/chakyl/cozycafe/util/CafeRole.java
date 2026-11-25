package io.github.chakyl.cozycafe.util;

/**
 * Label shown under a player's name while they work in an open cafe.
 */
public enum CafeRole {
    OWNER("role.cozyrestaurant.owner", 0x8A3FC2),
    WAITER("role.cozyrestaurant.waiter", 0xC9A227);

    public final String translationKey;
    /** Dark purple for the owner, dark yellow for waiters. */
    public final int color;

    CafeRole(String translationKey, int color) {
        this.translationKey = translationKey;
        this.color = color;
    }
}
