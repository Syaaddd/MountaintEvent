package com.mountaintevent.config;

import com.mountaintevent.Mountaintevent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigValue {

    public static int MIN_HEIGHT = 292; // Default dari config

    // Config variables
    public static String eventWorldName;
    public static int rtpMinY;
    public static int rtpMaxY;
    public static int rtpRadius;
    public static int batchSize;
    public static long batchInterval;

    // Winner locations
    public static Location juara1Location;
    public static Location juara2Location;
    public static Location juara3Location;

    // Main spawn location
    public static Location mainSpawnLocation;
    public static boolean noJumpEnabled = true; // Default enabled

    private static final Mountaintevent plugin = Mountaintevent.getInstance();


    public static void createDefaultConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!plugin.getConfig().contains("min-height")) {
            // Set default values manually
            plugin.getConfig().set("min-height", 292);
            plugin.getConfig().set("event-world", "MountCliff");
            plugin.getConfig().set("rtp.min-y", 64);
            plugin.getConfig().set("rtp.max-y", 64);
            plugin.getConfig().set("rtp.radius", 1000);
            plugin.getConfig().set("batch.size", 5);
            plugin.getConfig().set("batch.interval", 2);
            plugin.getConfig().set("no-jump", true);

            plugin.saveConfig();
            plugin.getLogger().info("Config.yml berhasil dibuat dengan nilai default!");
        }
    }

    public static void loadConfigValues() {
        FileConfiguration config = plugin.getConfig();
        MIN_HEIGHT = config.getInt("min-height", 292);
        eventWorldName = config.getString("event-world", "MountCliff");
        rtpMinY = config.getInt("rtp.min-y", 64);
        rtpMaxY = config.getInt("rtp.max-y", 64);
        rtpRadius = config.getInt("rtp.radius", 1000);
        batchSize = config.getInt("batch.size", 5);
        batchInterval = config.getLong("batch.interval", 2L);
        noJumpEnabled = config.getBoolean("no-jump", true);

        // Load main spawn location
        loadMainSpawnLocation();

        // Load winner locations
        loadWinnerLocation("juara1", 1);
        loadWinnerLocation("juara2", 2);
        loadWinnerLocation("juara3", 3);
    }

    private static void loadMainSpawnLocation() {
        if (plugin.getConfig().contains("main-spawn.world")) {
            String worldName = plugin.getConfig().getString("main-spawn.world");
            double x = plugin.getConfig().getDouble("main-spawn.x");
            double y = plugin.getConfig().getDouble("main-spawn.y");
            double z = plugin.getConfig().getDouble("main-spawn.z");
            float yaw = (float) plugin.getConfig().getDouble("main-spawn.yaw");
            float pitch = (float) plugin.getConfig().getDouble("main-spawn.pitch");

            if (worldName == null) {
                return;
            }

            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                mainSpawnLocation = new Location(world, x, y, z, yaw, pitch);
            }
        }
    }

    private static void loadWinnerLocation(String key, int rank) {
        if (plugin.getConfig().contains("winners." + key + ".world")) {
            String worldName = plugin.getConfig().getString("winners." + key + ".world");
            double x = plugin.getConfig().getDouble("winners." + key + ".x");
            double y = plugin.getConfig().getDouble("winners." + key + ".y");
            double z = plugin.getConfig().getDouble("winners." + key + ".z");
            float yaw = (float) plugin.getConfig().getDouble("winners." + key + ".yaw");
            float pitch = (float) plugin.getConfig().getDouble("winners." + key + ".pitch");

            if (worldName == null) {
                return;
            }

            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                Location loc = new Location(world, x, y, z, yaw, pitch);
                switch (rank) {
                    case 1:
                        juara1Location = loc;
                        break;
                    case 2:
                        juara2Location = loc;
                        break;
                    case 3:
                        juara3Location = loc;
                        break;
                }
            }
        }
    }

    public static void saveMainSpawnLocation(Location location) {
        plugin.getConfig().set("main-spawn.world", location.getWorld().getName());
        plugin.getConfig().set("main-spawn.x", location.getX());
        plugin.getConfig().set("main-spawn.y", location.getY());
        plugin.getConfig().set("main-spawn.z", location.getZ());
        plugin.getConfig().set("main-spawn.yaw", location.getYaw());
        plugin.getConfig().set("main-spawn.pitch", location.getPitch());
        plugin.saveConfig();
    }

    public static void saveWinnerLocation(String key, Location location) {
        plugin.getConfig().set("winners." + key + ".world", location.getWorld().getName());
        plugin.getConfig().set("winners." + key + ".x", location.getX());
        plugin.getConfig().set("winners." + key + ".y", location.getY());
        plugin.getConfig().set("winners." + key + ".z", location.getZ());
        plugin.getConfig().set("winners." + key + ".yaw", location.getYaw());
        plugin.getConfig().set("winners." + key + ".pitch", location.getPitch());
        plugin.saveConfig();
    }

    public static void showConfigInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== MOUNTAIN EVENT INFO ==========");
        sender.sendMessage(ChatColor.YELLOW + "Min Height: " + ChatColor.WHITE + MIN_HEIGHT);
        sender.sendMessage(ChatColor.YELLOW + "Event World: " + ChatColor.WHITE + eventWorldName);
        sender.sendMessage(ChatColor.YELLOW + "RTP Y Range: " + ChatColor.WHITE + rtpMinY + "-" + rtpMaxY);
        sender.sendMessage(ChatColor.YELLOW + "RTP Radius: " + ChatColor.WHITE + rtpRadius + " blok");
        sender.sendMessage(ChatColor.YELLOW + "Batch Size: " + ChatColor.WHITE + batchSize + " players");
        sender.sendMessage(ChatColor.YELLOW + "Batch Interval: " + ChatColor.WHITE + batchInterval + " ticks");
        sender.sendMessage(ChatColor.YELLOW + "No Jump: " + (noJumpEnabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));

        if (mainSpawnLocation != null) {
            sender.sendMessage(ChatColor.AQUA + "Main Spawn: " + ChatColor.WHITE + formatLocation(mainSpawnLocation));
        } else {
            sender.sendMessage(ChatColor.RED + "Main Spawn: " + ChatColor.GRAY + "Belum diset!");
        }

        sender.sendMessage(ChatColor.GOLD + "=== Winner Locations ===");
        if (juara1Location != null) {
            sender.sendMessage(ChatColor.GOLD + "Juara 1: " + ChatColor.WHITE + formatLocation(juara1Location));
        } else {
            sender.sendMessage(ChatColor.RED + "Juara 1: " + ChatColor.GRAY + "Belum diset!");
        }

        if (juara2Location != null) {
            sender.sendMessage(ChatColor.YELLOW + "Juara 2: " + ChatColor.WHITE + formatLocation(juara2Location));
        } else {
            sender.sendMessage(ChatColor.RED + "Juara 2: " + ChatColor.GRAY + "Belum diset!");
        }

        if (juara3Location != null) {
            sender.sendMessage(ChatColor.GRAY + "Juara 3: " + ChatColor.WHITE + formatLocation(juara3Location));
        } else {
            sender.sendMessage(ChatColor.RED + "Juara 3: " + ChatColor.GRAY + "Belum diset!");
        }

        sender.sendMessage(ChatColor.GOLD + "=========================================");

        // Show readiness status
        boolean ready = (mainSpawnLocation != null && juara1Location != null &&
                juara2Location != null && juara3Location != null);

        if (ready) {
            sender.sendMessage(ChatColor.GREEN + "✓ Event siap untuk dimulai!");
        } else {
            sender.sendMessage(ChatColor.RED + "✗ Event belum siap! Set semua lokasi terlebih dahulu.");
        }
    }

    public static String formatLocation(Location loc) {
        return String.format("%.1f, %.1f, %.1f (%s)",
                loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
    }
}
