package theLifesteal.crafting;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

public class CraftingRecipe {

    private final String id;
    private final ItemStack result;
    private final Map<Material, Integer> materials;
    private final long craftingTime; // in seconds
    private final String category;
    private final List<String> description;
    private final boolean isShapeless;
    private final int experienceReward;

    public CraftingRecipe(String id, ItemStack result, Map<Material, Integer> materials,
                          long craftingTime, String category, List<String> description,
                          boolean isShapeless, int experienceReward) {
        this.id = id;
        this.result = result.clone();
        this.materials = new HashMap<>(materials);
        this.craftingTime = craftingTime;
        this.category = category;
        this.description = description;
        this.isShapeless = isShapeless;
        this.experienceReward = experienceReward;
    }

    public String getId() { return id; }
    public ItemStack getResult() { return result.clone(); }
    public Map<Material, Integer> getMaterials() { return new HashMap<>(materials); }
    public long getCraftingTime() { return craftingTime; }
    public String getCategory() { return category; }
    public List<String> getDescription() { return description; }
    public boolean isShapeless() { return isShapeless; }
    public int getExperienceReward() { return experienceReward; }

    public boolean canCraft(Map<Material, Integer> playerMaterials) {
        for (Map.Entry<Material, Integer> entry : materials.entrySet()) {
            Material mat = entry.getKey();
            int required = entry.getValue();
            int available = playerMaterials.getOrDefault(mat, 0);
            if (available < required) {
                return false;
            }
        }
        return true;
    }
}