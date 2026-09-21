package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * What a player's client could not act on, reported back to the server that said it.
 *
 * <p>Your mod runs where you can see it and draws where you cannot. A word this Pandorical has no
 * code for is skipped quietly on the client, so without this the feature is absent and the
 * log that says why sits on a machine you will never read. Pandorical writes every report to the
 * server log; listen here to do more with it.
 *
 * <pre>{@code
 * PandoricalApi.onNotUnderstood((player, kind, value) -> {
 *     if (NotUnderstood.COMPONENT_TYPE.equals(kind)) fallBackToPlainScreen(player);
 * });
 * }</pre>
 *
 * <p>A client reports each distinct word once per session, so this is a fair signal to act on and
 * not a stream. An older client that predates reporting says nothing at all, so treat silence as
 * "no news", never as "understood".
 */
public interface NotUnderstood {

    /** A component type in a screen or HUD. The component drew nothing, or its fallback. */
    String COMPONENT_TYPE = "component_type";
    /** A camera hint type. */
    String CAMERA_HINT = "camera_hint";
    /** A camera perspective. The view was left as the player had it. */
    String PERSPECTIVE = "perspective";
    /** An animation channel target. The channel was left out of the animation. */
    String ANIMATION_TARGET = "animation_target";
    /** A chest overlay op. Nothing was drawn or removed. */
    String CHEST_OP = "chest_op";
    /** A block tint type. The blocks in that group keep their vanilla colour. */
    String TINT_TYPE = "tint_type";
    /** A tool kind on a synced item. The item is plain. */
    String TOOL_KIND = "tool_kind";
    /** An entity renderer key. The entity has no renderer and does not show. */
    String RENDERER_KEY = "renderer_key";
    /** A vanilla HUD element id. That element is still drawn. */
    String HUD_ELEMENT = "hud_element";
    /** A HUD anchor. The overlay sits top left. */
    String HUD_ANCHOR = "hud_anchor";

    /** Told when a player's client reports a word it could not act on. */
    @FunctionalInterface
    interface Listener {
        void accept(ServerPlayer player, String kind, String value);
    }
}
