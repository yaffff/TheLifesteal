package theLifesteal.crafting;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import theLifesteal.ColorUtils;

import java.util.*;

public class AdminCraftingGUI {

    private final JavaPlugin plugin;
    private final CraftingManager craftingManager;
    private final CustomItemManager customItemManager;
    private final FileConfiguration config;
    private final Map<UUID, RecipeEditSession> editSessions;
    private final Map<UUID, Inventory> openEditors;
    private final NamespacedKey adminRecipeKey;

    private static final int RESULT_SLOT = 4;
    private static final int[] MATERIAL_SLOTS = {19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final int TIME_SLOT = 47;
    private static final int XP_SLOT = 48;
    private static final int CATEGORY_SLOT = 46;
    private static final int SAVE_SLOT = 52;
    private static final int DELETE_SLOT = 51;
    private static final int CANCEL_SLOT = 53;

    public AdminCraftingGUI(JavaPlugin plugin, CraftingManager craftingManager,
                            CustomItemManager customItemManager, FileConfiguration config) {
        this.plugin = plugin;
        this.craftingManager = craftingManager;
        this.customItemManager = customItemManager;
        this.config = config;
        this.editSessions = new HashMap<>();
        this.openEditors = new HashMap<>();
        this.adminRecipeKey = new NamespacedKey(plugin, "admin_recipe_id");
    }

    // ---------- PUBLIC CLEANUP (called from main class on quit) ----------
    public void cleanupPlayer(UUID uuid) {
        editSessions.remove(uuid);
        openEditors.remove(uuid);
    }

    // ========== ADMIN MAIN MENU ==========
    public void openAdminMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text(ColorUtils.colorize("&c⚙ &4&lAdmin Crafting Menu &c⚙")));

