package justfatlard.pandorical.trust;

import justfatlard.pandorical.api.Trust;
import justfatlard.pandorical.api.TrustApi;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * What ops have trusted each player with, and the game's own ways of doing each, refused to a
 * player who is not trusted.
 *
 * <p>A player's own choice wins; without one, the server's default. PvP's default is the game's
 * {@code pvp} gamerule, so a server that has never opened this page plays exactly as before, and an
 * op who trusts one player with PvP on a PvP-off server lets that player fight others they trust.
 *
 * <p>What is refused is the deliberate act, by hand: lighting, pouring, placing, setting off,
 * striking. What follows from it is the world's business - fire spreading, a redstone line reaching
 * TNT - and is not traced back to a player.
 */
public final class TrustRules implements TrustApi {
    public static final TrustRules INSTANCE = new TrustRules();

    private final List<Predicate<ServerLevel>> elsewhere = new CopyOnWriteArrayList<>();

    private TrustRules() {}

    @Override
    public boolean may(ServerPlayer player, Trust what) {
        Boolean own = TrustBook.get(player.level().getServer()).choice(player.getUUID(), what);
        if (own != null) return own;
        return byDefault(player.level(), what);
    }

    public static boolean byDefault(ServerLevel level, Trust what) {
        return what == Trust.PVP
            ? level.getGameRules().get(GameRules.PVP)
            : TrustBook.get(level.getServer()).byDefault(what);
    }

    @Override
    public boolean mayHurt(ServerPlayer attacker, LivingEntity target) {
        if (target == attacker) return true;
        if (target instanceof ServerPlayer victim) return victim.canHarmPlayer(attacker);
        return !belongsToOthers(target, attacker) || may(attacker, Trust.ANIMALS);
    }

    @Override
    public void pvpDecidedElsewhere(Predicate<ServerLevel> where) {
        elsewhere.add(where);
    }

    @Override
    public void open(ServerPlayer op) {
        TrustScreen.open(op);
    }

    /**
     * Whether {@code attacker} may hurt {@code victim}, or null to leave it to the game: when neither
     * has a choice of their own, the gamerule already says what both would get, and in a place that
     * decides PvP for itself nothing here has a say.
     */
    public @Nullable Boolean pvp(ServerPlayer victim, ServerPlayer attacker) {
        if (victim == attacker) return null;
        for (Predicate<ServerLevel> where : elsewhere) if (where.test(victim.level())) return null;
        TrustBook book = TrustBook.get(victim.level().getServer());
        if (book.choice(victim.getUUID(), Trust.PVP) == null && book.choice(attacker.getUUID(), Trust.PVP) == null) return null;
        return may(victim, Trust.PVP) && may(attacker, Trust.PVP);
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            TrustBook.get(server).remember(handler.player.getUUID(), handler.player.getGameProfile().name()));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer server)) return InteractionResult.PASS;
            Trust needed = forBlock(server, player.getItemInHand(hand), hit);
            return needed == null || INSTANCE.may(server, needed) ? InteractionResult.PASS : refuse(server, needed);
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer server)) return InteractionResult.PASS;
            if (!player.getItemInHand(hand).is(Items.LAVA_BUCKET) || INSTANCE.may(server, Trust.FIRE)) return InteractionResult.PASS;
            // The client poured it already, on its own say-so: give it back its bucket.
            server.inventoryMenu.sendAllDataToRemote();
            return refuse(server, Trust.FIRE);
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer server) || !(entity instanceof Creeper)) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            boolean lights = held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE);
            return !lights || INSTANCE.may(server, Trust.EXPLOSIVES) ? InteractionResult.PASS : refuse(server, Trust.EXPLOSIVES);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(source.getEntity() instanceof ServerPlayer attacker) || !belongsToOthers(entity, attacker)) return true;
            if (INSTANCE.may(attacker, Trust.ANIMALS)) return true;
            refuse(attacker, Trust.ANIMALS);
            return false;
        });
    }

    /** What using {@code held} on the block {@code hit} would need, or null for nothing. */
    private static @Nullable Trust forBlock(ServerPlayer player, ItemStack held, BlockHitResult hit) {
        ServerLevel level = player.level();
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) {
            return state.getBlock() instanceof TntBlock ? Trust.EXPLOSIVES : Trust.FIRE;
        }
        if (held.is(Items.END_CRYSTAL)) return Trust.EXPLOSIVES;
        if (held.is(Items.WITHER_SKELETON_SKULL) && besideSoul(level, pos.relative(hit.getDirection()))) return Trust.EXPLOSIVES;
        if (state.getBlock() instanceof BedBlock bed && bed.getBedRule(level, pos).destroyOnUse() && !player.isSecondaryUseActive()) {
            return Trust.EXPLOSIVES;
        }
        if (state.getBlock() instanceof RespawnAnchorBlock && state.getValue(RespawnAnchorBlock.CHARGE) > 0
                && !RespawnAnchorBlock.canSetSpawn(level, pos) && !held.is(Items.GLOWSTONE)) {
            return Trust.EXPLOSIVES;
        }
        return null;
    }

    /** A skull set on the soul sand or soil a wither is built from, which is what summons one. */
    private static boolean besideSoul(ServerLevel level, BlockPos skull) {
        BlockState below = level.getBlockState(skull.below());
        return below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL);
    }

    /** Somebody else's pet or mount, a villager, or a creature someone has named. */
    private static boolean belongsToOthers(LivingEntity entity, ServerPlayer attacker) {
        if (entity instanceof Player) return false;
        if (entity instanceof OwnableEntity owned && owned.getOwnerReference() != null) {
            return !owned.getOwnerReference().getUUID().equals(attacker.getUUID());
        }
        if (entity instanceof AbstractVillager) return true;
        return entity.hasCustomName() && !(entity instanceof Enemy);
    }

    private static InteractionResult refuse(ServerPlayer player, Trust what) {
        player.sendOverlayMessage(Component.translatableWithFallback("pandorical.trust.refused." + what.id(),
            "An op has not trusted you with " + what.label.toLowerCase(java.util.Locale.ROOT)));
        return InteractionResult.FAIL;
    }
}
