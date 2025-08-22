package com.mountaintevent.commands;

import com.mountaintevent.Mountaintevent;
import com.mountaintevent.config.ConfigValue;
import com.mountaintevent.maps.Utils;
import com.mountaintevent.mechanics.EventManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MainCommands implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("mountaint")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "========== MOUNTAIN EVENT ==========");
                sender.sendMessage(ChatColor.YELLOW + "Available Commands:");
                if (sender.hasPermission("mountainevent.start")) {
                    sender.sendMessage(ChatColor.WHITE + "/mountaint start - Memulai Mountain Event");
                }
                if (sender.hasPermission("mountainevent.admin")) {
                    sender.sendMessage(ChatColor.WHITE + "/mountaint reload - Reload config plugin");
                    sender.sendMessage(ChatColor.WHITE + "/mountaint set spawn - Set main spawn point");
                    sender.sendMessage(ChatColor.WHITE + "/mountaint set juara <1/2/3> - Set winner location");
                    sender.sendMessage(ChatColor.WHITE + "/mountaint info - Show configuration info");
                    sender.sendMessage(ChatColor.WHITE + "/mountaint toggle-jump - Toggle no-jump feature");
                }
                if (sender.hasPermission("mountainevent.giveflag")) {
                    sender.sendMessage(ChatColor.WHITE + "/mountaint giveflag - Get mountain flag");
                }
                sender.sendMessage(ChatColor.WHITE + "/mountaint leaderboard - Show leaderboard");
                sender.sendMessage(ChatColor.GOLD + "===================================");
                return true;
            }

            String subCommand = args[0].toLowerCase();

            // START SUBCOMMAND
            if (subCommand.equals("start")) {
                if (!sender.hasPermission("mountainevent.start")) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada permission untuk memulai event!");
                    return true;
                }
                EventManager eventManager = new EventManager();
                eventManager.startEvent();
                return true;
            }

            // TOGGLE JUMP SUBCOMMAND
            if (subCommand.equals("toggle-jump")) {
                if (!sender.hasPermission("mountainevent.admin")) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada permission!");
                    return true;
                }

                ConfigValue.noJumpEnabled = !ConfigValue.noJumpEnabled;
                Mountaintevent.getInstance().getConfig().set("no-jump", ConfigValue.noJumpEnabled);
                Mountaintevent.getInstance().saveConfig();

                sender.sendMessage(ChatColor.GREEN + "No-Jump feature " +
                        (ConfigValue.noJumpEnabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED") + "!");

                if (Mountaintevent.getInstance().eventActive) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "No-Jump feature " +
                            (ConfigValue.noJumpEnabled ? ChatColor.GREEN + "diaktifkan" : ChatColor.RED + "dinonaktifkan") +
                            " oleh " + sender.getName() + "!");
                }

                return true;
            }

            // RELOAD SUBCOMMAND
            if (subCommand.equals("reload")) {
                if (!sender.hasPermission("mountainevent.admin")) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada permission untuk reload config!");
                    return true;
                }

                Mountaintevent.getInstance().reloadConfig();
                ConfigValue.loadConfigValues();
                sender.sendMessage(ChatColor.GREEN + "Config berhasil direload!");
                sender.sendMessage(ChatColor.YELLOW + "Min Height: " + ConfigValue.MIN_HEIGHT);
                sender.sendMessage(ChatColor.YELLOW + "Event World: " + ConfigValue.eventWorldName);
                sender.sendMessage(ChatColor.YELLOW + "RTP Range: Y" + ConfigValue.rtpMinY + "-" + ConfigValue.rtpMaxY + " (Radius: " + ConfigValue.rtpRadius + ")");
                sender.sendMessage(ChatColor.YELLOW + "Batch: " + ConfigValue.batchSize + " players/" + ConfigValue.batchInterval + " ticks");
                sender.sendMessage(ChatColor.YELLOW + "No Jump: " + (ConfigValue.noJumpEnabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));

                if (ConfigValue.mainSpawnLocation != null) {
                    sender.sendMessage(ChatColor.AQUA + "Main Spawn: " + Utils.formatLocation(ConfigValue.mainSpawnLocation));
                } else {
                    sender.sendMessage(ChatColor.RED + "WARNING: Main spawn belum diset!");
                }

                // Show border info if world exists
                World world = Bukkit.getWorld(ConfigValue.eventWorldName);
                if (world != null) {
                    WorldBorder border = world.getWorldBorder();
                    double borderSize = border.getSize();
                    double effectiveRadius = Math.min(ConfigValue.rtpRadius, (borderSize / 2.0) - 50);
                    sender.sendMessage(ChatColor.BLUE + "World Border: " + (int)borderSize + "x" + (int)borderSize + " blocks");
                    sender.sendMessage(ChatColor.BLUE + "Effective RTP Radius: " + (int)effectiveRadius + " blocks");

                    if (effectiveRadius <= 0) {
                        sender.sendMessage(ChatColor.RED + "WARNING: Border terlalu kecil untuk RTP!");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "WARNING: Event world tidak ditemukan!");
                }

                return true;
            }

            // GIVEFLAG SUBCOMMAND
            if (subCommand.equals("giveflag")) {
                if (!sender.hasPermission("mountainevent.giveflag")) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada permission untuk mendapatkan bendera!");
                    return true;
                }

                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Command ini hanya bisa digunakan oleh player!");
                    return true;
                }

                if (!Mountaintevent.getInstance().eventActive) {
                    player.sendMessage(ChatColor.RED + "Event belum dimulai!");
                    return true;
                }

                if (Utils.hasMountainFlag(player)) {
                    player.sendMessage(ChatColor.RED + "Anda sudah memiliki bendera!");
                    return true;
                }

                ItemStack flag = Utils.createFlag(player.getUniqueId());
                player.getInventory().addItem(flag);

                // Give oak slab for building
                ItemStack oakSlab = new ItemStack(Material.OAK_SLAB, 64);
                player.getInventory().addItem(oakSlab);

                player.sendMessage(ChatColor.GREEN + "Bendera Merah Putih diberikan!");
                player.sendMessage(ChatColor.AQUA + "Oak Slab x64 diberikan untuk building!");
                return true;
            }

            // LEADERBOARD SUBCOMMAND
            if (subCommand.equals("leaderboard") || subCommand.equals("lb") || subCommand.equals("top")) {
                if (!Mountaintevent.getInstance().eventActive) {
                    sender.sendMessage(ChatColor.RED + "Event belum dimulai!");
                    return true;
                }
                Utils.showLeaderboard(sender);
                return true;
            }

            // INFO SUBCOMMAND
            if (subCommand.equals("info")) {
                if (!sender.hasPermission("mountainevent.admin")) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada permission!");
                    return true;
                }
                ConfigValue.showConfigInfo(sender);
                return true;
            }

            // SET SUBCOMMAND
            if (subCommand.equals("set")) {
                if (!sender.hasPermission("mountainevent.admin")) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada permission!");
                    return true;
                }

                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Command ini hanya bisa digunakan oleh player!");
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /mountaint set <spawn|juara>");
                    return true;
                }

                String setType = args[1].toLowerCase();

                if (setType.equals("spawn")) {
                    ConfigValue.mainSpawnLocation = player.getLocation().clone();
                    ConfigValue.saveMainSpawnLocation(ConfigValue.mainSpawnLocation);
                    sender.sendMessage(ChatColor.GREEN + "Main spawn berhasil diset!");
                    sender.sendMessage(ChatColor.YELLOW + "Lokasi: " + Utils.formatLocation(ConfigValue.mainSpawnLocation));
                    sender.sendMessage(ChatColor.AQUA + "RTP akan menggunakan titik ini sebagai pusat dengan radius " + ConfigValue.rtpRadius + " blok");
                    return true;
                }

                if (setType.equals("juara")) {
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /mountaint set juara <1/2/3>");
                        return true;
                    }

                    String rank = args[2];

                    switch (rank) {
                        case "1":
                            ConfigValue.juara1Location = player.getLocation().clone();
                            ConfigValue.saveWinnerLocation("juara1", ConfigValue.juara1Location);
                            sender.sendMessage(ChatColor.GREEN + "Lokasi Juara 1 berhasil diset!");
                            break;
                        case "2":
                            ConfigValue.juara2Location = player.getLocation().clone();
                            ConfigValue.saveWinnerLocation("juara2", ConfigValue.juara2Location);
                            sender.sendMessage(ChatColor.GREEN + "Lokasi Juara 2 berhasil diset!");
                            break;
                        case "3":
                            ConfigValue.juara3Location = player.getLocation().clone();
                            ConfigValue.saveWinnerLocation("juara3", ConfigValue.juara3Location);
                            sender.sendMessage(ChatColor.GREEN + "Lokasi Juara 3 berhasil diset!");
                            break;
                        default:
                            sender.sendMessage(ChatColor.RED + "Rank harus 1, 2, atau 3!");
                            return true;
                    }

                    Location loc = player.getLocation();
                    sender.sendMessage(ChatColor.YELLOW + "Koordinat: " + Utils.formatLocation(loc));
                    return true;
                }

                sender.sendMessage(ChatColor.RED + "Usage: /mountaint set <spawn|juara>");
                return true;
            }

            // Invalid subcommand
            sender.sendMessage(ChatColor.RED + "Subcommand tidak dikenal: " + subCommand);
            sender.sendMessage(ChatColor.YELLOW + "Gunakan /mountaint untuk melihat daftar command.");
            return true;
        }

        return false;
    }
}
