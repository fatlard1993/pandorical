package justfatlard.pandorical.client.entitymodel;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * An entity model as a mod ships it: geometry, part transforms and a texture, any of them optional.
 * The file format is documented on {@code EntityModelApi}; this reads it into the game's own model
 * builders, so a part here is built exactly as the game builds its own.
 */
public record EntityModelDefinition(@Nullable LayerDefinition geometry, @Nullable Identifier texture,
        Map<String, PartTransform> transforms) {

    /**
     * {@code pose.scaled(scale).translated(offset)}, the two steps the game's own mesh transformers
     * are made of: a part's position and size multiplied, then its position moved.
     */
    public record PartTransform(float sx, float sy, float sz, float ox, float oy, float oz) {
        public void apply(ModelPart part) {
            part.x = part.x * sx + ox;
            part.y = part.y * sy + oy;
            part.z = part.z * sz + oz;
            part.xScale *= sx;
            part.yScale *= sy;
            part.zScale *= sz;
        }
    }

    public static EntityModelDefinition parse(JsonObject json) {
        Identifier texture = json.has("texture") ? Identifier.parse(json.get("texture").getAsString()) : null;

        Map<String, PartTransform> transforms = new LinkedHashMap<>();
        if (json.has("transforms")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("transforms").entrySet()) {
                JsonObject t = entry.getValue().getAsJsonObject();
                float[] scale = vector(t, "scale", 1F);
                float[] offset = vector(t, "offset", 0F);
                transforms.put(entry.getKey(), new PartTransform(scale[0], scale[1], scale[2], offset[0], offset[1], offset[2]));
            }
        }

        LayerDefinition geometry = null;
        if (json.has("parts")) {
            JsonArray size = json.getAsJsonArray("texture_size");
            if (size == null || size.size() != 2) throw new IllegalArgumentException("geometry needs a texture_size of [width, height]");
            MeshDefinition mesh = new MeshDefinition();
            parts(mesh.getRoot(), json.getAsJsonObject("parts"));
            geometry = LayerDefinition.create(mesh, size.get(0).getAsInt(), size.get(1).getAsInt());
        }
        return new EntityModelDefinition(geometry, texture, Map.copyOf(transforms));
    }

    private static void parts(PartDefinition parent, JsonObject parts) {
        for (Map.Entry<String, JsonElement> entry : parts.entrySet()) {
            JsonObject part = entry.getValue().getAsJsonObject();
            CubeListBuilder cubes = CubeListBuilder.create();
            if (part.has("cubes")) {
                for (JsonElement element : part.getAsJsonArray("cubes")) {
                    JsonObject cube = element.getAsJsonObject();
                    float[] uv = vector2(cube, "uv");
                    float[] origin = vector(cube, "origin", 0F);
                    float[] extent = vector(cube, "size", 0F);
                    float[] inflate = cube.has("inflate") && cube.get("inflate").isJsonPrimitive()
                        ? new float[] {cube.get("inflate").getAsFloat(), cube.get("inflate").getAsFloat(), cube.get("inflate").getAsFloat()}
                        : vector(cube, "inflate", 0F);
                    boolean mirror = cube.has("mirror") && cube.get("mirror").getAsBoolean();
                    cubes.texOffs((int) uv[0], (int) uv[1]).mirror(mirror)
                        .addBox(origin[0], origin[1], origin[2], extent[0], extent[1], extent[2],
                            new CubeDeformation(inflate[0], inflate[1], inflate[2]));
                }
            }
            float[] pivot = vector(part, "pivot", 0F);
            float[] rotation = vector(part, "rotation", 0F);
            float[] scale = vector(part, "scale", 1F);
            PartDefinition child = parent.addOrReplaceChild(entry.getKey(), cubes, new PartPose(pivot[0], pivot[1], pivot[2],
                rotation[0] * Mth.DEG_TO_RAD, rotation[1] * Mth.DEG_TO_RAD, rotation[2] * Mth.DEG_TO_RAD,
                scale[0], scale[1], scale[2]));
            if (part.has("children")) parts(child, part.getAsJsonObject("children"));
        }
    }

    /** Three numbers, or one standing for all three, or {@code otherwise} for each when absent. */
    private static float[] vector(JsonObject json, String key, float otherwise) {
        if (!json.has(key)) return new float[] {otherwise, otherwise, otherwise};
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive()) {
            float v = value.getAsFloat();
            return new float[] {v, v, v};
        }
        JsonArray array = value.getAsJsonArray();
        if (array.size() != 3) throw new IllegalArgumentException(key + " needs three numbers");
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static float[] vector2(JsonObject json, String key) {
        if (!json.has(key)) return new float[] {0F, 0F};
        JsonArray array = json.getAsJsonArray(key);
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat()};
    }
}
