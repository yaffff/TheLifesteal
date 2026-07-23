package theLifesteal;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class DeathListener implements Listener {

    private final TheLifesteal plugin;
    private final HeartManager heartManager;
    private final ConfigManager configManager;

    public DeathListener(TheLifesteal plugin) {
        this.plugin = plugin;
        this.heartManager = plugin.getHeartManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // If Bukkit killer is null, check if victim was recently damaged/affected by an ability
        if (killer == null && plugin.getAbilityManager() != null) {
            var killTracker = plugin.getAbilityManager().getKillTracker();
            if (killTracker != null) {
                killer = killTracker.getCasterForVictim(victim.getUniqueId());
                if (killer != null) {
                    victim.setKiller(killer);
                }
            }
        }

        // === PVP HANDLING ===
        if (killer != null) {
            // Victim loses max HP
            heartManager.removeHeartOnDeath(victim);

            // Broadcast lifesteal message
            if (configManager.getConfig().getBoolean("settings.broadcast-lifesteal", true)) {
                String message = configManager.getMessage("lifesteal-broadcast")
                        .replace("%killer%", killer.getName())
                        .replace("%victim%", victim.getName());
                Bukkit.broadcastMessage(message);
            }
        }

        // === DEATH DROP ===
        handleDeathDrop(victim, killer != null);

        // Cleanup ability tracking for victim
        if (plugin.getAbilityManager() != null && plugin.getAbilityManager().getKillTracker() != null) {
            plugin.getAbilityManager().getKillTracker().clearVictim(victim.getUniqueId());
        }
    }

    /**
     * Handle dropping a custom item on death based on config settings.
     */
    private void handleDeathDrop(Player victim, boolean isPvp) {
        boolean enabled = configManager.getConfig().getBoolean("settings.death-drop.enabled", true);
        if (!enabled) return;

        // Check pvp-only
        boolean pvpOnly = configManager.getConfig().getBoolean("settings.death-drop.pvp-only", true);
        if (pvpOnly && !isPvp) return;

        // Check min-health: victim must have more than min-health max HP
        double minHealth = configManager.getConfig().getDouble("settings.death-drop.min-health", 2.0);
        double victimMaxHp = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
        if (victimMaxHp <= minHealth) return;

        // Roll chance
        double chance = configManager.getConfig().getDouble("settings.death-drop.chance", 1.0);
        if (Math.random() >= chance) return;

        // Get the item to drop
        String itemId = configManager.getConfig().getString("settings.death-drop.item-id", "life_shard");
        var advancedItemManager = plugin.getAdvancedItemManager();
        if (advancedItemManager == null) return;

        var customItem = advancedItemManager.getItem(itemId);
        if (customItem == null) {
            plugin.getLogger().warning("Death drop item not found: " + itemId);
            return;
        }

        // Build and drop the item
        int dropCount = configManager.getConfig().getInt("settings.death-drop.drop-count", 1);
        ItemStack dropStack = advancedItemManager.buildItem(customItem);
        dropStack.setAmount(dropCount);
        victim.getWorld().dropItemNaturally(victim.getLocation(), dropStack);
    }
}