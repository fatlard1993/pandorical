package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.entitykind.ClientEntityKinds;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A living stand-in starts with its base's attributes, and a body with every attribute there is: the server's own values arrive in the
 * attribute packet right behind the spawn, and a living entity cannot be built with none at all.
 *
 * <p>Only for a type that has none of its own. In singleplayer the server runs in this same game,
 * and the real type there has its own; taking the base's over them made every one of a mod's
 * entities its base's size and strength, for the person hosting.
 */
@Mixin(DefaultAttributes.class)
public abstract class EntityKindAttributesMixin {

	@Inject(method = "getSupplier", at = @At("RETURN"), cancellable = true)
	@SuppressWarnings("unchecked")
	private static void pandorical$baseAttributes(EntityType<? extends LivingEntity> type,
			CallbackInfoReturnable<AttributeSupplier> cir) {
		if (cir.getReturnValue() != null) return;
		ClientEntityKinds.Kind kind = ClientEntityKinds.of(type);
		if (kind != null) cir.setReturnValue(DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) kind.base()));
		else if (ClientEntityKinds.bodyOf(type) != null) cir.setReturnValue(pandorical$everyAttribute());
	}

	/**
	 * A body could have any attribute at all, and the server sends the ones it syncs - its scale,
	 * its speed - which a client throws away if the entity lacks them. So it has every one there is.
	 */
	@Unique
	private static AttributeSupplier pandorical$every;

	@Unique
	private static AttributeSupplier pandorical$everyAttribute() {
		if (pandorical$every == null) {
			AttributeSupplier.Builder builder = AttributeSupplier.builder();
			BuiltInRegistries.ATTRIBUTE.listElements().forEach(builder::add);
			pandorical$every = builder.build();
		}
		return pandorical$every;
	}

	@Inject(method = "hasSupplier", at = @At("RETURN"), cancellable = true)
	private static void pandorical$hasBaseAttributes(EntityType<?> type, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) return;
		ClientEntityKinds.Kind kind = ClientEntityKinds.of(type);
		if (kind != null) cir.setReturnValue(DefaultAttributes.hasSupplier(kind.base()));
		else if (ClientEntityKinds.bodyOf(type) != null) cir.setReturnValue(true);
	}
}
