package justfatlard.pandorical.login;

import justfatlard.pandorical.protocol.ClientModPartS2C;
import justfatlard.pandorical.protocol.ClientModsOfferS2C;
import justfatlard.pandorical.protocol.ClientModsWantC2S;
import justfatlard.pandorical.protocol.InstallKeptS2C;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.Map;

/**
 * The CurseForge build's stand-in: a server keeps no client mods for players. The payloads are
 * still registered, so its protocol matches every other build's.
 */
public final class ServedClientMods {
	private ServedClientMods() {}

	public static void registerPayloads() {
		PayloadTypeRegistry.clientboundConfiguration().register(ClientModsOfferS2C.TYPE, ClientModsOfferS2C.STREAM_CODEC);
		PayloadTypeRegistry.clientboundConfiguration().register(ClientModPartS2C.TYPE, ClientModPartS2C.STREAM_CODEC);
		PayloadTypeRegistry.serverboundConfiguration().register(ClientModsWantC2S.TYPE, ClientModsWantC2S.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(InstallKeptS2C.TYPE, InstallKeptS2C.STREAM_CODEC);
	}

	public static void register() {}

	public static Map<String, String> versions() {
		return Map.of();
	}
}
