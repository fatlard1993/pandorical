package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.client.item.PandoricalItemModels;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/** Item models under {@code pandorical/items/} load in place of their plain ones. */
@Mixin(ClientItemInfoLoader.class)
public class PandoricalItemModelsMixin {
    @WrapOperation(
        method = "*",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resources/FileToIdConverter;listMatchingResources(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;"))
    private static Map<Identifier, Resource> pandorical$overlay(FileToIdConverter lister, ResourceManager manager,
            Operation<Map<Identifier, Resource>> original) {
        return PandoricalItemModels.overlay(manager, original.call(lister, manager));
    }
}
