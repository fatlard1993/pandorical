package justfatlard.pandorical.client.screen;

import justfatlard.pandorical.client.component.PandoricalComponent;
import justfatlard.pandorical.protocol.ComponentUpdate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a screen's components have been told since it opened, so that rebuilding the tree
 * for a resize starts from now and not from the open: a control swapped in stays swapped in, a
 * relabelled button keeps its label.
 */
final class UpdateMemory {
    private final Map<String, Map<String, String>> latest = new HashMap<>();

    void record(List<ComponentUpdate> updates) {
        for (ComponentUpdate update : updates) {
            latest.computeIfAbsent(update.componentId(), id -> new HashMap<>()).putAll(update.changedProps());
        }
    }

    /**
     * Bring a freshly built tree up to date: the latest value of every prop ever updated, then
     * each component handed the one it replaces, for whatever it keeps that no prop holds.
     */
    void restore(Map<String, PandoricalComponent> fresh, Map<String, PandoricalComponent> previous) {
        fresh.forEach((id, component) -> {
            Map<String, String> props = latest.get(id);
            if (props != null) component.updateProps(props);
            PandoricalComponent old = previous.get(id);
            if (old != null && old.getClass() == component.getClass()) component.inherit(old);
        });
    }
}
