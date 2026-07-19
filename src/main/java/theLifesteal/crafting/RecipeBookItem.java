package theLifesteal.crafting;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import theLifesteal.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class RecipeBookItem {

    private final NamespacedKey bookKey;
    private final FileConfiguration config;

    // Configurable settings
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final boolean glow;
    private final int customModelData;

    public RecipeBookItem(NamespacedKey bookKey, FileConfiguration config) {
        this.bookKey = bookKey;
        this.config = config;

        // Load item settings from config - FIXED: Use a temporary variable
        String materialName = config.getString("recipe-book.item.material", "KNOWLEDGE_BOOK");
        Material tempMaterial;
        try {
            tempMaterial = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            tempMaterial = Material.KNOWLEDGE_BOOK;
        }
        this.material = tempMaterial;

        this.displayName = config.getString("recipe-book.item.name", "&6✦ &e&lCrafting Recipe Book &6✦");
        this.lore = config.getStringList("recipe-book.item.lore");
        this.glow = config.getBoolean("recipe-book.item.glow", true);
        this.customModelData = config.getInt("recipe-book.item.custom-model-data", 0);
    }

    public ItemStack createRecipeBook() {
        ItemStack book = new ItemStack(material);
        ItemMeta meta = book.getItemMeta();

        meta.displayName(Component.text(ColorUtils.colorize(displayName)));

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(Component.text(ColorUtils.colorize(line)));
        }
        meta.lore(loreComponents);

        // Make it glow if enabled
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        }

        // Set custom model data if applicable
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        // Mark it as the recipe book
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BOOLEAN, true);

        book.setItemMeta(meta);
        return book;
    }

    public boolean isRecipeBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(bookKey, PersistentDataType.BOOLEAN);
    }

    public int getSlot() {
        return config.getInt("recipe-book.slot", 8);
    }

    public boolean giveOnJoin() {
        return config.getBoolean("recipe-book.give-on-join", true);
    }

    public boolean giveOnRespawn() {
        return config.getBoolean("recipe-book.give-on-respawn", true);
    }
}