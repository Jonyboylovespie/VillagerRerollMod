package com.villagerreroll.autolibrarian;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import com.villagerreroll.config.TargetBook;
import com.villagerreroll.config.VillagerRerollConfig;

public final class AutoLibrarianController {
	private record BookTradeInfo(String enchantmentId, int level, int price) {
	}

	private record MatchedTrade(int offerIndex, TargetBook target) {
	}

	private final VillagerRerollConfig config;
	private final Path configPath;

	private boolean running;
	private boolean awaitingLectern;
	private boolean rerollPending;
	private boolean breakingJobSite;
	private boolean breakingStarted;
	private boolean waitingForLectern;
	private Integer lockInTradePendingOfferIndex;
	private int lockInTradePendingCooldown;
	private int cooldownTicks;

	public AutoLibrarianController(VillagerRerollConfig config, Path configPath) {
		this.config = config;
		this.configPath = configPath;
	}

	public VillagerRerollConfig getConfig() {
		return config;
	}

	public boolean isRunning() {
		return running;
	}

	public boolean isAwaitingLectern() {
		return awaitingLectern;
	}

	public Optional<String> validateReady() {
		if(config.getTargets().isEmpty())
			return Optional.of("Add at least one target book first.");
		return Optional.empty();
	}

	public boolean armForLectern(Minecraft client) {
		Optional<String> error = validateReady();
		if(error.isPresent()) {
			notify(client, error.get());
			return false;
		}
		if(client.level == null || client.player == null) {
			notify(client, "Join a world before starting.");
			return false;
		}

		running = false;
		awaitingLectern = true;
		rerollPending = false;
		breakingJobSite = false;
		breakingStarted = false;
		waitingForLectern = false;
		lockInTradePendingOfferIndex = null;
		lockInTradePendingCooldown = 0;
		cooldownTicks = 0;
		notify(client, "Right click the lectern you wish to roll.");
		return true;
	}

	public void stop(Minecraft client, String message) {
		running = false;
		awaitingLectern = false;
		rerollPending = false;
		breakingJobSite = false;
		breakingStarted = false;
		waitingForLectern = false;
		lockInTradePendingOfferIndex = null;
		lockInTradePendingCooldown = 0;
		cooldownTicks = 0;
		if(client.gameMode != null && client.gameMode.isDestroying())
			client.gameMode.stopDestroyBlock();
		if(message != null && !message.isBlank())
			notify(client, message);
	}

	public void toggle(Minecraft client) {
		if(running)
			stop(client, "Auto Librarian stopped.");
		else
			armForLectern(client);
	}

	public void tick(Minecraft client) {
		if(!running && !awaitingLectern)
			return;
		if(client.level == null || client.player == null || client.gameMode == null)
			return;
		if(lockInTradePendingOfferIndex != null) {
			if(!(client.screen instanceof MerchantScreen merchantScreen)) {
				lockInTradePendingOfferIndex = null;
				lockInTradePendingCooldown = 0;
				stop(client, "Trade screen closed before locking the trade.");
				return;
			}

			if(lockInTradePendingCooldown > 0) {
				lockInTradePendingCooldown--;
				return;
			}

			int offerIndex = lockInTradePendingOfferIndex.intValue();
			if(lockInTrade(client, merchantScreen, offerIndex)) {
				lockInTradePendingOfferIndex = null;
				lockInTradePendingCooldown = 0;
				stop(client, "Stopped on match and locked trade.");
			} else {
				lockInTradePendingOfferIndex = null;
				lockInTradePendingCooldown = 0;
				stop(client, "Stopped on match, but could not lock trade.");
			}
			return;
		}
		if(client.screen != null
			&& client.screen.getClass().getPackageName().equals("com.villagerreroll.screen"))
			return;
		if(awaitingLectern)
			return;
		if(cooldownTicks > 0) {
			cooldownTicks--;
			return;
		}

		BlockPos jobSite = config.getJobSite();
		if(jobSite == null) {
			stop(client, "Bind a job site first.");
			return;
		}

		Villager villager = findTargetVillager(client, jobSite);
		if(villager == null) {
			stop(client, "Couldn't find a nearby villager.");
			return;
		}

		if(client.screen instanceof MerchantScreen merchantScreen) {
			handleTradeScreen(client, merchantScreen);
			return;
		}

		BlockState state = client.level.getBlockState(jobSite);

		if(breakingJobSite) {
			breakJobSite(client, jobSite, state);
			return;
		}

		if(rerollPending) {
			if(state.is(Blocks.LECTERN)) {
				beginBreakingJobSite(client, jobSite, state);
				return;
			}

			rerollPending = false;
		}

		if(state.isAir() || state.canBeReplaced()) {
			if(placeLectern(client, jobSite))
				cooldownTicks = 4;
			return;
		}

		if(!state.is(Blocks.LECTERN)) {
			beginBreakingJobSite(client, jobSite, state);
			return;
		}

		if(villager.distanceToSqr(Vec3.atCenterOf(jobSite)) > rangeSq(config.getRange())) {
			stop(client, "The librarian is out of range.");
			return;
		}

		openTrade(client, villager);
		cooldownTicks = 4;
	}

