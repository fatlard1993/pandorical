package justfatlard.pandorical.client.decal;

import justfatlard.pandorical.protocol.BannerDecalsS2C;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BannerDecalStore {
	private BannerDecalStore() {}

	private static final Map<Long, BannerDecalsS2C.Entry> DECALS = new ConcurrentHashMap<>();

	public static void apply(BannerDecalsS2C payload) {
		for (BannerDecalsS2C.Entry entry : payload.entries()) {
			if (entry.layers().layers().isEmpty()) {
				DECALS.remove(entry.pos());
			} else {
				DECALS.put(entry.pos(), entry);
			}
		}
	}

	public static Collection<BannerDecalsS2C.Entry> all() {
		return DECALS.values();
	}

	public static void clear() {
		DECALS.clear();
	}
}
