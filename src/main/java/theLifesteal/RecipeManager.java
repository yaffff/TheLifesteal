package theLifesteal;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;


public class RecipeManager {

    private final TheLifesteal plugin;
    private final HeartManager heartManager;

    public RecipeManager(TheLifesteal plugin) {
        // Store plugin reference as TheLifesteal directly instead of JavaPlugin
        this.plugin = plugin;
        // Now we can directly access getHeartManager()
        this.heartManager = plugin.getHeartManager();
    }

    public void registerRecipes() {
        // Register a crafting recipe for hearts
        NamespacedKey key = new NamespacedKey(plugin, "heart_crafting_recipe");

        // Check if recipe already exists to avoid duplicates
        if (Bukkit.getRecipe(key) != null) {
            return;
        }

        ShapedRecipe recipe = new ShapedRecipe(key, heartManager.createHeartItem(1));
        recipe.setCategory(CraftingBookCategory.MISC);

        recipe.shape("GGG", "GNG", "GGG");
        recipe.setIngredient('G', Material.GOLD_BLOCK);
        recipe.setIngredient('N', Material.NETHER_STAR);

        Bukkit.addRecipe(recipe);

        plugin.getLogger().info("§aCustom heart crafting recipe registered!");

        // Optional: Register additional recipes
        registerExtraRecipes();
    }

    private void registerExtraRecipes() {
        // Example: 4 hearts from emerald blocks
        NamespacedKey emeraldKey = new NamespacedKey(plugin, "emerald_heart_recipe");

        if (Bukkit.getRecipe(emeraldKey) != null) {
            return;
        }

        ShapedRecipe emeraldRecipe = new ShapedRecipe(emeraldKey, heartManager.createHeartItem(2));
        emeraldRecipe.setCategory(CraftingBookCategory.MISC);

        emeraldRecipe.shape("EEE", "EDE", "EEE");
        emeraldRecipe.setIngredient('E', Material.EMERALD_BLOCK);
        emeraldRecipe.setIngredient('D', Material.DIAMOND);

        Bukkit.addRecipe(emeraldRecipe);
    }
}