package justfatlard.pandorical.client.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonListenerAccessor {
	@Accessor("serverData") ServerData pandorical$serverData();

	@Accessor("connection") Connection pandorical$connection();
}
