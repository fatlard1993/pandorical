package justfatlard.pandorical.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Predicate;

/**
 * Per-player trust, set by ops: whether a player may fight other players, use fire and lava, use
 * explosives, or hurt other people's animals.
 *
 * <p>Pandorical enforces the game's own ways of doing each. A mod that does one of these things its
 * own way - fire from a staff, an explosive arrow - asks {@link #may} before doing it.
 */
public interface TrustApi {
    /** Whether this player may, by their own choice from an op or else the server's default. */
    boolean may(ServerPlayer player, Trust what);

    /**
     * Whether this player may hurt this creature: another player only as PvP allows, both ways
     * round and as the game's own check would; somebody else's pet or mount, a villager or a named
     * creature only with {@link Trust#ANIMALS}. For a mod that hurts, burns or curses something by
     * a way of its own the game does not check.
     */
    boolean mayHurt(ServerPlayer attacker, LivingEntity target);

    /**
     * Places whose own PvP rules stand whatever anyone's trust says, such as an arena: in a level
     * this holds for, Pandorical leaves PvP to the game and whoever else decides it.
     */
    void pvpDecidedElsewhere(Predicate<ServerLevel> where);

    /** The page where ops set it, player by player; for an op with a Pandorical client. */
    void open(ServerPlayer op);
}
