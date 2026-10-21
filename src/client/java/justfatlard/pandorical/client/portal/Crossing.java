package justfatlard.pandorical.client.portal;

import justfatlard.pandorical.client.settings.ClientPrefs;
import justfatlard.pandorical.client.settings.ClientSettings;
import justfatlard.pandorical.portal.ImmersivePortals;
import justfatlard.pandorical.protocol.ImmersivePortalsS2C;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Stepping through a portal as one movement: the swirl thickens over the server's short wait,
 * stands in for the loading screen while the world changes, and thins again on the far side.
 * The sounds are wet rather than airy, so the hold the portal has on you reads as the stuff of it.
 *
 * <p>Only when the server says its portals are immersive, and the player has not turned it off.
 */
public final class Crossing {
    private Crossing() {}

    private static final String KEY = "immersive_portals";
    private static Boolean wanted;
    private static volatile boolean serverOn;

    public static void register() {
        ClientSettings.INSTANCE.group("pandorical", "Pandorical")
            .toggle(KEY, "Immersive portals",
                "On servers that have them: see through portals, and step through as one swirl instead of a loading screen",
                Crossing::wanted, Crossing::want);
        ClientPlayNetworking.registerGlobalReceiver(ImmersivePortalsS2C.TYPE,
            (payload, context) -> serverOn = payload.on());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> serverOn = false);
    }

    public static boolean active() {
        return serverOn && wanted();
    }

    private static boolean wanted() {
        if (wanted == null) wanted = ClientPrefs.getBoolean(KEY, true);
        return wanted;
    }

    private static void want(boolean on) {
        wanted = on;
        ClientPrefs.set(KEY, on);
    }

    /** How much the swirl thickens each tick in a portal: full as the server's wait runs out. */
    public static float thickening() {
        return 1.0F / ImmersivePortals.WAIT_TICKS;
    }

    /** Stepping in: a slow slide into something thick. */
    public static SimpleSoundInstance squelchIn() {
        play(SimpleSoundInstance.forLocalAmbience(SoundEvents.SLIME_SQUISH, 0.6F, 0.35F));
        return SimpleSoundInstance.forLocalAmbience(SoundEvents.HONEY_BLOCK_SLIDE, 0.7F, 0.6F);
    }

    /** Coming out: the stuff letting go. */
    public static SimpleSoundInstance squelchOut() {
        play(SimpleSoundInstance.forLocalAmbience(SoundEvents.HONEY_BLOCK_STEP, 0.5F, 0.5F));
        return SimpleSoundInstance.forLocalAmbience(SoundEvents.SLIME_SQUISH, 0.8F, 0.45F);
    }

    private static void play(SoundInstance sound) {
        Minecraft.getInstance().getSoundManager().play(sound);
    }
}
