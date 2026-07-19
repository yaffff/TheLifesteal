package theLifesteal.crafting;

import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class CustomItem {

    private final String id;
    private final ItemStack item;
    private final String name;
    private final String category;
    private final List<String> tags;

    public CustomItem(String id, ItemStack item, String name, String category, List<String> tags) {
        this.id = id;
        this.item = item.clone();
        this.name = name;
        this.category = category;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public String getId() { return id; }
    public ItemStack getItem() { return item.clone(); }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public List<String> getTags() { return tags; }

    public boolean isStackable() {
        return item.getMaxStackSize() > 1;
    }
}