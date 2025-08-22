package com.mountaintevent.maps;

import com.mountaintevent.Mountaintevent;
import com.mountaintevent.config.ConfigValue;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class Utils {

    public static ItemStack createFlag(UUID ownerUUID) {
        ItemStack flag = new ItemStack(Material.RED_BANNER, 1);
        BannerMeta meta = (BannerMeta) flag.getItemMeta();

        if (meta != null) {
            // Set pattern: Merah di atas, Putih di bawah
            List<Pattern> patterns = new ArrayList<>();
            patterns.add(new Pattern(DyeColor.WHITE, PatternType.HALF_HORIZONTAL_BOTTOM));
            meta.setPatterns(patterns);

            meta.displayName(Component.text(ChatColor.RED + "Bendera Merah Putih"));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Pasang di ketinggian Y >= " + ConfigValue.MIN_HEIGHT);
            lore.add(ChatColor.GOLD + "Bertahan 10 detik untuk menang!");
            if (ConfigValue.noJumpEnabled) {
                lore.add(ChatColor.RED + "⚠️ JUMPING DILARANG! ⚠️");
            }
            lore.add(ChatColor.GRAY + "Owner: " + Bukkit.getOfflinePlayer(ownerUUID).getName());
            meta.setLore(lore);

            // Menandai item sebagai bendera khusus
            meta.getPersistentDataContainer().set(Mountaintevent.getInstance().flagKey, PersistentDataType.STRING, "mountain_flag");
            meta.getPersistentDataContainer().set(Mountaintevent.getInstance().ownerKey, PersistentDataType.STRING, ownerUUID.toString());

            flag.setItemMeta(meta);
        }

        return flag;
    }

    public static boolean isMountainFlag(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer().has(Mountaintevent.getInstance().flagKey, PersistentDataType.STRING);
    }

    public static UUID getFlagOwner(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }

        String ownerString = item.getItemMeta().getPersistentDataContainer().get(Mountaintevent.getInstance().ownerKey, PersistentDataType.STRING);
        if (ownerString != null) {
            try {
                return UUID.fromString(ownerString);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    public static boolean hasMountainFlag(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMountainFlag(item)) {
                return true;
            }
        }
        return false;
    }

    public static void clearAllFlags() {
        // Cancel all timers
        for (BukkitTask timer : UtilityMap.flagTimers.values()) {
            if (timer != null && !timer.isCancelled()) {
                timer.cancel();
            }
        }
        UtilityMap.flagTimers.clear();
        UtilityMap.flagOwners.clear();
    }

    public static void clearPlayerFlags(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (Utils.isMountainFlag(item)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    public static void showLeaderboard(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== LEADERBOARD ==========");

        if (UtilityMap.playerWins.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Belum ada pemenang!");
            return;
        }

        // Sort players by wins (descending)
        LinkedHashMap<UUID, Integer> sortedWins = UtilityMap.playerWins.entrySet()
                .stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sortedWins.entrySet()) {
            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            int wins = entry.getValue();

            ChatColor rankColor = ChatColor.WHITE;
            if (rank == 1) rankColor = ChatColor.GOLD;
            else if (rank == 2) rankColor = ChatColor.YELLOW;
            else if (rank == 3) rankColor = ChatColor.GRAY;

            sender.sendMessage(rankColor + "#" + rank + " " + playerName + " - " + wins + " kemenangan");
            rank++;
        }

        sender.sendMessage(ChatColor.GOLD + "================================");
    }

    public static String formatLocation(Location loc) {
        return String.format("%.1f, %.1f, %.1f (%s)",
                loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
    }

    public static boolean inScope(Player p) {
        if (!ConfigValue.noJumpEnabled) return false;
        if (p.hasPermission("mountainevent.admin")) return false;
        if (!p.getWorld().getName().equalsIgnoreCase(ConfigValue.eventWorldName)) return false;
        if (!p.getScoreboardTags().contains("mountainEvent")) return false;
        return UtilityMap.trackPlayer.contains(p.getUniqueId());
    }

}
