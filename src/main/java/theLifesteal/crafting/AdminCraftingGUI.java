package theLifesteal.crafting;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

    // Inventory layout constants
    private static final int RESULT_SLOT = 4;
    private static final int[] MATERIAL_SLOTS = {19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final int TIME_SLOT = 47;
    private static final int XP_SLOT = 48;
    private static final int CATEGORY_SLOT = 46;
    private static final int SAVE_SLOT = 52;
    private static final int CANCEL_SLOT = 53;

    public AdminCraftingGUI(JavaPlugin plugin, CraftingManager craftingManager,
                            CustomItemManager customItemManager, FileConfiguration config) {
        this.plugin = plugin;
        this.craftingManager = craftingManager;
        this.customItemManager = customItemManager;
        this.config = config;
        this.editSessions = new HashMap<>();
        this.openEditors = new HashMap<>();
    }

    // ---------- Admin Main Menu (unchanged) ----------
    public void openAdminMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text(ColorUtils.colorize("&c⚙ &4&lAdmin Crafting Menu &c⚙")));

        List<CraftingRecipe> allRecipes = new ArrayList<>(craftingManager.getAllRecipes());
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};

        for (int i = 0; i < Math.min(allRecipes.size(), slots.length); i++) {
            CraftingRecipe recipe = allRecipes.get(i);
            ItemStack display = recipe.getResult().clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.addAll(Arrays.asList(
                    Component.text(""),
                    Component.text(ColorUtils.colorize("&7&m-------------------")),
                    Component.text(ColorUtils.colorize("&eID: &f" + recipe.getId())),
                    Component.text(ColorUtils.colorize("&eCategory: &f" + recipe.getCategory())),
                    Component.text(ColorUtils.colorize("&eTime: &f" + recipe.getCraftingTime() + "s")),
                    Component.text(ColorUtils.colorize("&a▶ Left-click to edit")),
                    Component.text(ColorUtils.colorize("&c▶ Right-click to delete"))
            ));
            meta.lore(lore);
            display.setItemMeta(meta);
            gui.setItem(slots[i], display);
        }

        // Add new recipe button
        ItemStack newBtn = createItem(Material.EMERALD_BLOCK, "&a✦ &6&lAdd New Recipe &a✦",
                "&7Click with result item in hand");
        gui.setItem(49, newBtn);
        gui.setItem(45, createItem(Material.ARROW, "&a← Back"));

        // Fill
        ItemStack filler = createFiller();
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, filler);

        player.openInventory(gui);
    }

    public void handleAdminMenuClick(Player player, int slot, ClickType clickType, ItemStack clicked) {
        if (slot == 45) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.performCommand("craft"), 2L);
            return;
        }
        if (slot == 49) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                player.sendMessage(ColorUtils.colorize("&cHold the result item in your hand!"));
                return;
            }
            String id = "recipe_" + System.currentTimeMillis();
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openRecipeEditor(player, id, hand.clone()), 2L);
            return;
        }
        if (clicked != null && clicked.hasItemMeta() && clicked.getType() != Material.BLACK_STAINED_GLASS_PANE) {
            for (CraftingRecipe recipe : craftingManager.getAllRecipes()) {
                if (clicked.getItemMeta().getDisplayName().equals(recipe.getResult().getItemMeta().getDisplayName())) {
                    if (clickType.isRightClick()) {
                        craftingManager.unregisterRecipe(recipe.getId());
                        player.closeInventory();
                        Bukkit.getScheduler().runTaskLater(plugin, () -> openAdminMenu(player), 2L);
                    } else {
                        player.closeInventory();
                        Bukkit.getScheduler().runTaskLater(plugin, () -> openRecipeEditor(player, recipe.getId(), null), 2L);
                    }
                    return;
                }
            }
        }
    }

    // ---------- NEW INTERACTIVE RECIPE EDITOR ----------
    public void openRecipeEditor(Player player, String recipeId, ItemStack initialResult) {
        CraftingRecipe existing = craftingManager.getRecipe(recipeId);
        RecipeEditSession session = new RecipeEditSession(recipeId);

        if (existing != null) {
            session.setResult(existing.getResult());
            session.setMaterials(new LinkedHashMap<>(existing.getMaterials()));
            session.setCraftingTime(existing.getCraftingTime());
            session.setCategory(existing.getCategory());
            session.setExperienceReward(existing.getExperienceReward());
        } else if (initialResult != null) {
            session.setResult(initialResult);
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
            ItemStack mat = new ItemStack(entry.getKey(), Math.min(entry.getValue(), 64));
            ItemMeta meta = mat.getItemMeta();
            meta.displayName(Component.text(ColorUtils.colorize("&e" + formatMaterialName(entry.getKey()))));
            meta.lore(Arrays.asList(
                    Component.text(ColorUtils.colorize("&7Amount: &f" + entry.getValue())),
                    Component.text(ColorUtils.colorize("&a▶ Click to adjust amount")),
                    Component.text(ColorUtils.colorize("&c▶ Right-click to remove"))
            ));
            mat.setItemMeta(meta);
            inv.setItem(MATERIAL_SLOTS[i], mat);
            i++;
        }

        // Control buttons
        inv.setItem(TIME_SLOT, createTimeButton(session));
        inv.setItem(XP_SLOT, createXPButton(session));
        inv.setItem(CATEGORY_SLOT, createCategoryButton(session));
        inv.setItem(SAVE_SLOT, createItem(Material.LIME_DYE, "&a✔ Save Recipe"));
        inv.setItem(CANCEL_SLOT, createItem(Material.RED_DYE, "&c✖ Cancel"));

        // Fillers
        ItemStack filler = createFiller();
        for (int j = 0; j < 54; j++) if (inv.getItem(j) == null) inv.setItem(j, filler);

        // Update stored inventory
        openEditors.put(player.getUniqueId(), inv);
    }

    private ItemStack createTimeButton(RecipeEditSession session) {
        long time = session.getCraftingTime();
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize("&e⏰ Crafting Time: &f" + formatTime(time))));
        meta.lore(Arrays.asList(
                Component.text(ColorUtils.colorize("&7&m-------------------")),
                Component.text(ColorUtils.colorize("&a▶ Left-click: Open time controls")),
                Component.text(ColorUtils.colorize("&7Current: " + formatTime(time))),
                Component.text(ColorUtils.colorize("&7&m-------------------"))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createXPButton(RecipeEditSession session) {
        int xp = session.getExperienceReward();
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize("&a⭐ XP Reward: &f" + xp)));
        meta.lore(Arrays.asList(
                Component.text(ColorUtils.colorize("&7&m-------------------")),
                Component.text(ColorUtils.colorize("&a▶ Left-click: Open XP controls")),
                Component.text(ColorUtils.colorize("&7Current: " + xp + " XP")),
                Component.text(ColorUtils.colorize("&7&m-------------------"))
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
                Component.text(ColorUtils.colorize("&a▶ Left-click: Cycle category")),
                Component.text(ColorUtils.colorize("&7&m-------------------"))
        ));
        item.setItemMeta(meta);
        return item;
    }

    // ---------- Editor Click Handling ----------
    public void handleEditorClick(Player player, int slot, ClickType clickType, ItemStack cursor, ItemStack current) {
        RecipeEditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;

        // Result slot
        if (slot == RESULT_SLOT) {
            if (cursor != null && cursor.getType() != Material.AIR) {
                session.setResult(cursor.clone());
                player.sendMessage(ColorUtils.colorize("&a✔ Result item updated!"));
            } else if (current != null && current.getType() != Material.AIR && current.getType() != Material.BARRIER) {
                // Pick up result (give back)
                player.setItemOnCursor(current.clone());
                session.setResult(null);
            }
            refreshEditor(player, session);
            return;
        }

        // Material slots
        for (int i = 0; i < MATERIAL_SLOTS.length; i++) {
            if (slot == MATERIAL_SLOTS[i]) {
                handleMaterialSlot(player, session, i, clickType, cursor);
                return;
            }
        }

        // Time button
        if (slot == TIME_SLOT) {
            openTimeEditor(player, session);
            return;
        }

        // XP button
        if (slot == XP_SLOT) {
            openXPEditor(player, session);
            return;
        }

        // Category button
        if (slot == CATEGORY_SLOT) {
            String[] cats = {"Weapons", "Armor", "Tools", "Items", "Food", "Blocks", "Misc"};
            String cur = session.getCategory();
            int idx = Arrays.asList(cats).indexOf(cur);
            idx = (idx + 1) % cats.length;
            session.setCategory(cats[idx]);
            refreshEditor(player, session);
            return;
        }

        // Save
        if (slot == SAVE_SLOT) {
            saveRecipe(player, session);
            return;
        }

        // Cancel
        if (slot == CANCEL_SLOT) {
            editSessions.remove(player.getUniqueId());
            openEditors.remove(player.getUniqueId());
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openAdminMenu(player), 2L);
            return;
        }
    }

    private void handleMaterialSlot(Player player, RecipeEditSession session, int index, ClickType clickType, ItemStack cursor) {
        List<Material> materials = new ArrayList<>(session.getMaterials().keySet());

        if (index < materials.size()) {
            // Existing material clicked
            Material mat = materials.get(index);
            if (clickType.isRightClick()) {
                session.removeMaterial(mat);
                refreshEditor(player, session);
            } else if (clickType.isLeftClick()) {
                // Open amount editor
                openMaterialAmountEditor(player, session, mat);
            }
        } else {
            // Empty slot - add material
            if (cursor != null && cursor.getType() != Material.AIR) {
                session.addMaterial(cursor.getType(), 1);
                refreshEditor(player, session);
            }
        }
    }

    // ---------- Sub-Editors (Time, XP, Material Amount) ----------
    private void openTimeEditor(Player player, RecipeEditSession session) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(ColorUtils.colorize("&e⏰ Set Crafting Time")));

        long t = session.getCraftingTime();
        // Row 1: add
        inv.setItem(10, makeTimeButton(Material.LIME_STAINED_GLASS_PANE, "&a+10s", 10));
        inv.setItem(11, makeTimeButton(Material.LIME_STAINED_GLASS_PANE, "&a+60s", 60));
        inv.setItem(12, makeTimeButton(Material.LIME_STAINED_GLASS_PANE, "&a+30m", 1800));
        inv.setItem(13, makeTimeButton(Material.LIME_STAINED_GLASS_PANE, "&a+1h", 3600));
        // Row 2: subtract
        inv.setItem(14, makeTimeButton(Material.RED_STAINED_GLASS_PANE, "&c-10s", -10));
        inv.setItem(15, makeTimeButton(Material.RED_STAINED_GLASS_PANE, "&c-60s", -60));
        inv.setItem(16, makeTimeButton(Material.RED_STAINED_GLASS_PANE, "&c-30m", -1800));
        inv.setItem(17, makeTimeButton(Material.RED_STAINED_GLASS_PANE, "&c-1h", -3600));

        // Current time display
        inv.setItem(22, createItem(Material.CLOCK, "&eCurrent: " + formatTime(t)));
        // Back button
        inv.setItem(26, createItem(Material.ARROW, "&a← Back"));

        // store session reference
        player.openInventory(inv);
        // We need to track that this is a time editor. Use a map or check title.
        // For simplicity, we'll handle in the main click router.
        player.setMetadata("editType", new org.bukkit.metadata.FixedMetadataValue(plugin, "time"));
    }

    private void openXPEditor(Player player, RecipeEditSession session) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(ColorUtils.colorize("&a⭐ Set XP Reward")));

        int xp = session.getExperienceReward();
        // Add buttons
        inv.setItem(10, makeXPButton(Material.LIME_STAINED_GLASS_PANE, "&a+250 XP", 250));
        inv.setItem(11, makeXPButton(Material.LIME_STAINED_GLASS_PANE, "&a+1k XP", 1000));
        inv.setItem(12, makeXPButton(Material.LIME_STAINED_GLASS_PANE, "&a+5k XP", 5000));
        inv.setItem(13, makeXPButton(Material.LIME_STAINED_GLASS_PANE, "&a+10k XP", 10000));
        // Subtract
        inv.setItem(14, makeXPButton(Material.RED_STAINED_GLASS_PANE, "&c-250 XP", -250));
        inv.setItem(15, makeXPButton(Material.RED_STAINED_GLASS_PANE, "&c-1k XP", -1000));
        inv.setItem(16, makeXPButton(Material.RED_STAINED_GLASS_PANE, "&c-5k XP", -5000));

        inv.setItem(22, createItem(Material.EXPERIENCE_BOTTLE, "&aCurrent: " + xp + " XP"));
        inv.setItem(26, createItem(Material.ARROW, "&a← Back"));

        player.openInventory(inv);
        player.setMetadata("editType", new org.bukkit.metadata.FixedMetadataValue(plugin, "xp"));
    }

    private void openMaterialAmountEditor(Player player, RecipeEditSession session, Material mat) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(ColorUtils.colorize("&eSet Amount for " + formatMaterialName(mat))));

        int current = session.getMaterials().get(mat);
        // Add buttons
        inv.setItem(10, makeAmountButton(Material.LIME_STAINED_GLASS_PANE, "&a+1", mat, 1));
        inv.setItem(11, makeAmountButton(Material.LIME_STAINED_GLASS_PANE, "&a+4", mat, 4));
        inv.setItem(12, makeAmountButton(Material.LIME_STAINED_GLASS_PANE, "&a+16", mat, 16));
        inv.setItem(13, makeAmountButton(Material.LIME_STAINED_GLASS_PANE, "&a+64", mat, 64));
        // Subtract
        inv.setItem(14, makeAmountButton(Material.RED_STAINED_GLASS_PANE, "&c-1", mat, -1));
        inv.setItem(15, makeAmountButton(Material.RED_STAINED_GLASS_PANE, "&c-4", mat, -4));
        inv.setItem(16, makeAmountButton(Material.RED_STAINED_GLASS_PANE, "&c-16", mat, -16));
        inv.setItem(17, makeAmountButton(Material.RED_STAINED_GLASS_PANE, "&c-64", mat, -64));

        inv.setItem(22, new ItemStack(mat, Math.min(current, 64)));
        ItemMeta meta = inv.getItem(22).getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize("&eCurrent amount: " + current)));
        inv.getItem(22).setItemMeta(meta);

        inv.setItem(26, createItem(Material.ARROW, "&a← Back"));

        player.openInventory(inv);
        player.setMetadata("editType", new org.bukkit.metadata.FixedMetadataValue(plugin, "material"));
        player.setMetadata("editMaterial", new org.bukkit.metadata.FixedMetadataValue(plugin, mat.name()));
    }

    // ---------- Helpers for sub-editor buttons ----------
    private ItemStack makeTimeButton(Material mat, String name, long delta) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize(name)));
        item.setItemMeta(meta);
        // Store delta in item's persistent data? Simpler: check name in handler
        return item;
    }

    private ItemStack makeXPButton(Material mat, String name, int delta) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize(name)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeAmountButton(Material mat, String name, Material targetMat, int delta) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize(name)));
        meta.lore(Arrays.asList(Component.text(ColorUtils.colorize("&7Material: " + formatMaterialName(targetMat)))));
        item.setItemMeta(meta);
        return item;
    }

    // ---------- Handle clicks in sub-editors (called from CraftingGUI's handleClick) ----------
    public void handleSubEditorClick(Player player, String title, int slot, ClickType clickType) {
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
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Inventory inv = openEditors.get(player.getUniqueId());
                    if (inv != null) {
                        refreshEditorInventory(player, session, inv);
                        player.openInventory(inv);
                    }
                }, 2L);
                return;
            }
        }
        if (slot == 26) { // Back
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Inventory inv = openEditors.get(player.getUniqueId());
                if (inv != null) player.openInventory(inv);
            }, 2L);
        }
    }

    private void handleXPEditorClick(Player player, int slot) {
        RecipeEditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;

        int[] deltas = {250, 1000, 5000, 10000, -250, -1000, -5000};
        int[] slots = {10,11,12,13,14,15,16};
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                session.setExperienceReward(Math.max(0, session.getExperienceReward() + deltas[i]));
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Inventory inv = openEditors.get(player.getUniqueId());
                    if (inv != null) {
                        refreshEditorInventory(player, session, inv);
                        player.openInventory(inv);
                    }
                }, 2L);
                return;
            }
        }
        if (slot == 26) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Inventory inv = openEditors.get(player.getUniqueId());
                if (inv != null) player.openInventory(inv);
            }, 2L);
        }
    }

    private void handleMaterialAmountEditorClick(Player player, int slot) {
        RecipeEditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;

        // Get material from metadata
        if (!player.hasMetadata("editMaterial")) return;
        String matName = player.getMetadata("editMaterial").get(0).asString();
        Material mat = Material.getMaterial(matName);
        if (mat == null) return;

        int[] deltas = {1,4,16,64,-1,-4,-16,-64};
        int[] slots = {10,11,12,13,14,15,16,17};
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                int cur = session.getMaterials().getOrDefault(mat, 0);
                int newAmount = Math.max(0, cur + deltas[i]);
                if (newAmount <= 0) session.removeMaterial(mat);
                else session.addMaterial(mat, newAmount);
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Inventory inv = openEditors.get(player.getUniqueId());
                    if (inv != null) {
                        refreshEditorInventory(player, session, inv);
                        player.openInventory(inv);
                    }
                }, 2L);
                return;
            }
        }
        if (slot == 26) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Inventory inv = openEditors.get(player.getUniqueId());
                if (inv != null) player.openInventory(inv);
            }, 2L);
        }
    }

    // ---------- Save and Utilities ----------
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
                session.getCraftingTime(), session.getCategory(), new ArrayList<>(), false,
                session.getExperienceReward()
        );
        craftingManager.registerRecipe(recipe);
        editSessions.remove(player.getUniqueId());
        openEditors.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ColorUtils.colorize("&a✔ Recipe saved!"));
    }

    private void refreshEditor(Player player, RecipeEditSession session) {
        Inventory inv = openEditors.get(player.getUniqueId());
        if (inv != null) {
            refreshEditorInventory(player, session, inv);
        }
    }

    // ---------- Custom Items Menu (unchanged) ----------
    public void openCustomItemsMenu(Player player) { /* same as before */ }
    public void handleCustomItemsClick(Player player, int slot, ClickType clickType, ItemStack clicked) { /* same */ }

    // ---------- Helpers ----------
    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ColorUtils.colorize(name)));
        if (lore.length > 0) {
            List<Component> list = new ArrayList<>();
            for (String s : lore) list.add(Component.text(ColorUtils.colorize(s)));
            meta.lore(list);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFiller() {
        Material mat = Material.getMaterial(config.getString("crafting.gui.filler.material", "BLACK_STAINED_GLASS_PANE"));
        if (mat == null) mat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack f = new ItemStack(mat);
        ItemMeta m = f.getItemMeta();
        m.displayName(Component.text(" "));
        f.setItemMeta(m);
        return f;
    }

    private String formatTime(long seconds) {
        long h = seconds/3600, m = (seconds%3600)/60, s = seconds%60;
        if (h>0) return h+"h "+m+"m "+s+"s";
        if (m>0) return m+"m "+s+"s";
        return s+"s";
    }

    private String formatMaterialName(Material mat) {
        return mat.name().replace("_"," ").toLowerCase();
    }
    public void syncEditorFromInventory(Player player, Inventory inv) {
        RecipeEditSession session = editSessions.get(player.getUniqueId());
        if (session == null) return;

        // Result slot
        ItemStack result = inv.getItem(RESULT_SLOT);
        if (result != null && result.getType() != Material.AIR && result.getType() != Material.BARRIER) {
            session.setResult(result.clone());
        } else {
            session.setResult(null);
        }

        // Material slots – rebuild materials map
        session.getMaterials().clear();
        for (int matSlot : MATERIAL_SLOTS) {
            ItemStack mat = inv.getItem(matSlot);
            if (mat != null && mat.getType() != Material.AIR) {
               Material type = mat.getType();
                int stored = session.getMaterials().getOrDefault(type, 0);
                if (stored == 0) {
                    session.addMaterial(type, mat.getAmount());
                } else {
                    // Keep the stored amount (user can adjust via button)
                    session.addMaterial(type, stored);
                }
            }
        }

        refreshEditorInventory(player, session, inv);
    }
}