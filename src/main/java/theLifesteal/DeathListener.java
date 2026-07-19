package theLifesteal;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {

    private final TheLifesteal plugin;
    private final HeartManager heartManager;

    public DeathListener(TheLifesteal plugin) {
        this.plugin = plugin;
        this.heartManager = plugin.getHeartManager();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Check if player was killed by another player (optional, for PvP-only lifesteal)
        if (player.getKiller() != null && player.getKiller() instanceof Player) {
            // Remove heart from victim
            heartManager.removeHeartOnDeath(player);

            // Give heart to killer (optional feature)
            Player killer = player.getKiller();
            heartManager.applyHeartEffect(killer);
        }
    }
}