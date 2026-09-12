package justfatlard.pandorical.client.screen;

import justfatlard.pandorical.client.component.PandoricalComponent;
import justfatlard.pandorical.protocol.ComponentUpdate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The latest value of every prop updated since open, replayed onto a tree rebuilt for a resize. */
final class UpdateMemory {
    private final Map<String, Map<String, String>> latest = new HashMap<>();

    void record(List<ComponentUpdate> updates) {
        for (ComponentUpdate update : updates) {
            latest.computeIfAbsent(update.componentId(), id -> new HashMap<>()).putAll(update.changedProps());
        }
    }

    void restore(Map<String, PandoricalComponent> fresh, Map<String, PandoricalComponent> previous) {
        fresh.forEach((id, component) -> {
            Map<String, String> props = latest.get(id);
            if (props != null) component.updateProps(props);
            PandoricalComponent old = previous.get(id);
            if (old != null && old.getClass() == component.getClass()) component.carryOverFrom(old);
        });
    }
}
