package justfatlard.pandorical.trip;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.SideTripApi;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class SideTrips implements SideTripApi {
	public static final SideTrips INSTANCE = new SideTrips();

	private SideTrips() {}

	private final List<Host> hosts = new CopyOnWriteArrayList<>();
	private final List<Permit> permits = new CopyOnWriteArrayList<>();
	private final Set<Object> failed = ConcurrentHashMap.newKeySet();

	@Override
	public void host(Host host) {
		hosts.add(Objects.requireNonNull(host, "host"));
	}

	@Override
	public void permit(Permit permit) {
		permits.add(Objects.requireNonNull(permit, "permit"));
	}

	@Override
	public boolean mayStepAside(ServerPlayer player) {
		for (Permit permit : permits) {
			if (asked(permit, player, permit::lets)) return true;
		}
		return false;
	}

	@Override
	public boolean away(ServerPlayer player) {
		for (Host host : hosts) {
			if (asked(host, player, host::holds)) return true;
		}
		return false;
	}

	@Override
	public void callBack(ServerPlayer player) {
		for (Host host : hosts) {
			if (!asked(host, player, host::holds)) continue;
			try {
				host.sendBack(player);
			} catch (RuntimeException e) {
				Pandorical.LOGGER.error("A side trip host failed to send {} back", player.getName().getString(), e);
			}
		}
	}

	/** One that throws is logged once and counted as no. */
	private boolean asked(Object who, ServerPlayer player, Predicate<ServerPlayer> question) {
		try {
			return question.test(player);
		} catch (RuntimeException e) {
			if (failed.add(who)) Pandorical.LOGGER.error("A side trip hook failed on {}; it is skipped wherever it fails", player.getName().getString(), e);
			return false;
		}
	}
}
