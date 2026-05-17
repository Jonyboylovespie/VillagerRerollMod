package com.villagerreroll;

import java.nio.file.Path;
import java.util.Arrays;

import com.mojang.blaze3d.platform.InputConstants;
import com.villagerreroll.autolibrarian.AutoLibrarianController;
import com.villagerreroll.screen.TargetBookListScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

public final class VillagerRerollClient implements ClientModInitializer {
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("villager-reroll.json");
	private static final AutoLibrarianController CONTROLLER =
		new AutoLibrarianController(
			com.villagerreroll.config.VillagerRerollConfig.load(CONFIG_PATH),
			CONFIG_PATH);
	private static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
		"key.villager_reroll.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
		KeyMapping.Category.MISC);
	private static boolean keyBindingRegistered;

	public static AutoLibrarianController getController() {
		return CONTROLLER;
	}

	@Override
	public void onInitializeClient() {
		ensureKeyBindingRegistered(Minecraft.getInstance());
		ClientTickEvents.END_CLIENT_TICK.register(VillagerRerollClient::onClientTick);
		UseBlockCallback.EVENT.register(VillagerRerollClient::onUseBlock);
	}

	private static void onClientTick(Minecraft client) {
		ensureKeyBindingRegistered(client);
		while(OPEN_MENU_KEY.consumeClick()) {
			Screen current = client.screen;
			if(current != null && current.getClass().getPackageName().equals("com.villagerreroll.screen"))
				continue;

			client.setScreen(new TargetBookListScreen(current, CONTROLLER));
		}
		CONTROLLER.tick(client);
	}

	private static InteractionResult onUseBlock(net.minecraft.world.entity.player.Player player,
		net.minecraft.world.level.Level level, net.minecraft.world.InteractionHand hand,
		net.minecraft.world.phys.BlockHitResult hitResult) {
		if(!level.isClientSide())
			return InteractionResult.PASS;

		BlockPos pos = hitResult.getBlockPos();
		if(level.getBlockState(pos).is(Blocks.LECTERN)
			&& CONTROLLER.onLecternClicked(Minecraft.getInstance(), pos))
			return InteractionResult.SUCCESS;

		return InteractionResult.PASS;
	}

	private static void ensureKeyBindingRegistered(Minecraft client) {
		if(keyBindingRegistered || client == null || client.options == null)
			return;

		Options options = client.options;
		KeyMapping[] current = options.keyMappings;
		for(KeyMapping mapping : current) {
			if(mapping.getName().equals(OPEN_MENU_KEY.getName())) {
				keyBindingRegistered = true;
				return;
			}
		}

		KeyMapping[] updated = Arrays.copyOf(current, current.length + 1);
		updated[current.length] = OPEN_MENU_KEY;

		try {
			var field = Options.class.getField("keyMappings");
			field.setAccessible(true);
			field.set(options, updated);
			keyBindingRegistered = true;
		}catch(ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to register key binding", e);
		}
	}
}
