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
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && killer instanceof Player) {
            // Victim may drop a heart; killer doesn't auto-gain
            heartManager.removeHeartOnDeath(victim);
        }
    }
}