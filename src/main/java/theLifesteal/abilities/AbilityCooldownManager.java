package theLifesteal.abilities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AbilityCooldownManager {

    // player UUID -> (abilityId:itemId -> expiry timestamp)
    private final Map<UUID, Map<String, Long>> cooldowns;

    public AbilityCooldownManager() {
        this.cooldowns = new HashMap<>();
    }

    public boolean isOnCooldown(UUID playerId, String abilityId, String itemId) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return false;

        String key = abilityId + ":" + itemId;
        Long expiry = playerCooldowns.get(key);
        if (expiry == null) return false;

        return System.currentTimeMillis() < expiry;
    }

    public long getRemainingCooldown(UUID playerId, String abilityId, String itemId) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return 0;

        String key = abilityId + ":" + itemId;
        Long expiry = playerCooldowns.get(key);
        if (expiry == null) return 0;

        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public void setCooldown(UUID playerId, String abilityId, String itemId, long cooldownSeconds) {
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(playerId, k -> new HashMap<>());
        String key = abilityId + ":" + itemId;
        playerCooldowns.put(key, System.currentTimeMillis() + (cooldownSeconds * 1000));
    }

    public void clearPlayer(UUID playerId) {
        cooldowns.remove(playerId);
    }

    public String formatCooldown(long remainingMillis) {
        if (remainingMillis <= 0) return "Ready";
        long seconds = remainingMillis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + "m " + seconds + "s";
    }
    private final Map<UUID, Map<String, Integer>> customCounters = new HashMap<>();

    public int getCustomCounter(UUID playerId, String key) {
        Map<String, Integer> playerCounters = customCounters.get(playerId);
        if (playerCounters == null) return 0;
        return playerCounters.getOrDefault(key, 0);
    }

    public void setCustomCounter(UUID playerId, String key, int value) {
        customCounters.computeIfAbsent(playerId, k -> new HashMap<>()).put(key, value);
    }
}