package theLifesteal.abilities;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TotemProtectionManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Integer> totemPopTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> totemPopTimestamps = new ConcurrentHashMap<>();

    public TotemProtectionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            markTotemPop(player);
        }
    }

    /**
     * Mark a player as having popped a totem on the current tick.
     */
    public void markTotemPop(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        int currentTick = Bukkit.getCurrentTick();
        totemPopTicks.put(uuid, currentTick);
        totemPopTimestamps.put(uuid, System.currentTimeMillis());
    }

    /**
     * Check if a player is protected by a recently popped Totem of Undying on the current tick or within 500ms.
     */
    public boolean isTotemProtected(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();

        Integer popTick = totemPopTicks.get(uuid);
        if (popTick != null) {
            int currentTick = Bukkit.getCurrentTick();
            if (currentTick - popTick <= 1) {
                return true;
            }
        }

        Long popTime = totemPopTimestamps.get(uuid);
        if (popTime != null) {
            if (System.currentTimeMillis() - popTime < 500L) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a player currently holds a Totem of Undying in their main hand or off hand.
     */
    public boolean hasTotem(Player player) {
        if (player == null) return false;
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        return (mainHand != null && mainHand.getType() == Material.TOTEM_OF_UNDYING) ||
               (offHand != null && offHand.getType() == Material.TOTEM_OF_UNDYING);
    }

    /**
     * Clear tracking data when player leaves.
     */
    public void clearPlayer(UUID uuid) {
        if (uuid != null) {
            totemPopTicks.remove(uuid);
            totemPopTimestamps.remove(uuid);
        }
    }
}
