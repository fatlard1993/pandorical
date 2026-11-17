package justfatlard.pandorical.api;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

/**
 * An item icon drawn as one particular stack, not just its item: a miniature as its own build, a
 * named or enchanted item as it is. Set {@link ComponentType#PROP_ITEM_COMPONENTS} to
 * {@link #components}; a client that cannot read it draws the plain item.
 */
public final class ItemIcons {
	private ItemIcons() {}

	/** A prop value is one string on the wire, at most this long. */
	private static final int MAX = 32767;

	/**
	 * The stack's components, packed for {@link ComponentType#PROP_ITEM_COMPONENTS}: empty when it
	 * has none of its own, or when they are too large to send, so the plain item is drawn.
	 */
	public static String components(ItemStack stack, HolderLookup.Provider registries) {
		DataComponentPatch patch = stack.getComponentsPatch();
		if (patch.isEmpty()) return "";
		Optional<Tag> encoded = DataComponentPatch.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), patch).result();
		if (encoded.isEmpty()) return "";
		CompoundTag root = new CompoundTag();
		root.put("c", encoded.get());
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			NbtIo.writeCompressed(root, bytes);
		} catch (IOException e) {
			return "";
		}
		String packed = Base64.getEncoder().encodeToString(bytes.toByteArray());
		return packed.length() > MAX ? "" : packed;
	}

	/** What {@link #components} packed, or empty when it cannot be read here. */
	public static Optional<DataComponentPatch> read(String packed, HolderLookup.Provider registries) {
		if (packed == null || packed.isEmpty()) return Optional.empty();
		try {
			CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(packed)), NbtAccounter.create(2L << 20));
			Tag tag = root.get("c");
			if (tag == null) return Optional.empty();
			return DataComponentPatch.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag).result();
		} catch (IOException | RuntimeException e) {
			return Optional.empty();
		}
	}
}
