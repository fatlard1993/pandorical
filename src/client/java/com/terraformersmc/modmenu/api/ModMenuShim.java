/*
 * Not part of Mod Menu: Pandorical's own, beside its copy of Mod Menu's API.
 */
package com.terraformersmc.modmenu.api;

/** Reaches into Mod Menu where it is installed, by name, since Pandorical does not compile against it. */
final class ModMenuShim {
	private ModMenuShim() {}

	/**
	 * A static method's result, or with a null method name a new instance; null without Mod Menu.
	 * Arguments match by their runtime types, boxed primitives standing for primitives.
	 */
	static Object call(String className, String method, Object... args) {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) return null;
		try {
			Class<?> type = Class.forName(className);
			Class<?>[] types = new Class<?>[args.length];
			for (int i = 0; i < args.length; i++) {
				types[i] = args[i] instanceof Boolean ? boolean.class
					: args[i] instanceof net.minecraft.client.gui.screens.Screen ? net.minecraft.client.gui.screens.Screen.class
					: args[i].getClass();
			}
			return method == null ? type.getConstructor(types).newInstance(args) : type.getMethod(method, types).invoke(null, args);
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}
}
