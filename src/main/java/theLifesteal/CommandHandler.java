package theLifesteal;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import theLifesteal.crafting.CraftingGUI;
import theLifesteal.crafting.RecipeBookItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final TheLifesteal plugin;
    private final ConfigManager configManager;
    private final HeartManager heartManager;
    private final RecipeBookItem recipeBookItem;
    private final CraftingGUI craftingGUI;

    public CommandHandler(TheLifesteal plugin, RecipeBookItem recipeBookItem, CraftingGUI craftingGUI) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.heartManager = plugin.getHeartManager();
        this.recipeBookItem = recipeBookItem;
        this.craftingGUI = craftingGUI;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase()) {
            case "withdrawhearts":
                return handleWithdrawCommand(sender, args);
            case "setmaxhp":
                return handleSetMaxHPCommand(sender, args);
            case "craft":
                return handleCraftCommand(sender, args);
            case "recipebook":
                return handleRecipeBookCommand(sender, args);
            case "customitems":
                return handleCustomItemsCommand(sender, args);
            default:
                return false;
        }
    }

    private boolean handleWithdrawCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("player-only")));
            return true;
        }

        if (!player.hasPermission("thelifesteal.withdraw")) {
            player.sendMessage(ColorUtils.colorize(configManager.getMessage("no-permission")));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(ColorUtils.colorize("&cUsage: /withdrawhearts <amount>"));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(ColorUtils.colorize(configManager.getMessage("invalid-amount")));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(ColorUtils.colorize(configManager.getMessage("invalid-amount")));
            return true;
        }

        if (!heartManager.canWithdrawHearts(player, amount)) {
            player.sendMessage(ColorUtils.colorize(configManager.getMessage("min-health-reached")));
            return true;
        }

        ItemStack heartItem = heartManager.createHeartItem(amount);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(heartItem);

        int given = amount;
        if (!leftovers.isEmpty()) {
            int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            given = amount - leftoverAmount;

            if (given <= 0) {
                player.sendMessage(ColorUtils.colorize(configManager.getMessage("inventory-full")));
                return true;
            }

            player.sendMessage(ColorUtils.colorize(
                    configManager.getMessage("heart-withdrawn").replace("%amount%", String.valueOf(given))
            ));
            player.sendMessage(ColorUtils.colorize("&e" + leftoverAmount + " items didn't fit in your inventory!"));
        } else {
            player.sendMessage(ColorUtils.colorize(
                    configManager.getMessage("heart-withdrawn").replace("%amount%", String.valueOf(amount))
            ));
        }

        heartManager.withdrawHearts(player, given);
        return true;
    }

    private boolean handleSetMaxHPCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("thelifesteal.admin")) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("no-permission")));
            return true;
        }

        Player target;
        double amount;

        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.colorize("&cUsage: /setmaxhp <player> <amount>"));
                return true;
            }
            target = player;
            try {
                amount = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.colorize(configManager.getMessage("invalid-amount")));
                return true;
            }
        } else if (args.length == 2) {
            target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ColorUtils.colorize(configManager.getMessage("player-not-found")));
                return true;
            }
            try {
                amount = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.colorize(configManager.getMessage("invalid-amount")));
                return true;
            }
        } else {
            sender.sendMessage(ColorUtils.colorize("&cUsage: /setmaxhp [player] <amount>"));
            return true;
        }

        if (amount < configManager.getMinimumMaxHealth()) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("min-health-reached")));
            return true;
        }

        heartManager.setMaxHealth(target, amount);

        if (target.equals(sender)) {
            sender.sendMessage(ColorUtils.colorize(
                    configManager.getMessage("health-set-self").replace("%amount%", String.valueOf(amount))
            ));
        } else {
            sender.sendMessage(ColorUtils.colorize(
                    configManager.getMessage("health-set")
                            .replace("%player%", target.getName())
                            .replace("%amount%", String.valueOf(amount))
            ));
        }

        return true;
    }

    private boolean handleCraftCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("player-only")));
            return true;
        }

        if (!player.hasPermission("thelifesteal.craft")) {
            player.sendMessage(ColorUtils.colorize(configManager.getMessage("no-permission")));
            return true;
        }

        craftingGUI.openMainMenu(player);
        return true;
    }

    private boolean handleRecipeBookCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("thelifesteal.admin")) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("no-permission")));
            return true;
        }

        Player target;
        if (args.length == 0 && sender instanceof Player player) {
            target = player;
        } else if (args.length == 1) {
            target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ColorUtils.colorize(configManager.getMessage("player-not-found")));
                return true;
            }
        } else {
            sender.sendMessage(ColorUtils.colorize("&cUsage: /recipebook [player]"));
            return true;
        }

        int slot = recipeBookItem.getSlot();
        target.getInventory().setItem(slot, recipeBookItem.createRecipeBook());
        target.sendMessage(ColorUtils.colorize("&a✦ You received a new Recipe Book!"));

        if (target != sender) {
            sender.sendMessage(ColorUtils.colorize("&a✦ Gave a Recipe Book to " + target.getName()));
        }

        return true;
    }

    private boolean handleCustomItemsCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("thelifesteal.admin")) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("no-permission")));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("player-only")));
            return true;
        }

        // Access admin GUI through crafting GUI
        craftingGUI.getAdminGUI().openCustomItemsMenu(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        switch (command.getName().toLowerCase()) {
            case "withdrawhearts":
                if (args.length == 1) {
                    completions.add("1");
                    completions.add("2");
                    completions.add("5");
                    completions.add("10");
                    completions.add("20");
                }
                break;

            case "setmaxhp":
                if (args.length == 1) {
                    completions.addAll(plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                } else if (args.length == 2) {
                    completions.add("20");
                    completions.add("30");
                    completions.add("40");
                    completions.add("60");
                }
                break;

            case "recipebook":
                if (args.length == 1) {
                    completions.addAll(plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                }
                break;

            case "craft":
            case "customitems":
                break;
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}