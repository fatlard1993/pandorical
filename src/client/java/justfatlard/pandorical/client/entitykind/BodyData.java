package justfatlard.pandorical.client.entitykind;

import justfatlard.pandorical.client.mixin.SynchedEntityDataBuilderAccessor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.MobCategory;

/** What the generic mobs share: the values past their own class's, and how a body breathes. */
public final class BodyData {
    private BodyData() {}

    /**
     * Every value the body syncs that its class does not define itself, at the ids the server
     * gave them. Defined empty: the server pairs a body with all its values, so none is read before
     * it arrives. The builder was made long enough for them by {@code SynchedEntityDataBuilderMixin}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void defineRest(SynchedEntityData.Builder builder) {
        ClientEntityKinds.Body body = ClientEntityKinds.building();
        if (body == null) return;
        SynchedEntityData.DataItem<?>[] items = ((SynchedEntityDataBuilderAccessor) builder).pandorical$itemsById();
        for (int id = 0; id < items.length && id < body.serializers().size(); id++) {
            if (items[id] != null) continue;
            EntityDataSerializer serializer = body.serializers().get(id);
            builder.define(new EntityDataAccessor(id, serializer), null);
        }
    }

    /** The size of the builder for a body: every value it syncs. */
    public static int count(SyncedDataHolder entity, int ownCount) {
        if (!(entity instanceof BodyMob || entity instanceof AgeableBodyMob)) return ownCount;
        ClientEntityKinds.Body body = ClientEntityKinds.building();
        return body == null ? ownCount : Math.max(ownCount, body.serializers().size());
    }

    static boolean breathesWater(MobCategory category) {
        return category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
            || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS;
    }
}