	public void setJobSite(BlockPos jobSite) {
		config.setJobSite(jobSite);
		save();
	}

	public void setRange(int range) {
		config.setRange(range);
		save();
	}

	public void addTarget(TargetBook target) {
		config.getTargets().removeIf(existing -> existing.enchantmentId().equals(target.enchantmentId()));
		config.addTarget(target);
		save();
	}

	public boolean replaceTarget(int index, TargetBook target) {
		boolean replaced = config.replaceTarget(index, target);
		if(replaced)
			save();
		return replaced;
	}

	public boolean removeTarget(int index) {
		boolean removed = config.removeTarget(index);
		if(removed)
			save();
		return removed;
	}

	public boolean removeTarget(String enchantmentId, int level) {
		boolean removed = config.getTargets().removeIf(target -> target.enchantmentId()
			.equals(TargetBook.normalize(enchantmentId)) && target.level() == level);
		if(removed)
			save();
		return removed;
	}

	public void clearTargets() {
		config.getTargets().clear();
		save();
	}

	public void saveConfig() {
		save();
	}

	public List<TargetBook> getTargets() {
		return List.copyOf(config.getTargets());
	}

	public BlockPos getJobSite() {
		return config.getJobSite();
	}

	public boolean isLockInTradeEnabled() {
		return config.isLockInTrade();
	}

	public void setLockInTradeEnabled(boolean lockInTrade) {
		config.setLockInTrade(lockInTrade);
		save();
	}

	public boolean onLecternClicked(Minecraft client, BlockPos pos) {
		if(!awaitingLectern || client.level == null)
			return false;

		if(!client.level.getBlockState(pos).is(Blocks.LECTERN))
			return false;

		config.setJobSite(pos);
		save();
		awaitingLectern = false;
		running = true;
		rerollPending = false;
		breakingJobSite = false;
		breakingStarted = false;
		waitingForLectern = false;
		cooldownTicks = 4;
		notify(client, "Rolling started.");
		return true;
	}

	private void save() {
		config.save(configPath);
	}

	private void handleTradeScreen(Minecraft client, MerchantScreen screen) {
		if(client.player == null)
			return;

		if(screen.getMenu().getTraderXp() > 0) {
			stop(client, "This villager is already experienced and won't reroll.");
			return;
		}

		Optional<MatchedTrade> match = findMatchingOffer(client, screen.getMenu().getOffers());
		if(match.isPresent()) {
			MatchedTrade found = match.get();
			notify(client, "Found " + describe(found.target()) + ".");
			if(config.isLockInTrade()) {
				lockInTradePendingOfferIndex = found.offerIndex();
				lockInTradePendingCooldown = 1;
				notify(client, "Locking trade...");
			} else {
				stop(client, "Stopped on match.");
			}
			return;
		}

		Optional<BookTradeInfo> book = findFirstBookOffer(client, screen.getMenu().getOffers());
		if(book.isPresent()) {
			BookTradeInfo info = book.get();
			notify(client, "Selling " + info.enchantmentId() + " " + info.level()
				+ " for " + info.price() + " emeralds. Rerolling...");
		} else {
			notify(client, "Not selling a book. Rerolling...");
		}

		client.player.closeContainer();
		rerollPending = true;
	}

	private Optional<BookTradeInfo> findFirstBookOffer(Minecraft client, MerchantOffers offers) {
		for(MerchantOffer offer : offers) {
			ItemStack result = offer.getResult();
			if(!result.is(Items.ENCHANTED_BOOK))
				continue;

			var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(result);
			if(enchantments.isEmpty())
				continue;

			var entry = enchantments.entrySet().iterator().next();
			Object key = entry.getKey();
			Enchantment enchantment;
			if(key instanceof Holder<?> holder && holder.value() instanceof Enchantment e)
				enchantment = e;
			else if(key instanceof Enchantment e)
				enchantment = e;
			else
				continue;

			Registry<Enchantment> registry = client.level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT);
			String id = String.valueOf(registry.getKey(enchantment));
			int level = entry.getValue();
			int price = offer.getCostA().getCount();
			return Optional.of(new BookTradeInfo(id, level, price));
		}

