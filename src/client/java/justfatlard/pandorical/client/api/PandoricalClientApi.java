package justfatlard.pandorical.client.api;

import justfatlard.pandorical.client.settings.ClientSettings;

/** Pandorical's API for client-side mods. */
public final class PandoricalClientApi {
    private PandoricalClientApi() {}

    public static ClientSettingsApi settings() {
        return ClientSettings.INSTANCE;
    }
}