        List<CraftingRecipe> allRecipes = new ArrayList<>(craftingManager.getAllRecipes());
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};

        for (int i = 0; i < Math.min(allRecipes.size(), slots.length); i++) {
            CraftingRecipe recipe = allRecipes.get(i);
            ItemStack display = recipe.getResult().clone();
            ItemMeta meta = display.getItemMeta();
            meta.getPersistentDataContainer().set(adminRecipeKey, PersistentDataType.STRING, recipe.getId());

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text(ColorUtils.colorize("&7&m-------------------")));
            lore.add(Component.text(ColorUtils.colorize("&eID: &f" + recipe.getId())));
            lore.add(Component.text(ColorUtils.colorize("&eCategory: &f" + recipe.getCategory())));
            lore.add(Component.text(ColorUtils.colorize("&eTime: &f" + recipe.getCraftingTime() + "s")));
            lore.add(Component.text(ColorUtils.colorize("&a▶ Left-click to edit")));
            lore.add(Component.text(ColorUtils.colorize("&c▶ Right-click to delete")));
            meta.lore(lore);
            display.setItemMeta(meta);
            gui.setItem(slots[i], display);
        }

        // Add new recipe button (slot 49)
        gui.setItem(49, createItem(Material.EMERALD_BLOCK,
                "&a✦ &6&lAdd New Recipe &a✦",
                "&7Click with result item in hand"));
        // Back button (slot 45)
        gui.setItem(45, createItem(Material.ARROW, "&a← Back"));

        ItemStack filler = createFiller();
        for (int i = 0; i < 54; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, filler);
        }
        player.openInventory(gui);
    }

    public void handleAdminMenuClick(Player player, int slot, ClickType clickType, ItemStack clicked) {
        // Back button
        if (slot == 45) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.performCommand("craft"), 2L);
            return;
        }

        // Add new recipe button
        if (slot == 49) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                player.sendMessage(ColorUtils.colorize("&cHold the result item in your hand!"));
                return;
            }
            String id = "recipe_" + System.currentTimeMillis();
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openRecipeEditor(player, id, hand.clone()), 2L);
            return;
        }

        // Recipe items (identified by persistent data)
        if (clicked != null && clicked.hasItemMeta()) {
            String recipeId = clicked.getItemMeta().getPersistentDataContainer()
                    .get(adminRecipeKey, PersistentDataType.STRING);
            if (recipeId != null) {
                if (clickType.isRightClick()) {
                    craftingManager.unregisterRecipe(recipeId);
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openAdminMenu(player), 2L);
                } else {
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> openRecipeEditor(player, recipeId, null), 2L);
                }
            }
        }
    }

    // ========== RECIPE EDITOR ==========
    public void openRecipeEditor(Player player, String recipeId, ItemStack initialResult) {
        CraftingRecipe existing = craftingManager.getRecipe(recipeId);
        RecipeEditSession session = new RecipeEditSession(recipeId);

        if (existing != null) {
            session.setResult(existing.getResult().clone());
            session.setMaterials(new LinkedHashMap<>(existing.getMaterials()));
            session.setCraftingTime(existing.getCraftingTime());
            session.setCategory(existing.getCategory());
            session.setExperienceReward(existing.getExperienceReward());
        } else if (initialResult != null) {
            session.setResult(initialResult.clone());
        }

        editSessions.put(player.getUniqueId(), session);
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(ColorUtils.colorize("&c⚙ &4Recipe Editor &c⚙")));
        openEditors.put(player.getUniqueId(), inv);
        refreshEditorInventory(player, session, inv);
        player.openInventory(inv);
    }

    private void refreshEditorInventory(Player player, RecipeEditSession session, Inventory inv) {
        inv.clear();

        // Result slot
        if (session.getResult() != null) {
            inv.setItem(RESULT_SLOT, session.getResult().clone());
        } else {
            inv.setItem(RESULT_SLOT, createItem(Material.BARRIER, "&cDrop result item here"));
        }

        // Material slots
        int i = 0;
        for (Map.Entry<Material, Integer> entry : session.getMaterials().entrySet()) {
            if (i >= MATERIAL_SLOTS.length) break;
            Material mat = entry.getKey();
            int amount = entry.getValue();
            ItemStack matItem = new ItemStack(mat, Math.min(amount, 64));
            ItemMeta meta = matItem.getItemMeta();
            meta.displayName(Component.text(ColorUtils.colorize("&e" + formatMaterialName(mat))));
            meta.lore(Arrays.asList(
                    Component.text(ColorUtils.colorize("&7Amount: &f" + amount)),
                    Component.text(ColorUtils.colorize("&a▶ Click to adjust amount")),
                    Component.text(ColorUtils.colorize("&c▶ Right-click to remove"))
            ));
            matItem.setItemMeta(meta);
            inv.setItem(MATERIAL_SLOTS[i], matItem);
            i++;
        }

        // Control buttons
        inv.setItem(TIME_SLOT, createTimeButton(session));
        inv.setItem(XP_SLOT, createXPButton(session));
        inv.setItem(CATEGORY_SLOT, createCategoryButton(session));
        inv.setItem(SAVE_SLOT, createItem(Material.LIME_DYE, "&a✔ Save Recipe"));
        inv.setItem(DELETE_SLOT, createItem(Material.REDSTONE_BLOCK, "&c✖ Delete Recipe",
                "&7Right-click to confirm"));
        inv.setItem(CANCEL_SLOT, createItem(Material.RED_DYE, "&c✖ Cancel"));

        ItemStack filler = createFiller();
        for (int j = 0; j < 54; j++) {
            if (inv.getItem(j) == null) inv.setItem(j, filler);
        }
    }

    public void handleEditorClick(Player player, int slot, ClickType clickType,
                                  ItemStack cursor, ItemStack current) {
        RecipeEditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;

        // Result slot
        if (slot == RESULT_SLOT) {
            if (cursor.getType() != Material.AIR) {
                session.setResult(cursor.clone());
                player.setItemOnCursor(null);
                refreshEditor(player, session);
            } else if (current != null && current.getType() != Material.AIR
                    && current.getType() != Material.BARRIER) {
                player.setItemOnCursor(current.clone());
                session.setResult(null);
                refreshEditor(player, session);
            }
            return;
        }

        // Material slots
        for (int i = 0; i < MATERIAL_SLOTS.length; i++) {
            if (slot == MATERIAL_SLOTS[i]) {
                handleMaterialSlot(player, session, i, clickType, cursor, current);
                return;
            }
        }

        // Buttons
        if (slot == TIME_SLOT) {
            openTimeEditor(player, session);
        } else if (slot == XP_SLOT) {
            openXPEditor(player, session);
        } else if (slot == CATEGORY_SLOT) {
            cycleCategory(player, session);
        } else if (slot == SAVE_SLOT) {
            saveRecipe(player, session);
        } else if (slot == DELETE_SLOT) {
            if (clickType.isRightClick()) {
                deleteRecipe(player, session);
            }
        } else if (slot == CANCEL_SLOT) {
            cancelEditing(player);
        }
    }

    private void handleMaterialSlot(Player player, RecipeEditSession session, int index,
                                    ClickType clickType, ItemStack cursor, ItemStack current) {
        List<Material> mats = new ArrayList<>(session.getMaterials().keySet());
        if (index < mats.size()) {
            Material mat = mats.get(index);
            if (clickType.isRightClick()) {
                session.removeMaterial(mat);
                refreshEditor(player, session);
            } else if (clickType.isLeftClick()) {
                if (cursor.getType() != Material.AIR) {
                    session.removeMaterial(mat);
                    session.addMaterial(cursor.getType(), cursor.getAmount());
                    player.setItemOnCursor(null);
                    refreshEditor(player, session);
                } else {
                    openMaterialAmountEditor(player, session, mat);
                }
            }
        } else {
            if (cursor.getType() != Material.AIR) {
                session.addMaterial(cursor.getType(), cursor.getAmount());
                player.setItemOnCursor(null);
                refreshEditor(player, session);
            }
        }
    }

    private void cycleCategory(Player player, RecipeEditSession session) {
        String[] cats = {"Weapons", "Armor", "Tools", "Items", "Food", "Blocks", "Misc"};
        String cur = session.getCategory();
        int idx = Arrays.asList(cats).indexOf(cur);
        idx = (idx + 1) % cats.length;
        session.setCategory(cats[idx]);
        refreshEditor(player, session);
    }

    private void saveRecipe(Player player, RecipeEditSession session) {
        if (session.getResult() == null) {
            player.sendMessage(ColorUtils.colorize("&cSet a result item first!"));
            return;
        }
        if (session.getMaterials().isEmpty()) {
            player.sendMessage(ColorUtils.colorize("&cAdd at least one material!"));
            return;
        }
        CraftingRecipe recipe = new CraftingRecipe(
                session.getRecipeId(), session.getResult(), session.getMaterials(),
                session.getCraftingTime(), session.getCategory(),
                new ArrayList<>(), false, session.getExperienceReward()
        );
        craftingManager.registerRecipe(recipe);
        editSessions.remove(player.getUniqueId());
        openEditors.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ColorUtils.colorize("&a✔ Recipe saved!"));
    }

    private void deleteRecipe(Player player, RecipeEditSession session) {
        craftingManager.unregisterRecipe(session.getRecipeId());
        editSessions.remove(player.getUniqueId());
        openEditors.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ColorUtils.colorize("&cRecipe deleted."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> openAdminMenu(player), 2L);
    }

    private void cancelEditing(Player player) {
        editSessions.remove(player.getUniqueId());
        openEditors.remove(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> openAdminMenu(player), 2L);
    }

    private void refreshEditor(Player player, RecipeEditSession session) {
        Inventory inv = openEditors.get(player.getUniqueId());
        if (inv != null) {
            refreshEditorInventory(player, session, inv);
        }
    }

    // ========== SUB EDITORS ==========
    private void openTimeEditor(Player player, RecipeEditSession session) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(ColorUtils.colorize("&e⏰ Set Crafting Time")));

        inv.setItem(10, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+10s"));
        inv.setItem(11, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+60s"));
        inv.setItem(12, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+30m"));
        inv.setItem(13, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+1h"));
        inv.setItem(14, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-10s"));
        inv.setItem(15, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-60s"));
        inv.setItem(16, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-30m"));
        inv.setItem(17, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-1h"));
        inv.setItem(22, createItem(Material.CLOCK,
                "&eCurrent: " + formatTime(session.getCraftingTime())));
        inv.setItem(26, createItem(Material.ARROW, "&a← Back"));

        player.openInventory(inv);
        player.setMetadata("editType", new FixedMetadataValue(plugin, "time"));
    }

    private void openXPEditor(Player player, RecipeEditSession session) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(ColorUtils.colorize("&a⭐ Set XP Reward")));

        inv.setItem(10, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+250 XP"));
        inv.setItem(11, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+1k XP"));
        inv.setItem(12, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+5k XP"));
        inv.setItem(13, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+10k XP"));
        inv.setItem(14, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-250 XP"));
        inv.setItem(15, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-1k XP"));
        inv.setItem(16, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-5k XP"));
        inv.setItem(22, createItem(Material.EXPERIENCE_BOTTLE,
                "&aCurrent: " + session.getExperienceReward() + " XP"));
        inv.setItem(26, createItem(Material.ARROW, "&a← Back"));

        player.openInventory(inv);
        player.setMetadata("editType", new FixedMetadataValue(plugin, "xp"));
    }

    private void openMaterialAmountEditor(Player player, RecipeEditSession session, Material mat) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(ColorUtils.colorize("&eSet Amount for " + formatMaterialName(mat))));

        int current = session.getMaterials().get(mat);
        inv.setItem(10, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+1"));
        inv.setItem(11, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+4"));
        inv.setItem(12, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+16"));
        inv.setItem(13, makeSubButton(Material.LIME_STAINED_GLASS_PANE, "&a+64"));
        inv.setItem(14, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-1"));
        inv.setItem(15, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-4"));
        inv.setItem(16, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-16"));
        inv.setItem(17, makeSubButton(Material.RED_STAINED_GLASS_PANE, "&c-64"));

        ItemStack display = new ItemStack(mat, Math.min(current, 64));
        ItemMeta meta = display.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize("&eCurrent amount: " + current)));
        display.setItemMeta(meta);
        inv.setItem(22, display);
        inv.setItem(26, createItem(Material.ARROW, "&a← Back"));

        player.openInventory(inv);
        player.setMetadata("editType", new FixedMetadataValue(plugin, "material"));
        player.setMetadata("editMaterial", new FixedMetadataValue(plugin, mat.name()));
    }

    public void handleSubEditorClick(Player player, String title, int slot, ClickType clickType) {
        RecipeEditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;

        if (title.contains("Set Crafting Time")) {
            handleTimeEditorClick(player, slot);
        } else if (title.contains("Set XP Reward")) {
            handleXPEditorClick(player, slot);
        } else if (title.contains("Set Amount for")) {
            handleMaterialAmountEditorClick(player, slot);
        }
    }

    private void handleTimeEditorClick(Player player, int slot) {
        RecipeEditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;

        long[] deltas = {10, 60, 1800, 3600, -10, -60, -1800, -3600};
        int[] slots = {10,11,12,13,14,15,16,17};

        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                session.setCraftingTime(Math.max(1, session.getCraftingTime() + deltas[i]));
                openTimeEditor(player, session);   // refresh the sub-editor in place
                return;
            }
        }
        if (slot == 26) returnToEditor(player);   // Back button only
    }

    private void handleXPEditorClick(Player player, int slot) {
        RecipeEditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;

        int[] deltas = {250, 1000, 5000, 10000, -250, -1000, -5000};
        int[] slots = {10,11,12,13,14,15,16};

        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                session.setExperienceReward(Math.max(0, session.getExperienceReward() + deltas[i]));
                openXPEditor(player, session);   // refresh in place
                return;
            }
        }
        if (slot == 26) returnToEditor(player);
    }

    private void handleMaterialAmountEditorClick(Player player, int slot) {
        RecipeEditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;
        if (!player.hasMetadata("editMaterial")) return;

        String matName = player.getMetadata("editMaterial").get(0).asString();
        Material mat = Material.getMaterial(matName);
        if (mat == null) return;

        int[] deltas = {1, 4, 16, 64, -1, -4, -16, -64};
        int[] slots = {10,11,12,13,14,15,16,17};

        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                int cur = session.getMaterials().getOrDefault(mat, 0);
                int newAmount = Math.max(0, cur + deltas[i]);
                if (newAmount <= 0) {
                    session.removeMaterial(mat);
                } else {
                    session.addMaterial(mat, newAmount);
                }
                openMaterialAmountEditor(player, session, mat);   // refresh in place
                return;
            }
        }
        if (slot == 26) returnToEditor(player);
    }

    private void returnToEditor(Player player) {
        player.closeInventory();
        player.removeMetadata("editType", plugin);
        player.removeMetadata("editMaterial", plugin);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            RecipeEditSession session = editSessions.get(player.getUniqueId());
            Inventory inv = openEditors.get(player.getUniqueId());
            if (session != null && inv != null) {
                refreshEditorInventory(player, session, inv);
                player.openInventory(inv);
            }
        }, 2L);
    }

    // ========== CUSTOM ITEMS MENU ==========
    public void openCustomItemsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text(ColorUtils.colorize("&d✦ &5&lCustom Items Manager &d✦")));

        Collection<CustomItem> items = customItemManager.getAllItems();
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};

        int i = 0;
        for (CustomItem item : items) {
            if (i >= slots.length) break;
            ItemStack display = item.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(ColorUtils.colorize("&7&m-------------------")));
            lore.add(Component.text(ColorUtils.colorize("&eID: &f" + item.getId())));
            lore.add(Component.text(ColorUtils.colorize("&eCategory: &f" + item.getCategory())));
            if (!item.getTags().isEmpty()) {
                lore.add(Component.text(ColorUtils.colorize("&eTags: &f" + String.join(", ", item.getTags()))));
            }
            lore.add(Component.text(ColorUtils.colorize("&a▶ Click to edit")));
            lore.add(Component.text(ColorUtils.colorize("&c▶ Right-click to delete")));
            lore.add(Component.text(ColorUtils.colorize("&7&m-------------------")));
            meta.lore(lore);
            display.setItemMeta(meta);
            gui.setItem(slots[i], display);
            i++;
        }

        gui.setItem(49, createItem(Material.EMERALD, "&a✦ &6&lCreate New Item &a✦",
                "&7Click with item in hand"));
        gui.setItem(45, createItem(Material.ARROW, "&a← Back"));

        ItemStack filler = createFiller();
        for (int j = 0; j < 54; j++) {
            if (gui.getItem(j) == null) gui.setItem(j, filler);
        }
        player.openInventory(gui);
    }

    public void handleCustomItemsClick(Player player, int slot, ClickType clickType, ItemStack clicked) {
        if (slot == 45) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.performCommand("craft"), 2L);
            return;
        }
        if (slot == 49) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                player.sendMessage(ColorUtils.colorize("&cHold an item in your hand!"));
                return;
            }
            String id = "custom_" + System.currentTimeMillis();
            CustomItem customItem = new CustomItem(id, hand.clone(), "Custom Item", "Misc", new ArrayList<>());
            customItemManager.saveItem(id, customItem);
            player.sendMessage(ColorUtils.colorize("&aItem saved as custom item!"));
            openCustomItemsMenu(player);
            return;
        }

        if (clicked != null && clicked.hasItemMeta() && clicked.getType() != Material.BLACK_STAINED_GLASS_PANE) {
            for (CustomItem item : customItemManager.getAllItems()) {
                if (clicked.getItemMeta().getDisplayName().equals(item.getItem().getItemMeta().getDisplayName())) {
                    if (clickType.isRightClick()) {
                        customItemManager.removeItem(item.getId());
                        openCustomItemsMenu(player);
                    } else {
                        player.sendMessage(ColorUtils.colorize("&eEditing custom items not yet available."));
                    }
                    return;
                }
            }
        }
    }

    // ========== HELPERS ==========
    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize(name)));
        if (lore.length > 0) {
            List<Component> loreList = new ArrayList<>();
            for (String line : lore) loreList.add(Component.text(ColorUtils.colorize(line)));
            meta.lore(loreList);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTimeButton(RecipeEditSession session) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize("&e⏰ Crafting Time: &f" + formatTime(session.getCraftingTime()))));
        meta.lore(Arrays.asList(
                Component.text(ColorUtils.colorize("&7&m-------------------")),
                Component.text(ColorUtils.colorize("&a▶ Left-click to change"))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createXPButton(RecipeEditSession session) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize("&a⭐ XP Reward: &f" + session.getExperienceReward())));
        meta.lore(Arrays.asList(
                Component.text(ColorUtils.colorize("&7&m-------------------")),
                Component.text(ColorUtils.colorize("&a▶ Left-click to change"))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCategoryButton(RecipeEditSession session) {
        ItemStack item = new ItemStack(Material.BOOKSHELF);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize("&d📚 Category: &f" + session.getCategory())));
        meta.lore(Arrays.asList(
                Component.text(ColorUtils.colorize("&7&m-------------------")),
                Component.text(ColorUtils.colorize("&a▶ Click to cycle"))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeSubButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize(name)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFiller() {
        Material mat = Material.getMaterial(config.getString("crafting.gui.filler.material", "BLACK_STAINED_GLASS_PANE"));
        if (mat == null) mat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = new ItemStack(mat);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        return filler;
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private String formatMaterialName(Material mat) {
        return mat.name().replace("_", " ").toLowerCase();
    }
}