package justfatlard.pandorical.gametest;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** One synced block: with no content, joining a world skips the configuration-phase sync. */
public final class GateContent implements ModInitializer {
	static final String BLOCK_ID = "pandorical-gametest:gate_block";

	@Override
	public void onInitialize() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.parse(BLOCK_ID));
		Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of().setId(key)));
		PandoricalApi.content().registerBlock(BLOCK_ID, new BlockRegistration());
	}
}
