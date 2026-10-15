package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.entitykind.ClientEntityKinds;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A living stand-in starts with its base's attributes: the server's own values arrive in the
 * attribute packet right behind the spawn, and a living entity cannot be built with none at all.
 */
@Mixin(DefaultAttributes.class)
public abstract class EntityKindAttributesMixin {

	@Inject(method = "getSupplier", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("unchecked")
	private static void pandorical$baseAttributes(EntityType<? extends LivingEntity> type,
			CallbackInfoReturnable<AttributeSupplier> cir) {
		ClientEntityKinds.Kind kind = ClientEntityKinds.of(type);
		if (kind != null) cir.setReturnValue(DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) kind.base()));
	}

	@Inject(method = "hasSupplier", at = @At("HEAD"), cancellable = true)
	private static void pandorical$hasBaseAttributes(EntityType<?> type, CallbackInfoReturnable<Boolean> cir) {
		ClientEntityKinds.Kind kind = ClientEntityKinds.of(type);
		if (kind != null) cir.setReturnValue(DefaultAttributes.hasSupplier(kind.base()));
	}
}
