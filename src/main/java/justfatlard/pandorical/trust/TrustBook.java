package justfatlard.pandorical.trust;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import justfatlard.pandorical.api.Trust;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What ops have chosen, saved with the world: each player's overrides, the server-wide defaults that
 * are Pandorical's to keep (PvP's is the gamerule's), and the name each player was last seen under,
 * so the page can list players who are not online.
 */
public final class TrustBook extends SavedData {
    private record Player(UUID id, String name, Map<String, Boolean> choices) {
        static final Codec<Player> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Player::id),
            Codec.STRING.fieldOf("name").forGetter(Player::name),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).fieldOf("choices").forGetter(Player::choices)
        ).apply(i, Player::new));
    }

    private record Stored(List<Player> players, Map<String, Boolean> defaults) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
            Player.CODEC.listOf().fieldOf("players").forGetter(Stored::players),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).fieldOf("defaults").forGetter(Stored::defaults)
        ).apply(i, Stored::new));
    }

    static final Codec<TrustBook> CODEC = Stored.CODEC.xmap(TrustBook::fromStored, TrustBook::toStored);
    private static final SavedDataType<TrustBook> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("pandorical", "trust"), TrustBook::new, CODEC, DataFixTypes.LEVEL);

    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Map<Trust, Boolean>> choices = new HashMap<>();
    private final Map<Trust, Boolean> defaults = new HashMap<>();

    public static TrustBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** This player's own choice, or null for the server's default. */
    public @Nullable Boolean choice(UUID player, Trust what) {
        Map<Trust, Boolean> own = choices.get(player);
        return own == null ? null : own.get(what);
    }

    public void choose(UUID player, Trust what, @Nullable Boolean allowed) {
        if (allowed == null) {
            Map<Trust, Boolean> own = choices.get(player);
            if (own != null) own.remove(what);
        } else {
            choices.computeIfAbsent(player, k -> new HashMap<>()).put(what, allowed);
        }
        setDirty();
    }

    /** The server-wide default; on unless an op has turned it off. Not used for PvP. */
    public boolean byDefault(Trust what) {
        return defaults.getOrDefault(what, true);
    }

    public void chooseDefault(Trust what, boolean allowed) {
        defaults.put(what, allowed);
        setDirty();
    }

    public void remember(UUID player, String name) {
        if (!name.equals(names.put(player, name))) setDirty();
    }

    public Map<UUID, String> known() {
        return names;
    }

    public @Nullable UUID byName(String name) {
        for (Map.Entry<UUID, String> entry : names.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) return entry.getKey();
        }
        return null;
    }

    private static TrustBook fromStored(Stored stored) {
        TrustBook book = new TrustBook();
        for (Player player : stored.players()) {
            book.names.put(player.id(), player.name());
            Map<Trust, Boolean> own = new HashMap<>();
            player.choices().forEach((id, allowed) -> {
                Trust trust = Trust.byId(id);
                if (trust != null) own.put(trust, allowed);
            });
            if (!own.isEmpty()) book.choices.put(player.id(), own);
        }
        stored.defaults().forEach((id, allowed) -> {
            Trust trust = Trust.byId(id);
            if (trust != null) book.defaults.put(trust, allowed);
        });
        return book;
    }

    private Stored toStored() {
        List<Player> players = names.entrySet().stream().map(entry -> {
            Map<String, Boolean> own = new HashMap<>();
            choices.getOrDefault(entry.getKey(), Map.of()).forEach((trust, allowed) -> own.put(trust.id(), allowed));
            return new Player(entry.getKey(), entry.getValue(), own);
        }).toList();
        Map<String, Boolean> stored = new HashMap<>();
        defaults.forEach((trust, allowed) -> stored.put(trust.id(), allowed));
        return new Stored(players, stored);
    }
}