		return Optional.empty();
	}

	private Optional<MatchedTrade> findMatchingOffer(Minecraft client, MerchantOffers offers) {
		for(int offerIndex = 0; offerIndex < offers.size(); offerIndex++) {
			MerchantOffer offer = offers.get(offerIndex);
			ItemStack result = offer.getResult();
			if(!result.is(Items.ENCHANTED_BOOK))
				continue;

			var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(result);
			if(enchantments.isEmpty())
				continue;

			var entry = enchantments.entrySet().iterator().next();
			Object key = entry.getKey();
			Enchantment enchantment;
			if(key instanceof Holder<?> holder && holder.value() instanceof Enchantment e)
				enchantment = e;
			else if(key instanceof Enchantment e)
				enchantment = e;
			else
				continue;

			Registry<Enchantment> registry = client.level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT);
			String id = String.valueOf(registry.getKey(enchantment));
			int level = entry.getValue();
			int price = offer.getCostA().getCount();

			for(TargetBook target : config.getTargets()) {
				if(target.matches(id, level, price))
					return Optional.of(new MatchedTrade(offerIndex, target));
			}
		}

		return Optional.empty();
	}

	private Villager findTargetVillager(Minecraft client, BlockPos jobSite) {
		double maxDistance = config.getRange();
		List<Villager> villagers = client.level.getEntitiesOfClass(Villager.class,
			client.player.getBoundingBox().inflate(maxDistance), villager -> {
				if(villager.isRemoved() || !villager.isAlive())
					return false;
				if(villager.isBaby())
					return false;
				if(villager.distanceToSqr(Vec3.atCenterOf(jobSite)) > rangeSq(config.getRange()))
					return false;
				return true;
			});

		return villagers.stream().min(Comparator
			.comparingInt((Villager villager) -> villager.getVillagerData()
				.profession() == VillagerProfession.LIBRARIAN ? 0 : 1)
			.thenComparingDouble(villager -> villager.distanceToSqr(client.player)))
			.orElse(null);
	}

	private boolean placeLectern(Minecraft client, BlockPos jobSite) {
		if(client.player == null || client.gameMode == null)
			return false;

		if(!equipLectern(client)) {
			if(!waitingForLectern) {
				waitingForLectern = true;
				notify(client, "Waiting for you to put a lecturn in your hotbar or inventory");
			}
			return false;
		}

		waitingForLectern = false;

		InteractionHand hand = InteractionHand.MAIN_HAND;
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(jobSite), net.minecraft.core.Direction.UP, jobSite, false);
		client.gameMode.useItemOn(client.player, hand, hit);
		client.player.swing(hand);
		return true;
	}

	private void breakJobSite(Minecraft client, BlockPos jobSite, BlockState state) {
		if(client.player == null || client.gameMode == null)
			return;

		if(isAxeAboutToBreak(client)) {
			stop(client, "Stopped before the axe broke.");
			return;
		}

		if(state.canBeReplaced()) {
			if(client.gameMode.isDestroying())
				client.gameMode.stopDestroyBlock();
			breakingJobSite = false;
			breakingStarted = false;
			rerollPending = false;
			return;
		}

		boolean started = breakingStarted
			? client.gameMode.continueDestroyBlock(jobSite, Direction.UP)
			: client.gameMode.startDestroyBlock(jobSite, Direction.UP);
		breakingStarted = true;
		if(started)
			client.player.swing(InteractionHand.MAIN_HAND);
	}

	private void beginBreakingJobSite(Minecraft client, BlockPos jobSite, BlockState state) {
		if(client.player == null)
			return;

		if(!client.player.getAbilities().instabuild && !equipBestAxe(client)) {
			stop(client, "Put an axe in your hotbar or inventory.");
			return;
		}

		if(isAxeAboutToBreak(client)) {
			stop(client, "Stopped before the axe broke.");
			return;
		}

		breakingJobSite = true;
		breakingStarted = false;
		breakJobSite(client, jobSite, state);
	}

	private void openTrade(Minecraft client, Villager villager) {
		if(client.player == null || client.gameMode == null)
			return;

		Vec3 hitVec = villager.getBoundingBox().getCenter();
		EntityHitResult hitResult = new EntityHitResult(villager, hitVec);
		client.gameMode.interact(client.player, villager, hitResult, InteractionHand.MAIN_HAND);
		client.player.swing(InteractionHand.MAIN_HAND);
	}

	private boolean equipLectern(Minecraft client) {
		Player player = client.player;
		if(player == null || client.getConnection() == null)
			return false;

		var inventory = player.getInventory();
		int selectedSlot = inventory.getSelectedSlot();

		for(int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
			if(!inventory.getItem(hotbarSlot).is(Items.LECTERN))
				continue;

			if(selectedSlot != hotbarSlot) {
				inventory.setSelectedSlot(hotbarSlot);
				client.getConnection()
					.send(new ServerboundSetCarriedItemPacket(hotbarSlot));
			}
			return true;
		}

		int inventorySlot = inventory.findSlotMatchingItem(new ItemStack(Items.LECTERN));
		if(inventorySlot < 0)
			return false;

		int menuSlot = -1;
		for(int i = 0; i < player.inventoryMenu.slots.size(); i++) {
			if(player.inventoryMenu.slots.get(i).getContainerSlot() == inventorySlot) {
				menuSlot = i;
				break;
			}
		}

		if(menuSlot < 0)
			return false;

		client.gameMode.handleContainerInput(player.inventoryMenu.containerId,
			menuSlot, selectedSlot, ContainerInput.SWAP, player);
		client.getConnection().send(new ServerboundSetCarriedItemPacket(selectedSlot));
		return true;
	}

	private boolean equipBestAxe(Minecraft client) {
		Player player = client.player;
		if(player == null || client.getConnection() == null)
			return false;

		Item[] priority = {Items.NETHERITE_AXE, Items.DIAMOND_AXE,
			Items.GOLDEN_AXE, Items.IRON_AXE, Items.STONE_AXE, Items.WOODEN_AXE};
		var inventory = player.getInventory();
		int selectedSlot = inventory.getSelectedSlot();

		for(Item axe : priority) {
			int slot = findInventorySlot(inventory, axe);
			if(slot < 0)
				continue;

			if(net.minecraft.world.entity.player.Inventory.isHotbarSlot(slot)) {
				if(selectedSlot != slot) {
					inventory.setSelectedSlot(slot);
					client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
				}
				return true;
			}

			return swapInventorySlotIntoSelected(client, player, slot, selectedSlot);
		}

		return false;
	}

	private int findInventorySlot(net.minecraft.world.entity.player.Inventory inventory,
		Item item) {
		for(int slot = 0; slot < 36; slot++) {
			if(inventory.getItem(slot).is(item))
				return slot;
		}
		return -1;
	}

	private boolean swapInventorySlotIntoSelected(Minecraft client, Player player,
		int inventorySlot, int selectedSlot) {
		int menuSlot = -1;
		for(int i = 0; i < player.inventoryMenu.slots.size(); i++) {
			if(player.inventoryMenu.slots.get(i).getContainerSlot() == inventorySlot) {
				menuSlot = i;
				break;
			}
		}

		if(menuSlot < 0)
			return false;

		client.gameMode.handleContainerInput(player.inventoryMenu.containerId,
			menuSlot, selectedSlot, ContainerInput.SWAP, player);
		client.getConnection().send(new ServerboundSetCarriedItemPacket(selectedSlot));
		return true;
	}

	private String describe(TargetBook target) {
		return target.enchantmentId() + " level " + target.level() + " for up to "
			+ target.maxPrice() + " emeralds";
	}

	private boolean lockInTrade(Minecraft client, MerchantScreen screen, int offerIndex) {
		try {
			screen.getMenu().setSelectionHint(offerIndex);
			screen.getMenu().tryMoveItems(offerIndex);
			client.getConnection().send(new ServerboundSelectTradePacket(offerIndex));
			client.gameMode.handleContainerInput(screen.getMenu().containerId,
				2, 0, ContainerInput.PICKUP, client.player);
			return true;
		} catch(Exception e) {
			System.out.println("Failed to lock in trade");
			e.printStackTrace();
			return false;
		}
	}

	private boolean isAxeAboutToBreak(Minecraft client) {
		if(client.player == null)
			return false;

		ItemStack stack = client.player.getMainHandItem();
		if(!isAxe(stack) && !stack.isDamageableItem())
			return false;

		if(!stack.isDamageableItem())
			return false;

		int remaining = stack.getMaxDamage() - stack.getDamageValue();
		return remaining <= 1;
	}

	private boolean isAxe(ItemStack stack) {
		return stack.is(Items.NETHERITE_AXE) || stack.is(Items.DIAMOND_AXE) || stack.is(Items.GOLDEN_AXE)
			|| stack.is(Items.IRON_AXE) || stack.is(Items.STONE_AXE) || stack.is(Items.WOODEN_AXE);
	}

	private double rangeSq(int range) {
		return (double)range * range;
	}

	private void notify(Minecraft client, String text) {
		String message = "[Villager Reroll] " + text;
		System.out.println(message);
		if(client.player != null)
			client.player.sendSystemMessage(Component.literal(message));
	}
}
