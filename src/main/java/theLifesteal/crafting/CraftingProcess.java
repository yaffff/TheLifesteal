package theLifesteal.crafting;

import org.bukkit.entity.Player;
import java.util.UUID;

public class CraftingProcess {

    private final UUID playerUUID;
    private final CraftingRecipe recipe;
    private final long startTime;
    private final long endTime;
    private boolean completed;
    private boolean claimed;

    public CraftingProcess(UUID playerUUID, CraftingRecipe recipe) {
        this.playerUUID = playerUUID;
        this.recipe = recipe;
        this.startTime = System.currentTimeMillis();
        this.endTime = startTime + (recipe.getCraftingTime() * 1000);
        this.completed = false;
        this.claimed = false;
    }
    public CraftingProcess(UUID playerUUID, CraftingRecipe recipe, long startTime, long endTime) {
        this.playerUUID = playerUUID;
        this.recipe = recipe;
        this.startTime = startTime;
        this.endTime = endTime;
        this.completed = false;
        this.claimed = false;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public CraftingRecipe getRecipe() { return recipe; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public boolean isCompleted() { return completed || System.currentTimeMillis() >= endTime; }
    public boolean isClaimed() { return claimed; }

    public void setClaimed(boolean claimed) { this.claimed = claimed; }

    public long getRemainingTime() {
        if (isCompleted()) return 0;
        return endTime - System.currentTimeMillis();
    }

    public String getFormattedRemainingTime() {
        long remaining = getRemainingTime() / 1000;
        long hours = remaining / 3600;
        long minutes = (remaining % 3600) / 60;
        long seconds = remaining % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public double getProgress() {
        if (isCompleted()) return 1.0;
        long total = endTime - startTime;
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.min(1.0, (double) elapsed / total);
    }
}