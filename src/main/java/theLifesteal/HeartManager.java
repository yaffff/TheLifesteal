package theLifesteal;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Sound;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

public class HeartManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey heartKey;

    public HeartManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = ((TheLifesteal) plugin).getConfigManager();
        this.heartKey = new NamespacedKey(plugin, "half_heart_item");
    }

    public ItemStack createHeartItem(int amount) {
        ItemStack item = new ItemStack(configManager.getHeartMaterial(), amount);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        // Set display name
        meta.displayName(Component.text(ColorUtils.colorize(configManager.getHeartDisplayName())));

        // Set lore
        List<Component> loreComponents = new ArrayList<>();
        for (String line : configManager.getHeartLore()) {
            loreComponents.add(Component.text(ColorUtils.colorize(line)));
        }
        meta.lore(loreComponents);

        // Add glow effect for 1.21.11
        if (configManager.isHeartGlow()) {
            // In 1.21.11, we can safely use UNBREAKING for the glow effect
            // This enchantment exists and has no visual effect on non-tools
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Set custom model data if applicable
        if (configManager.getHeartCustomModelData() > 0) {
            meta.setCustomModelData(configManager.getHeartCustomModelData());
        }

        // Store identifier
        meta.getPersistentDataContainer().set(heartKey, PersistentDataType.BOOLEAN, true);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isHeartItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (!item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer().has(heartKey, PersistentDataType.BOOLEAN);
    }

    public void applyHeartEffect(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        double newHealth = maxHealth.getBaseValue() + configManager.getHalfHeartValue();
        maxHealth.setBaseValue(newHealth);

        if (configManager.isHeartUseSoundEnabled()) {
            player.playSound(player.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }

        player.sendMessage(ColorUtils.colorize(
                configManager.getMessage("heart-gained")
                        .replace("%amount%", String.valueOf(configManager.getHalfHeartValue()))
        ));
    }

    public void removeHeartOnDeath(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        double currentMax = maxHealth.getBaseValue();
        double newMax = currentMax - configManager.getHeartValue();

        if (newMax < configManager.getMinimumMaxHealth()) {
            newMax = configManager.getMinimumMaxHealth();
        }

        maxHealth.setBaseValue(newMax);

        if (configManager.shouldDropHeartsOnDeath()) {
            ItemStack heartDrop = createHeartItem(1);
            player.getWorld().dropItemNaturally(player.getLocation(), heartDrop);
        }

        player.sendMessage(ColorUtils.colorize(
                configManager.getMessage("heart-dropped")
                        .replace("%amount%", String.valueOf(configManager.getHeartValue()))
        ));
    }

    public boolean canWithdrawHearts(Player player, int amount) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return false;

        double newHealth = maxHealth.getBaseValue() - amount;
        return newHealth >= configManager.getMinimumMaxHealth();
    }

    public void withdrawHearts(Player player, int amount) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        double newHealth = maxHealth.getBaseValue() - amount;
        maxHealth.setBaseValue(newHealth);

        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }

        if (configManager.isHeartWithdrawSoundEnabled()) {
            player.playSound(player.getLocation(),
                    Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        }
    }

    public void setMaxHealth(Player player, double amount) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        maxHealth.setBaseValue(Math.max(amount, configManager.getMinimumMaxHealth()));

        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }
}