package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.EntityRegistration;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * One synced block: with no content, joining a world skips the configuration-phase sync. And one
 * entity with its own body, for {@code EntityBodies}.
 */
public final class GateContent implements ModInitializer {
	static final String BLOCK_ID = "pandorical-gametest:gate_block";
	static final Identifier BLOB_ID = Identifier.fromNamespaceAndPath("pandorical-gametest", "blob");

	public static final EntityType<Blob> BLOB = FabricEntityType.Builder.createMob(Blob::new, MobCategory.CREATURE,
			mob -> mob.defaultAttributes(Blob::createAttributes))
		.sized(0.8F, 0.6F)
		.build(ResourceKey.create(Registries.ENTITY_TYPE, BLOB_ID));

	@Override
	public void onInitialize() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.parse(BLOCK_ID));
		Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of().setId(key)));
		PandoricalApi.content().registerBlock(BLOCK_ID, new BlockRegistration());

		Registry.register(BuiltInRegistries.ENTITY_TYPE, BLOB_ID, BLOB);
		// A cow cannot read a blob's own values, so vanilla clients are not shown one after all.
		PandoricalApi.content().registerEntity(BLOB, new EntityRegistration()
			.model(BLOB_ID)
			.vanillaFallback(EntityTypes.COW));
	}
}
