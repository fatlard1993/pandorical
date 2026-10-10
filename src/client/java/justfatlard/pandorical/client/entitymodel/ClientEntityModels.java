package justfatlard.pandorical.client.entitymodel;

import java.io.Reader;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.NotUnderstood;
import justfatlard.pandorical.client.ClientNotices;
import justfatlard.pandorical.client.renderer.EntityModelHolder;
import justfatlard.pandorical.protocol.EntityModelS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Entities the server assigned a model to, and the drawing of them.
 *
 * <p>Three hooks, all keyed off the render state: the state carries what was assigned
 * ({@link #extract}); the renderer swaps in the new geometry for that one entity, if there is any;
 * and every model drawn with that state - body, armour, clothes - has the part transforms laid over
 * the pose its animation gave it, each time it is posed ({@link #afterPose}). Nothing about any
 * kind of mob is known here: a part is found by its name or not at all.
 */
public final class ClientEntityModels {
    private ClientEntityModels() {}

    /** What one entity is drawn with, carried on its render state. */
    public record Drawn(Identifier id, Map<String, EntityModelDefinition.PartTransform> transforms,
            @Nullable EntityModel<?> model, @Nullable Identifier texture) {}

    private record Assignment(Identifier model, @Nullable Identifier texture, float drawScale) {}

    /** Entity id to its assignment. Ids are not reused within a session. */
    private static final Map<Integer, Assignment> ASSIGNED = new ConcurrentHashMap<>();
    /** Read once each; only successes are kept, so one asked for before the pack arrived is asked again. */
    private static final Map<Identifier, EntityModelDefinition> DEFINITIONS = new HashMap<>();
    /** New geometry, built per renderer, because it is built into that renderer's model class. */
    private static final Map<LivingEntityRenderer<?, ?, ?>, Map<Identifier, Optional<EntityModel<?>>>> BUILT = new WeakHashMap<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(EntityModelS2C.TYPE,
            (payload, context) -> context.client().execute(() -> apply(payload)));
        // The server sends it again when the entity comes back into view.
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> ASSIGNED.remove(entity.getId()));
    }

    public static void apply(EntityModelS2C payload) {
        Identifier model = payload.model().isEmpty() ? null : Identifier.tryParse(payload.model());
        if (model == null) {
            ASSIGNED.remove(payload.entityId());
            return;
        }
        Identifier texture = payload.texture().isEmpty() ? null : Identifier.tryParse(payload.texture());
        ASSIGNED.put(payload.entityId(), new Assignment(model, texture, payload.drawScale()));
    }

    public static @Nullable Identifier assigned(int entityId) {
        Assignment a = ASSIGNED.get(entityId);
        return a == null ? null : a.model();
    }

    /** Everything from the last server, on leaving it. */
    public static void clear() {
        ASSIGNED.clear();
        DEFINITIONS.clear();
        BUILT.clear();
    }

    /** Where a model's file is: {@code assets/<namespace>/pandorical/entity_models/<path>.json}. */
    public static Identifier file(Identifier model) {
        return Identifier.fromNamespaceAndPath(model.getNamespace(), "pandorical/entity_models/" + model.getPath() + ".json");
    }

    public static @Nullable EntityModelDefinition definition(Identifier model) {
        EntityModelDefinition known = DEFINITIONS.get(model);
        if (known != null) return known;
        var resource = Minecraft.getInstance().getResourceManager().getResource(file(model));
        if (resource.isEmpty()) {
            ClientNotices.report(NotUnderstood.ENTITY_MODEL, model.toString());
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            EntityModelDefinition definition = EntityModelDefinition.parse(json);
            DEFINITIONS.put(model, definition);
            return definition;
        } catch (Exception e) {
            Pandorical.LOGGER.warn("Entity model {} could not be read: {}", model, e.getMessage());
            ClientNotices.report(NotUnderstood.ENTITY_MODEL, model.toString());
            return null;
        }
    }

    /**
     * At the end of every living entity's render state, after the game's own: an assigned entity
     * carries what it is drawn with, and is drawn at its draw scale. Written every time, null
     * included, because render states are pooled and reused.
     */
    public static void extract(LivingEntityRenderer<?, ?, ?> renderer, LivingEntity entity, LivingEntityRenderState state) {
        EntityModelHolder holder = (EntityModelHolder) state;
        holder.pandorical$setEntityModel(null);
        if (ASSIGNED.isEmpty()) return;
        Assignment a = ASSIGNED.get(entity.getId());
        if (a == null) return;
        EntityModelDefinition definition = definition(a.model());
        if (definition == null) return;
        EntityModel<?> model = null;
        if (definition.geometry() != null) {
            model = built(renderer, a.model(), definition);
            // Geometry its renderer cannot animate is not drawn at all: the entity stays as it was.
            if (model == null) return;
        }
        Identifier texture = a.texture() != null ? a.texture() : definition.texture();
        holder.pandorical$setEntityModel(new Drawn(a.model(), definition.transforms(), model, texture));
        state.scale *= a.drawScale();
    }

    /**
     * The geometry built into a fresh instance of the class the renderer draws with, so the game's
     * animation for that class poses it. Null, said once, when that class will not take it: it
     * needs a constructor taking only the root part, and finds every part it wants by name.
     */
    private static @Nullable EntityModel<?> built(LivingEntityRenderer<?, ?, ?> renderer, Identifier id,
            EntityModelDefinition definition) {
        return BUILT.computeIfAbsent(renderer, r -> new HashMap<>()).computeIfAbsent(id, key -> {
            Class<?> type = renderer.getModel().getClass();
            ModelPart root = definition.geometry().bakeRoot();
            try {
                Constructor<?> constructor = type.getConstructor(ModelPart.class);
                return Optional.of((EntityModel<?>) constructor.newInstance(root));
            } catch (ReflectiveOperationException | RuntimeException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                Pandorical.LOGGER.warn("Entity model {} cannot stand in for {}: {}", id, type.getSimpleName(), cause.toString());
                ClientNotices.report(NotUnderstood.ENTITY_MODEL, id + " on " + type.getSimpleName());
                return Optional.empty();
            }
        }).orElse(null);
    }

    public static @Nullable Drawn drawn(Object state) {
        return state instanceof EntityModelHolder holder ? holder.pandorical$getEntityModel() : null;
    }

    /** After a model is posed for a state: that state's part transforms, over the pose. */
    public static void afterPose(Model<?> model, Object state) {
        Drawn drawn = drawn(state);
        if (drawn == null || drawn.transforms().isEmpty()) return;
        ModelPart root = model.root();
        for (Map.Entry<String, EntityModelDefinition.PartTransform> entry : drawn.transforms().entrySet()) {
            if (root.hasChild(entry.getKey())) entry.getValue().apply(root.getChild(entry.getKey()));
        }
    }

    /**
     * A held item is drawn at its own size on a resized arm, as it would be on the arm's owner
     * unchanged: the arm's transform scale taken back off. Called in the item's frame, which is the
     * arm's turned a quarter about x and a half about y, so the arm's length (y) is the item's z.
     */
    public static void heldItem(Object state, HumanoidArm arm, PoseStack poseStack) {
        Drawn drawn = drawn(state);
        if (drawn == null) return;
        EntityModelDefinition.PartTransform t = drawn.transforms().get(arm == HumanoidArm.RIGHT ? "right_arm" : "left_arm");
        if (t == null) return;
        poseStack.scale(1F / t.sx(), 1F / t.sz(), 1F / t.sy());
    }
}
