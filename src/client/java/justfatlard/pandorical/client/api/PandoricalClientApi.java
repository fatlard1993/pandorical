package justfatlard.pandorical.client.api;

import justfatlard.pandorical.client.settings.ClientSettings;

/** What Pandorical offers a client-side mod: today, a place in the mod menu for its settings. */
public final class PandoricalClientApi {
    private PandoricalClientApi() {}

    public static ClientSettingsApi settings() {
        return ClientSettings.INSTANCE;
    }
}
