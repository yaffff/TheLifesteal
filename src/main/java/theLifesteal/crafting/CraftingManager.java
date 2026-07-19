package theLifesteal.crafting;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CraftingManager {

    private final JavaPlugin plugin;
    private final Map<String, CraftingRecipe> recipes;
    private final Map<UUID, List<CraftingProcess>> activeProcesses;
    private final int maxConcurrentCrafts;
    private File dataFile;
    private long lastSaveTime;
    private static final long SAVE_INTERVAL = 30000; // Save every 30 seconds instead of instantly
    private boolean saveScheduled = false;

    public CraftingManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.recipes = new LinkedHashMap<>();
        this.activeProcesses = new ConcurrentHashMap<>(); // Thread-safe for async access
        this.maxConcurrentCrafts = plugin.getConfig().getInt("crafting.max-concurrent-crafts", 5);
        this.dataFile = new File(plugin.getDataFolder(), "crafting_data.yml");
        this.lastSaveTime = System.currentTimeMillis();

        registerDefaultRecipes();
        loadCustomRecipes();

        // Periodic cleanup task (runs every 5 minutes)
        startCleanupTask();
    }

    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int removed = 0;
            long now = System.currentTimeMillis();

            Iterator<Map.Entry<UUID, List<CraftingProcess>>> iterator = activeProcesses.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, List<CraftingProcess>> entry = iterator.next();
                List<CraftingProcess> processes = entry.getValue();

                // Use iterator to remove while counting
                Iterator<CraftingProcess> processIterator = processes.iterator();
                while (processIterator.hasNext()) {
                    CraftingProcess process = processIterator.next();
                    if (process.isClaimed() && (now - process.getEndTime()) > 300000) {
                        processIterator.remove();
                        removed++;
                    }
                }

                // Remove empty entries
                if (processes.isEmpty()) {
                    iterator.remove();
                }
            }

            if (removed > 0) {
                plugin.getLogger().info("Cleaned up " + removed + " old crafting processes");
                forceSave();
            }
        }, 6000L, 6000L); // Run every 5 minutes (6000 ticks = 300 seconds)
    }

    private void registerDefaultRecipes() {
        // Only register if recipes are empty (first time setup)
        if (recipes.isEmpty()) {
            // Default recipes can go here
            // These will only load if no recipes exist yet
        }
    }

    private void loadCustomRecipes() {
        File recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        if (!recipesFile.exists()) {
            // Don't try to save from jar, just skip loading
            plugin.getLogger().info("No recipes.yml found, skipping custom recipe loading.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(recipesFile);

        for (String key : config.getKeys(false)) {
            try {
                ItemStack result = config.getItemStack(key + ".result");
                if (result == null) continue;

                Map<Material, Integer> materials = new LinkedHashMap<>();
                List<String> materialList = config.getStringList(key + ".materials");

                for (String matStr : materialList) {
                    String[] parts = matStr.split(":");
                    if (parts.length == 2) {
                        Material mat = Material.getMaterial(parts[0]);
                        int amount = Integer.parseInt(parts[1]);
                        if (mat != null) {
                            materials.put(mat, amount);
                        }
                    }
                }

                long time = config.getLong(key + ".time", 60);
                String category = config.getString(key + ".category", "Misc");
                List<String> description = config.getStringList(key + ".description");
                boolean shapeless = config.getBoolean(key + ".shapeless", false);
                int xp = config.getInt(key + ".xp", 0);

                CraftingRecipe recipe = new CraftingRecipe(key, result, materials, time,
                        category, description, shapeless, xp);
                recipes.put(key, recipe);

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load recipe: " + key + " - " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + recipes.size() + " custom recipes");
    }

    public void saveRecipes() {
        File recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, CraftingRecipe> entry : recipes.entrySet()) {
            String key = entry.getKey();
            CraftingRecipe recipe = entry.getValue();

            config.set(key + ".result", recipe.getResult());

            List<String> materialList = new ArrayList<>();
            for (Map.Entry<Material, Integer> mat : recipe.getMaterials().entrySet()) {
                materialList.add(mat.getKey().name() + ":" + mat.getValue());
            }
            config.set(key + ".materials", materialList);

            config.set(key + ".time", recipe.getCraftingTime());
            config.set(key + ".category", recipe.getCategory());
            config.set(key + ".description", recipe.getDescription());
            config.set(key + ".shapeless", recipe.isShapeless());
            config.set(key + ".xp", recipe.getExperienceReward());
        }

        try {
            config.save(recipesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save recipes: " + e.getMessage());
        }
    }

    public boolean startCrafting(Player player, String recipeId) {
        CraftingRecipe recipe = recipes.get(recipeId);
        if (recipe == null) return false;

        List<CraftingProcess> processes = activeProcesses.computeIfAbsent(
                player.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>()));

        if (processes.size() >= maxConcurrentCrafts) {
            player.sendMessage("§cYou have reached the maximum concurrent crafts! (" + maxConcurrentCrafts + ")");
            return false;
        }

        // Check materials efficiently
        if (!hasRequiredMaterials(player, recipe)) {
            return false;
        }

        // Remove materials
        removeMaterials(player, recipe);

        CraftingProcess process = new CraftingProcess(player.getUniqueId(), recipe);
        processes.add(process);

        // Schedule async save instead of saving immediately
        scheduleSave();

        return true;
    }

    private boolean hasRequiredMaterials(Player player, CraftingRecipe recipe) {
        Map<Material, Integer> required = recipe.getMaterials();
        Map<Material, Integer> available = new HashMap<>();

        // Single pass through inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && required.containsKey(item.getType())) {
                available.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }

        // Check if we have enough
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                player.sendMessage("§cMissing: " + formatMaterialName(entry.getKey()) +
                        " x" + entry.getValue());
                return false;
            }
        }

        return true;
    }

    private void removeMaterials(Player player, CraftingRecipe recipe) {
        Map<Material, Integer> required = recipe.getMaterials();
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            player.getInventory().removeItem(new ItemStack(entry.getKey(), entry.getValue()));
        }
    }

    public boolean cancelCrafting(Player player, int processIndex) {
        List<CraftingProcess> processes = getPlayerProcesses(player.getUniqueId());

        if (processIndex >= processes.size() || processIndex < 0) {
            return false;
        }

        CraftingProcess process = processes.get(processIndex);
        if (process.isClaimed()) {
            return false;
        }

        // Return materials
        Map<Material, Integer> materials = process.getRecipe().getMaterials();
        for (Map.Entry<Material, Integer> entry : materials.entrySet()) {
            ItemStack refund = new ItemStack(entry.getKey(), entry.getValue());
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(refund);

            // Drop items that don't fit in inventory
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        processes.remove(processIndex);
        if (processes.isEmpty()) {
            activeProcesses.remove(player.getUniqueId());
        }

        scheduleSave();
        return true;
    }

    public boolean claimItem(Player player, int processIndex) {
        List<CraftingProcess> processes = getPlayerProcesses(player.getUniqueId());

        if (processIndex >= processes.size() || processIndex < 0) {
            return false;
        }

        CraftingProcess process = processes.get(processIndex);
        if (!process.isCompleted()) {
            player.sendMessage("§cThis item is not ready yet!");
            return false;
        }

        if (process.isClaimed()) {
            player.sendMessage("§cThis item has already been claimed!");
            return false;
        }

        ItemStack result = process.getRecipe().getResult();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(result);

        if (!leftover.isEmpty()) {
            player.sendMessage("§cYour inventory is full! Make space and try again.");
            return false;
        }

        process.setClaimed(true);
        player.giveExp(process.getRecipe().getExperienceReward());

        // Play a nice sound
        player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        scheduleSave();
        return true;
    }

    /**
     * Schedules a save instead of saving immediately
     * Prevents I/O spam when many players craft at once
     */
    private void scheduleSave() {
        if (saveScheduled) {
            return; // Already scheduled
        }

        saveScheduled = true;

        // Save after a short delay to batch multiple changes
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            saveCraftingProcesses();
            saveScheduled = false;
        }, 40L); // 2 second delay
    }

    public void saveCraftingProcesses() {
        // Don't save if nothing changed recently (optional optimization)
        if (System.currentTimeMillis() - lastSaveTime < 5000 && lastSaveTime > 0) {
            return;
        }

        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, List<CraftingProcess>> entry : activeProcesses.entrySet()) {
            List<CraftingProcess> processes = entry.getValue();
            if (processes.isEmpty()) continue;

            List<Map<String, Object>> processList = new ArrayList<>(processes.size());

            for (CraftingProcess process : processes) {
                // Skip already claimed processes older than 10 minutes
                if (process.isClaimed() &&
                        System.currentTimeMillis() - process.getEndTime() > 600000) {
                    continue;
                }

                Map<String, Object> processData = new HashMap<>(4);
                processData.put("recipeId", process.getRecipe().getId());
                processData.put("startTime", process.getStartTime());
                processData.put("endTime", process.getEndTime());
                processData.put("claimed", process.isClaimed());
                processList.add(processData);
            }

            if (!processList.isEmpty()) {
                config.set(entry.getKey().toString(), processList);
            }
        }

        try {
            config.save(dataFile);
            lastSaveTime = System.currentTimeMillis();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save crafting processes: " + e.getMessage());
        }
    }

    public void loadCraftingProcesses() {
        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        int loaded = 0;
        long now = System.currentTimeMillis();

        for (String uuidStr : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<Map<String, Object>> processList = (List<Map<String, Object>>) config.getList(uuidStr);

                if (processList != null) {
                    List<CraftingProcess> processes = Collections.synchronizedList(new ArrayList<>());

                    for (Map<String, Object> data : processList) {
                        String recipeId = (String) data.get("recipeId");
                        CraftingRecipe recipe = recipes.get(recipeId);

                        if (recipe != null) {
                            long startTime = ((Number) data.get("startTime")).longValue();
                            long endTime = ((Number) data.get("endTime")).longValue();
                            boolean claimed = (Boolean) data.get("claimed");

                            // Don't load claimed processes older than 30 minutes
                            if (claimed && (now - endTime) > 1800000) {
                                continue;
                            }

                            CraftingProcess process = new CraftingProcess(uuid, recipe, startTime, endTime);
                            if (claimed) {
                                process.setClaimed(true);
                            }
                            processes.add(process);
                            loaded++;
                        }
                    }

                    if (!processes.isEmpty()) {
                        activeProcesses.put(uuid, processes);
                    }
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in crafting data: " + uuidStr);
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " crafting processes from disk");
    }

    /**
     * Force save - call this on plugin disable
     */
    public void forceSave() {
        saveScheduled = false;
        saveCraftingProcesses();
    }

    // Thread-safe getter for player processes
    public List<CraftingProcess> getPlayerProcesses(UUID playerUUID) {
        return activeProcesses.getOrDefault(playerUUID, Collections.emptyList());
    }

    /**
     * Gets total number of active processes across all players
     * Useful for monitoring
     */
    public int getTotalActiveProcesses() {
        return activeProcesses.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    public void registerRecipe(CraftingRecipe recipe) {
        recipes.put(recipe.getId(), recipe);
        saveRecipes();
    }

    public void unregisterRecipe(String id) {
        recipes.remove(id);
        saveRecipes();
    }

    public CraftingRecipe getRecipe(String id) {
        return recipes.get(id);
    }

    public Collection<CraftingRecipe> getAllRecipes() {
        return recipes.values();
    }

    public List<CraftingRecipe> getRecipesByCategory(String category) {
        return recipes.values().stream()
                .filter(r -> r.getCategory().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    public List<String> getCategories() {
        return recipes.values().stream()
                .map(CraftingRecipe::getCategory)
                .distinct()
                .collect(Collectors.toList());
    }

    private String formatMaterialName(Material material) {
        return material.name().replace("_", " ").toLowerCase();
    }

}