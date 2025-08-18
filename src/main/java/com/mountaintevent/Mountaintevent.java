package com.mountaintevent;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.DyeColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.Random;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
interface BiConsumerWithIndex<T> {
    void accept(T t, int index);
}

public class Mountaintevent extends JavaPlugin implements Listener, TabCompleter {

    private static int MIN_HEIGHT = 292; // Default dari config
    private NamespacedKey flagKey;
    private NamespacedKey ownerKey;
    private Map<Location, UUID> flagOwners = new HashMap<>();
    private Map<Location, BukkitTask> flagTimers = new HashMap<>();
    private Map<UUID, Integer> playerWins = new HashMap<>();
    private boolean eventActive = false;
    private Random random = new Random();

    // Config variables
    private String eventWorldName;
    private int rtpMinY;
    private int rtpMaxY;
    private int rtpRadius;
    private int batchSize;
    private long batchInterval;

    // Winner locations
    private Location juara1Location;
    private Location juara2Location;
    private Location juara3Location;

    // Main spawn location
    private Location mainSpawnLocation;

    // Winner tracking
    private List<UUID> winners = new ArrayList<>();
    private boolean eventFinished = false;

    // No jump feature - track player Y positions
    private Map<UUID, Double> playerLastY = new HashMap<>();
    private boolean noJumpEnabled = true; // Default enabled

    @Override
    public void onEnable() {
        this.flagKey = new NamespacedKey(this, "mountain_flag");
        this.ownerKey = new NamespacedKey(this, "flag_owner");

        // Create config if doesn't exist
        createDefaultConfig();
        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);

        // Register tab completer
        this.getCommand("mountaint").setTabCompleter(this);

        getLogger().info("MountainEvent plugin telah aktif!");
        getLogger().info("Gunakan /mtstart untuk memulai event!");
        getLogger().info("Ketinggian minimal: Y >= " + MIN_HEIGHT);
        getLogger().info("Event World: " + eventWorldName);
        getLogger().info("No Jump: " + (noJumpEnabled ? "ENABLED" : "DISABLED"));
        if (mainSpawnLocation != null) {
            getLogger().info("Main Spawn: " + formatLocation(mainSpawnLocation));
        } else {
            getLogger().warning("Main spawn belum diset! Gunakan /mountaint set spawn");
        }
    }

    @Override
    public void onDisable() {
        // Cancel semua timer yang masih berjalan
        for (BukkitTask task : flagTimers.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        flagTimers.clear();

        // Clear tracking data
        playerLastY.clear();

        getLogger().info("MountainEvent plugin telah dinonaktifkan!");
    }

    private void createDefaultConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        if (!getConfig().contains("min-height")) {
            // Set default values manually
            getConfig().set("min-height", 292);
            getConfig().set("event-world", "MountCliff");
            getConfig().set("rtp.min-y", 64);
            getConfig().set("rtp.max-y", 64);
            getConfig().set("rtp.radius", 1000);
            getConfig().set("batch.size", 5);
            getConfig().set("batch.interval", 2);
            getConfig().set("no-jump", true);

            saveConfig();
            getLogger().info("Config.yml berhasil dibuat dengan nilai default!");
        }
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();
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

    private void loadMainSpawnLocation() {
        if (getConfig().contains("main-spawn.world")) {
            String worldName = getConfig().getString("main-spawn.world");
            double x = getConfig().getDouble("main-spawn.x");
            double y = getConfig().getDouble("main-spawn.y");
            double z = getConfig().getDouble("main-spawn.z");
            float yaw = (float) getConfig().getDouble("main-spawn.yaw");
            float pitch = (float) getConfig().getDouble("main-spawn.pitch");

            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                mainSpawnLocation = new Location(world, x, y, z, yaw, pitch);
            }
        }
    }

    private void loadWinnerLocation(String key, int rank) {
        if (getConfig().contains("winners." + key + ".world")) {
            String worldName = getConfig().getString("winners." + key + ".world");
            double x = getConfig().getDouble("winners." + key + ".x");
            double y = getConfig().getDouble("winners." + key + ".y");
            double z = getConfig().getDouble("winners." + key + ".z");
            float yaw = (float) getConfig().getDouble("winners." + key + ".yaw");
            float pitch = (float) getConfig().getDouble("winners." + key + ".pitch");

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

    private void saveMainSpawnLocation(Location location) {
        getConfig().set("main-spawn.world", location.getWorld().getName());
        getConfig().set("main-spawn.x", location.getX());
        getConfig().set("main-spawn.y", location.getY());
        getConfig().set("main-spawn.z", location.getZ());
        getConfig().set("main-spawn.yaw", location.getYaw());
        getConfig().set("main-spawn.pitch", location.getPitch());
        saveConfig();
    }

    private void saveWinnerLocation(String key, Location location) {
        getConfig().set("winners." + key + ".world", location.getWorld().getName());
        getConfig().set("winners." + key + ".x", location.getX());
        getConfig().set("winners." + key + ".y", location.getY());
        getConfig().set("winners." + key + ".z", location.getZ());
        getConfig().set("winners." + key + ".yaw", location.getYaw());
        getConfig().set("winners." + key + ".pitch", location.getPitch());
        saveConfig();
    }

    private String formatLocation(Location loc) {
        return String.format("%.1f, %.1f, %.1f (%s)",
                loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("mountaint")) {
            if (args.length == 1) {
                // First argument: subcommands
                if (sender.hasPermission("mountainevent.start")) {
                    completions.add("start");
                }
                if (sender.hasPermission("mountainevent.admin")) {
                    completions.add("reload");
                    completions.add("set");
                    completions.add("info");
                    completions.add("toggle-jump");
                }
                if (sender.hasPermission("mountainevent.giveflag")) {
                    completions.add("giveflag");
                }
                completions.add("leaderboard");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                // Second argument after "set": spawn, juara
                completions.add("spawn");
                completions.add("juara");
            } else if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("juara")) {
                // Third argument after "set juara": 1, 2, 3
                completions.add("1");
                completions.add("2");
                completions.add("3");
            }
        }

        // Filter based on what user has typed
        String lastArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
                startEvent();
                return true;
            }

            // TOGGLE JUMP SUBCOMMAND
            if (subCommand.equals("toggle-jump")) {
                if (!sender.hasPermission("mountainevent.admin")) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada permission!");
                    return true;
                }

                noJumpEnabled = !noJumpEnabled;
                getConfig().set("no-jump", noJumpEnabled);
                saveConfig();

                sender.sendMessage(ChatColor.GREEN + "No-Jump feature " +
                        (noJumpEnabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED") + "!");

                if (eventActive) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "No-Jump feature " +
                            (noJumpEnabled ? ChatColor.GREEN + "diaktifkan" : ChatColor.RED + "dinonaktifkan") +
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

                reloadConfig();
                loadConfigValues();
                sender.sendMessage(ChatColor.GREEN + "Config berhasil direload!");
                sender.sendMessage(ChatColor.YELLOW + "Min Height: " + MIN_HEIGHT);
                sender.sendMessage(ChatColor.YELLOW + "Event World: " + eventWorldName);
                sender.sendMessage(ChatColor.YELLOW + "RTP Range: Y" + rtpMinY + "-" + rtpMaxY + " (Radius: " + rtpRadius + ")");
                sender.sendMessage(ChatColor.YELLOW + "Batch: " + batchSize + " players/" + batchInterval + " ticks");
                sender.sendMessage(ChatColor.YELLOW + "No Jump: " + (noJumpEnabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));

                if (mainSpawnLocation != null) {
                    sender.sendMessage(ChatColor.AQUA + "Main Spawn: " + formatLocation(mainSpawnLocation));
                } else {
                    sender.sendMessage(ChatColor.RED + "WARNING: Main spawn belum diset!");
                }

                // Show border info if world exists
                World world = Bukkit.getWorld(eventWorldName);
                if (world != null) {
                    WorldBorder border = world.getWorldBorder();
                    double borderSize = border.getSize();
                    double effectiveRadius = Math.min(rtpRadius, (borderSize / 2.0) - 50);
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

                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Command ini hanya bisa digunakan oleh player!");
                    return true;
                }

                Player player = (Player) sender;

                if (!eventActive) {
                    player.sendMessage(ChatColor.RED + "Event belum dimulai!");
                    return true;
                }

                if (hasMountainFlag(player)) {
                    player.sendMessage(ChatColor.RED + "Anda sudah memiliki bendera!");
                    return true;
                }

                ItemStack flag = createFlag(player.getUniqueId());
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
                if (!eventActive) {
                    sender.sendMessage(ChatColor.RED + "Event belum dimulai!");
                    return true;
                }
                showLeaderboard(sender);
                return true;
            }

            // INFO SUBCOMMAND
            if (subCommand.equals("info")) {
                if (!sender.hasPermission("mountainevent.admin")) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada permission!");
                    return true;
                }
                showConfigInfo(sender);
                return true;
            }

            // SET SUBCOMMAND
            if (subCommand.equals("set")) {
                if (!sender.hasPermission("mountainevent.admin")) {
                    sender.sendMessage(ChatColor.RED + "Tidak ada permission!");
                    return true;
                }

                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Command ini hanya bisa digunakan oleh player!");
                    return true;
                }

                Player player = (Player) sender;

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /mountaint set <spawn|juara>");
                    return true;
                }

                String setType = args[1].toLowerCase();

                if (setType.equals("spawn")) {
                    mainSpawnLocation = player.getLocation().clone();
                    saveMainSpawnLocation(mainSpawnLocation);
                    sender.sendMessage(ChatColor.GREEN + "Main spawn berhasil diset!");
                    sender.sendMessage(ChatColor.YELLOW + "Lokasi: " + formatLocation(mainSpawnLocation));
                    sender.sendMessage(ChatColor.AQUA + "RTP akan menggunakan titik ini sebagai pusat dengan radius " + rtpRadius + " blok");
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
                            juara1Location = player.getLocation().clone();
                            saveWinnerLocation("juara1", juara1Location);
                            sender.sendMessage(ChatColor.GREEN + "Lokasi Juara 1 berhasil diset!");
                            break;
                        case "2":
                            juara2Location = player.getLocation().clone();
                            saveWinnerLocation("juara2", juara2Location);
                            sender.sendMessage(ChatColor.GREEN + "Lokasi Juara 2 berhasil diset!");
                            break;
                        case "3":
                            juara3Location = player.getLocation().clone();
                            saveWinnerLocation("juara3", juara3Location);
                            sender.sendMessage(ChatColor.GREEN + "Lokasi Juara 3 berhasil diset!");
                            break;
                        default:
                            sender.sendMessage(ChatColor.RED + "Rank harus 1, 2, atau 3!");
                            return true;
                    }

                    Location loc = player.getLocation();
                    sender.sendMessage(ChatColor.YELLOW + "Koordinat: " + formatLocation(loc));
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

    private void showConfigInfo(CommandSender sender) {
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

    // NO JUMP EVENT HANDLER
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process if event is active and no-jump is enabled
        if (!eventActive || !noJumpEnabled) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Skip if no actual movement or if player is teleporting
        if (to == null || (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        // Check if player is in event world
        if (!to.getWorld().getName().equals(eventWorldName)) {
            return;
        }

        // Store/update player's last Y position
        Double lastY = playerLastY.get(playerId);
        if (lastY == null) {
            playerLastY.put(playerId, to.getY());
            return;
        }

        // Detect if player is jumping (Y coordinate increased without being on a ladder, in water, etc.)
        double yDifference = to.getY() - lastY;

        // If player moved up more than 0.3 blocks (normal jump threshold)
        if (yDifference > 0.3) {
            // Check if player is legitimately moving up (stairs, blocks, etc.)
            Block blockBelow = to.getWorld().getBlockAt(to.getBlockX(), lastY.intValue(), to.getBlockZ());
            Block blockAtFeet = to.getWorld().getBlockAt(to.getBlockX(), (int)to.getY(), to.getBlockZ());
            Block blockAbove = to.getWorld().getBlockAt(to.getBlockX(), (int)to.getY() + 1, to.getBlockZ());

            // Check if player is in water, on ladder, or climbing
            if (blockAtFeet.getType() == Material.WATER ||
                    blockAtFeet.getType() == Material.LADDER ||
                    blockAtFeet.getType() == Material.VINE ||
                    blockBelow.getType().isSolid()) {
                // Allow legitimate vertical movement
                playerLastY.put(playerId, to.getY());
                return;
            }

            // This looks like a jump attempt - cancel it
            Location correctedLocation = from.clone();
            correctedLocation.setY(lastY);
            event.setTo(correctedLocation);

            // Optional: Send message to player (comment out if too spammy)
            // player.sendMessage(ChatColor.RED + "Jumping tidak diizinkan selama event!");

            // Optional: Apply brief slowness effect as penalty
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, false, false)); // 1 second slowness

            return;
        }

        // Update player's last Y position
        playerLastY.put(playerId, to.getY());
    }

    private void startEvent() {
        // Check if main spawn is set
        if (mainSpawnLocation == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "Error: Main spawn belum diset!");
            Bukkit.broadcastMessage(ChatColor.RED + "Gunakan /mountaint set spawn untuk set titik spawn utama!");
            getLogger().severe("Main spawn belum diset! Event dibatalkan.");
            return;
        }

        // Reset everything
        clearAllFlags();
        playerWins.clear();
        winners.clear();
        playerLastY.clear(); // Clear jump tracking
        eventActive = true;
        eventFinished = false;

        // Get event world
        World eventWorld = mainSpawnLocation.getWorld();
        if (eventWorld == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "Error: Event world tidak ditemukan!");
            getLogger().severe("Event world tidak ditemukan!");
            eventActive = false;
            return;
        }

        // Check world border
        WorldBorder border = eventWorld.getWorldBorder();
        double borderSize = border.getSize();
        double effectiveRadius = Math.min(rtpRadius, (borderSize / 2.0) - 50);

        if (effectiveRadius <= 0) {
            Bukkit.broadcastMessage(ChatColor.RED + "Error: World border terlalu kecil untuk RTP!");
            Bukkit.broadcastMessage(ChatColor.RED + "Border size: " + (int)borderSize + ", Required: " + (rtpRadius * 2 + 100));
            getLogger().severe("World border terlalu kecil! Border: " + borderSize + ", Required: " + (rtpRadius * 2 + 100));
            eventActive = false;
            return;
        }

        getLogger().info("Using main spawn: " + formatLocation(mainSpawnLocation));
        getLogger().info("World border info - Size: " + borderSize + ", Effective RTP radius: " + effectiveRadius);

        // Get all online players
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "Tidak ada player online!");
            eventActive = false;
            return;
        }

        // Generate random locations for all players around main spawn
        List<Location> rtpLocations = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Location rtpLoc = generateRandomLocationAroundSpawn(eventWorld, mainSpawnLocation);
            if (rtpLoc != null) {
                rtpLocations.add(rtpLoc);
            } else {
                // Fallback to main spawn if generation failed
                rtpLocations.add(mainSpawnLocation.clone().add(0, 5, 0));
            }
        }

        // Broadcast start
        Bukkit.broadcastMessage(ChatColor.GOLD + "================================");
        Bukkit.broadcastMessage(ChatColor.RED + "🏔️ MOUNTAIN EVENT DIMULAI! 🏔️");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Mempersiapkan arena event...");
        Bukkit.broadcastMessage(ChatColor.GREEN + "Loading chunks untuk " + players.size() + " player...");
        Bukkit.broadcastMessage(ChatColor.AQUA + "Spawn center: " + formatLocation(mainSpawnLocation));
        if (noJumpEnabled) {
            Bukkit.broadcastMessage(ChatColor.RED + "⚠️ JUMPING DILARANG SELAMA EVENT! ⚠️");
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "================================");

        // Preload chunks async, then batch process players
        preloadAllChunksAsync(rtpLocations).thenRun(() -> {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Arena siap! Teleporting players...");

            // Batch process: teleport players and give flags
            batchProcess(players, batchSize, batchInterval, (player, idx) -> {
                // Clear existing flags
                clearPlayerFlags(player);

                // Teleport player with safety check
                Location targetLoc = rtpLocations.get(idx);

                // Final safety check before teleport
                Location finalLoc = validateSafeLocation(targetLoc);

                // Teleport using sync method for compatibility
                Bukkit.getScheduler().runTask(this, () -> {
                    player.teleport(finalLoc);

                    // Initialize player's Y position for jump tracking
                    playerLastY.put(player.getUniqueId(), finalLoc.getY());

                    // Give flag after successful teleport
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (player.isOnline()) {
                            // Double-check player isn't in block after teleport
                            Location playerLoc = player.getLocation();
                            if (isPlayerInBlock(playerLoc)) {
                                // Emergency fix: move player up
                                playerLoc.add(0, 2, 0);
                                player.teleport(playerLoc);
                                playerLastY.put(player.getUniqueId(), playerLoc.getY()); // Update tracking
                                getLogger().info("Emergency fix: Moved " + player.getName() + " up 2 blocks to avoid suffocation");
                            }

                            ItemStack flag = createFlag(player.getUniqueId());
                            player.getInventory().addItem(flag);

                            // Give oak slab after teleport
                            ItemStack oakSlab = new ItemStack(Material.OAK_SLAB, 64);
                            player.getInventory().addItem(oakSlab);

                            player.sendMessage(ChatColor.GREEN + "Teleport berhasil! Bendera diberikan!");
                            player.sendMessage(ChatColor.AQUA + "Oak Slab x64 diberikan untuk building!");
                            player.sendMessage(ChatColor.GOLD + "Ketinggian minimal: Y >= " + MIN_HEIGHT);

                            if (noJumpEnabled) {
                                player.sendMessage(ChatColor.RED + "⚠️ JUMPING DILARANG! ⚠️");
                            }

                            // Show distance from spawn
                            double distance = finalLoc.distance(mainSpawnLocation);
                            player.sendMessage(ChatColor.AQUA + "Jarak dari pusat: " + (int)distance + " blok");
                        }
                    }, 10L);
                });
            });

            // Final announcement after all batches
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Bukkit.broadcastMessage(ChatColor.GOLD + "================================");
                Bukkit.broadcastMessage(ChatColor.GREEN + "Semua player siap di arena!");
                Bukkit.broadcastMessage(ChatColor.RED + "EVENT DIMULAI SEKARANG!");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Bertahan 10 detik untuk menang!");
                Bukkit.broadcastMessage(ChatColor.AQUA + "Semua player spawn melingkar dari titik yang sama!");
                if (noJumpEnabled) {
                    Bukkit.broadcastMessage(ChatColor.RED + "⚠️ JUMPING DILARANG! ⚠️");
                }
                Bukkit.broadcastMessage(ChatColor.GOLD + "================================");
            }, 40L); // 2 seconds after last batch
        });

        getLogger().info("Mountain Event started! Preparing " + players.size() + " players...");
        getLogger().info("No-Jump feature: " + (noJumpEnabled ? "ENABLED" : "DISABLED"));
    }

    private Location generateRandomLocationAroundSpawn(World world, Location centerSpawn) {
        int attempts = 0;
        int maxAttempts = 100;

        // Get world border info
        WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize();
        double borderRadius = borderSize / 2.0;

        while (attempts < maxAttempts) {
            double x, z;

            // Calculate effective radius (smaller of RTP radius or border radius - buffer)
            double effectiveRadius = Math.min(rtpRadius, borderRadius - 50); // 50 block buffer from border

            if (effectiveRadius <= 0) {
                getLogger().warning("World border terlalu kecil untuk RTP! Border size: " + borderSize);
                break;
            }

            // Generate random coordinates around main spawn
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * effectiveRadius;

            x = centerSpawn.getX() + (distance * Math.cos(angle));
            z = centerSpawn.getZ() + (distance * Math.sin(angle));

            // Find safe Y coordinate
            Location testLoc = new Location(world, x, world.getMaxHeight(), z);
            Location safeLoc = findSafeSpawnLocation(world, testLoc, border);

            if (safeLoc != null) {
                // Check if this location is reasonable distance from center spawn
                double distFromCenter = safeLoc.distance(centerSpawn);
                if (distFromCenter <= effectiveRadius) {
                    getLogger().info("RTP location generated: " + formatLocation(safeLoc) +
                            " (distance: " + (int)distFromCenter + ", attempt: " + (attempts + 1) + ")");
                    return safeLoc;
                }
            }

            attempts++;
        }

        // Fallback: safe location near main spawn
        Location safeSpawn = findSafeLocationNearSpawn(world, centerSpawn, border);

        getLogger().warning("Could not find valid RTP location after " + maxAttempts + " attempts, using safe spawn area");
        return safeSpawn;
    }

    private void clearAllFlags() {
        // Cancel all timers
        for (BukkitTask timer : flagTimers.values()) {
            if (timer != null && !timer.isCancelled()) {
                timer.cancel();
            }
        }
        flagTimers.clear();
        flagOwners.clear();
    }

    private void clearPlayerFlags(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isMountainFlag(item)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private Location findSafeSpawnLocation(World world, Location startLoc, WorldBorder border) {
        int startX = startLoc.getBlockX();
        int startZ = startLoc.getBlockZ();

        // Load chunk first
        world.getChunkAt(startX >> 4, startZ >> 4);

        // Find safe location around the preferred Y range from config
        int searchStartY = Math.max(rtpMaxY + 20, world.getMaxHeight() - 1);
        int searchEndY = Math.max(rtpMinY - 20, world.getMinHeight());

        // First pass: look for locations within preferred Y range
        for (int y = searchStartY; y >= searchEndY; y--) {
            Location checkLoc = new Location(world, startX + 0.5, y, startZ + 0.5);

            // Basic validation first
            if (!isValidLocation(world, checkLoc, border)) {
                continue;
            }

            Block block = world.getBlockAt(startX, y, startZ);
            Block above1 = world.getBlockAt(startX, y + 1, startZ);
            Block above2 = world.getBlockAt(startX, y + 2, startZ);

            // Check if this is a safe spawn location
            if (isSafeSpawnBlock(block) && isAirBlock(above1) && isAirBlock(above2)) {
                // Prioritize locations within config Y range
                if (y >= rtpMinY && y <= rtpMaxY + 10) {
                    return new Location(world, startX + 0.5, y + 1, startZ + 0.5);
                }
                // Accept other safe locations as fallback
                else if (y >= world.getMinHeight() + 10 && y <= world.getMaxHeight() - 10) {
                    // But try to get closer to preferred range
                    int targetY = (rtpMinY + rtpMaxY) / 2;
                    if (Math.abs(y - targetY) <= 50) { // Within 50 blocks of preferred range
                        return new Location(world, startX + 0.5, y + 1, startZ + 0.5);
                    }
                }
            }
        }

        return null; // No safe location found at this X,Z
    }

    private boolean isSafeSpawnBlock(Block block) {
        Material type = block.getType();

        // Blocks that are safe to spawn on
        if (type.isSolid() && !type.equals(Material.LAVA) && !type.equals(Material.MAGMA_BLOCK)) {
            // Avoid dangerous blocks
            switch (type) {
                case CACTUS:
                case FIRE:
                case SOUL_FIRE:
                case LAVA:
                case MAGMA_BLOCK:
                case WITHER_ROSE:
                case SWEET_BERRY_BUSH:
                case POINTED_DRIPSTONE:
                case POWDER_SNOW:
                    return false;
                default:
                    return true;
            }
        }

        return false;
    }

    private boolean isAirBlock(Block block) {
        Material type = block.getType();

        // Blocks that are safe to spawn in (air-like)
        switch (type) {
            case AIR:
            case CAVE_AIR:
            case VOID_AIR:
            case GRASS_BLOCK:
            case TALL_GRASS:
            case FERN:
            case LARGE_FERN:
            case DEAD_BUSH:
            case DANDELION:
            case POPPY:
            case BLUE_ORCHID:
            case ALLIUM:
            case AZURE_BLUET:
            case RED_TULIP:
            case ORANGE_TULIP:
            case WHITE_TULIP:
            case PINK_TULIP:
            case OXEYE_DAISY:
            case CORNFLOWER:
            case LILY_OF_THE_VALLEY:
            case WITHER_ROSE:
            case SUNFLOWER:
            case LILAC:
            case ROSE_BUSH:
            case PEONY:
            case SNOW:
                return true;
            default:
                return false;
        }
    }

    private boolean isLocationInBorder(Location loc, WorldBorder border) {
        Location center = border.getCenter();
        double borderRadius = border.getSize() / 2.0;

        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Add 10 block buffer to be safe
        return distance <= (borderRadius - 10);
    }

    private boolean isValidLocation(World world, Location loc, WorldBorder border) {
        // Check if location is within world border
        if (!isLocationInBorder(loc, border)) {
            return false;
        }

        // Basic coordinate validation
        if (Math.abs(loc.getX()) >= 30000000 || Math.abs(loc.getZ()) >= 30000000) {
            return false;
        }

        // Check if Y coordinate is valid
        if (loc.getY() < world.getMinHeight() || loc.getY() > world.getMaxHeight()) {
            return false;
        }

        return true;
    }

    private Location findSafeLocationNearSpawn(World world, Location spawn, WorldBorder border) {
        // Try to find safe location within 200 blocks of spawn (increased search area)
        for (int attempt = 0; attempt < 50; attempt++) {
            double x = spawn.getX() + (random.nextDouble() * 400 - 200); // ±200 blocks
            double z = spawn.getZ() + (random.nextDouble() * 400 - 200);

            Location testLoc = new Location(world, x, world.getMaxHeight(), z);
            Location safeLoc = findSafeSpawnLocation(world, testLoc, border);

            if (safeLoc != null) {
                return safeLoc;
            }
        }

        // Ultimate fallback: try spawn location itself
        Location spawnSafe = findSafeSpawnLocation(world, spawn, border);
        if (spawnSafe != null) {
            return spawnSafe;
        }

        // Last resort: main spawn with adjusted Y (at least it's safe from suffocation)
        Location lastResort = spawn.clone();
        lastResort.setY(Math.max(spawn.getY() + 5, rtpMinY)); // 5 blocks above spawn or rtpMinY
        return lastResort;
    }

    // Validate location is safe for teleportation
    private Location validateSafeLocation(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Check if player would be inside blocks
        Block feetBlock = world.getBlockAt(x, y, z);
        Block headBlock = world.getBlockAt(x, y + 1, z);

        // If either feet or head position has solid blocks, adjust upward
        while ((feetBlock.getType().isSolid() || headBlock.getType().isSolid()) && y < world.getMaxHeight() - 2) {
            y++;
            feetBlock = world.getBlockAt(x, y, z);
            headBlock = world.getBlockAt(x, y + 1, z);
        }

        return new Location(world, x + 0.5, y, z + 0.5);
    }

    // Check if player would suffocate at location
    private boolean isPlayerInBlock(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        Block feetBlock = world.getBlockAt(x, y, z);
        Block headBlock = world.getBlockAt(x, y + 1, z);

        return feetBlock.getType().isSolid() || headBlock.getType().isSolid();
    }

    // Preload all chunks async
    private CompletableFuture<Void> preloadAllChunksAsync(List<Location> locs) {
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (Location loc : locs) {
            // Try async chunk loading, fallback to sync if not available
            try {
                futures.add(loc.getWorld().getChunkAtAsync(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, true));
            } catch (NoSuchMethodError e) {
                // Fallback for older versions - load sync
                loc.getWorld().getChunkAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            }
        }

        if (futures.isEmpty()) {
            // All chunks loaded synchronously
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    // Batch utility generic
    private <T> void batchProcess(List<T> list, int batchSize, long intervalTicks, BiConsumerWithIndex<T> consumer) {
        Iterator<T> it = list.iterator();
        final int[] idx = {0};

        Bukkit.getScheduler().runTaskTimer(this, task -> {
            for (int i = 0; i < batchSize && it.hasNext(); i++) {
                T t = it.next();
                consumer.accept(t, idx[0]++);
            }
            if (!it.hasNext()) {
                task.cancel();
            }
        }, 0L, intervalTicks);
    }

    private void showLeaderboard(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== LEADERBOARD ==========");

        if (playerWins.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Belum ada pemenang!");
            return;
        }

        // Sort players by wins (descending)
        LinkedHashMap<UUID, Integer> sortedWins = playerWins.entrySet()
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

    private ItemStack createFlag(UUID ownerUUID) {
        ItemStack flag = new ItemStack(Material.RED_BANNER, 1);
        BannerMeta meta = (BannerMeta) flag.getItemMeta();

        if (meta != null) {
            // Set pattern: Merah di atas, Putih di bawah
            List<Pattern> patterns = new ArrayList<>();
            patterns.add(new Pattern(DyeColor.WHITE, PatternType.HALF_HORIZONTAL_BOTTOM));
            meta.setPatterns(patterns);

            meta.setDisplayName(ChatColor.RED + "Bendera Merah Putih");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Pasang di ketinggian Y >= " + MIN_HEIGHT);
            lore.add(ChatColor.GOLD + "Bertahan 10 detik untuk menang!");
            if (noJumpEnabled) {
                lore.add(ChatColor.RED + "⚠️ JUMPING DILARANG! ⚠️");
            }
            lore.add(ChatColor.GRAY + "Owner: " + Bukkit.getOfflinePlayer(ownerUUID).getName());
            meta.setLore(lore);

            // Menandai item sebagai bendera khusus
            meta.getPersistentDataContainer().set(flagKey, PersistentDataType.STRING, "mountain_flag");
            meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ownerUUID.toString());

            flag.setItemMeta(meta);
        }

        return flag;
    }

    private boolean isMountainFlag(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer().has(flagKey, PersistentDataType.STRING);
    }

    private UUID getFlagOwner(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }

        String ownerString = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerString != null) {
            try {
                return UUID.fromString(ownerString);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    private boolean hasMountainFlag(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMountainFlag(item)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Hanya berikan bendera jika event sedang aktif
        if (eventActive && !hasMountainFlag(player)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline() && !hasMountainFlag(player)) {
                    ItemStack flag = createFlag(player.getUniqueId());
                    player.getInventory().addItem(flag);

                    // Give oak slab to new joiners during active event
                    ItemStack oakSlab = new ItemStack(Material.OAK_SLAB, 64);
                    player.getInventory().addItem(oakSlab);

                    player.sendMessage(ChatColor.GREEN + "Bendera Merah Putih diberikan!");
                    player.sendMessage(ChatColor.AQUA + "Oak Slab x64 diberikan untuk building!");
                    player.sendMessage(ChatColor.GOLD + "Event Mountain sedang berlangsung!");
                    if (noJumpEnabled) {
                        player.sendMessage(ChatColor.RED + "⚠️ JUMPING DILARANG SELAMA EVENT! ⚠️");
                    }

                    // Initialize jump tracking for new player
                    playerLastY.put(player.getUniqueId(), player.getLocation().getY());
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        // Cek apakah yang dipasang adalah bendera khusus
        if (!isMountainFlag(item)) {
            return;
        }

        // Cek apakah event aktif dan belum selesai
        if (!eventActive || eventFinished) {
            event.setCancelled(true);
            if (eventFinished) {
                player.sendMessage(ChatColor.RED + "Event sudah selesai!");
            } else {
                player.sendMessage(ChatColor.RED + "Event belum dimulai!");
            }
            return;
        }

        Location location = event.getBlockPlaced().getLocation();

        // Cek ketinggian
        if (location.getY() < MIN_HEIGHT) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Ketinggian tidak cukup! Minimal Y = " + MIN_HEIGHT);
            return;
        }

        // Cek kepemilikan
        UUID flagOwnerUUID = getFlagOwner(item);
        if (flagOwnerUUID == null || !flagOwnerUUID.equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Bukan bendera Anda!");
            return;
        }

        // Cek apakah player sudah memasang bendera lain
        for (Location loc : flagOwners.keySet()) {
            if (flagOwners.get(loc).equals(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Anda sudah memasang bendera!");
                return;
            }
        }

        // Simpan informasi pemilik bendera
        flagOwners.put(location, player.getUniqueId());

        // Calculate distance from main spawn
        String distanceText = "";
        if (mainSpawnLocation != null && mainSpawnLocation.getWorld().equals(location.getWorld())) {
            double distance = location.distance(mainSpawnLocation);
            distanceText = " (jarak: " + (int)distance + "m)";
        }

        // Broadcast ke semua player
        Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + ChatColor.WHITE +
                " memasang bendera di Y=" + (int)location.getY() + distanceText + "!");
        Bukkit.broadcastMessage(ChatColor.RED + "10 detik untuk menghancurkannya!");

        // Start countdown timer
        startFlagTimer(location, player);
    }

    private void startFlagTimer(Location location, Player player) {
        BukkitTask timer = Bukkit.getScheduler().runTaskLater(this, () -> {
            // Cek apakah bendera masih ada dan event belum selesai
            if (flagOwners.containsKey(location) && !eventFinished) {
                // Player menang!
                playerWins.put(player.getUniqueId(), playerWins.getOrDefault(player.getUniqueId(), 0) + 1);
                winners.add(player.getUniqueId());

                int winnerRank = winners.size();

                Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");
                Bukkit.broadcastMessage(ChatColor.GREEN + "🏆 " + player.getName() + " JUARA " + winnerRank + "! 🏆");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Bendera bertahan selama 10 detik!");
                Bukkit.broadcastMessage(ChatColor.GRAY + "Total kemenangan: " + playerWins.get(player.getUniqueId()));

                // Show distance from main spawn if available
                if (mainSpawnLocation != null && mainSpawnLocation.getWorld().equals(location.getWorld())) {
                    double distance = location.distance(mainSpawnLocation);
                    Bukkit.broadcastMessage(ChatColor.AQUA + "Jarak dari pusat spawn: " + (int)distance + " blok");
                }

                Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");

                // Hapus bendera dari dunia
                Block flagBlock = location.getBlock();
                if (flagBlock.getType() == Material.RED_BANNER || flagBlock.getType() == Material.RED_WALL_BANNER) {
                    flagBlock.setType(Material.AIR);
                }

                // Teleport pemenang ke lokasi juara
                teleportWinnerToLocation(player, winnerRank);

                flagOwners.remove(location);
                flagTimers.remove(location);

                // Cek apakah sudah ada 3 pemenang
                if (winners.size() >= 3) {
                    finishEvent();
                } else {
                    // Show leaderboard sementara
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        Bukkit.broadcastMessage(ChatColor.AQUA + "Masih tersisa " + (3 - winners.size()) + " posisi juara!");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            showLeaderboard(p);
                        }
                    }, 40L); // 2 detik setelah announcement
                }
            }
        }, 200L); // 10 detik = 200 ticks

        flagTimers.put(location, timer);
    }

    private void teleportWinnerToLocation(Player player, int rank) {
        Location targetLocation = null;
        String rankName = "";

        switch (rank) {
            case 1:
                targetLocation = juara1Location;
                rankName = "JUARA 1";
                break;
            case 2:
                targetLocation = juara2Location;
                rankName = "JUARA 2";
                break;
            case 3:
                targetLocation = juara3Location;
                rankName = "JUARA 3";
                break;
        }

        if (targetLocation != null && player.isOnline()) {
            Location finalTargetLocation = targetLocation;
            String finalRankName = rankName;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                player.teleport(finalTargetLocation);
                player.sendTitle(
                        ChatColor.GOLD + "🏆 " + finalRankName + " 🏆",
                        ChatColor.YELLOW + "Selamat atas kemenangan Anda!",
                        10, 70, 20
                );

                // Give celebration effects
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 2, 0), 50, 2, 2, 2, 0.1);

                // Update jump tracking after teleport
                playerLastY.put(player.getUniqueId(), finalTargetLocation.getY());

            }, 60L); // 3 detik delay untuk effect
        } else {
            player.sendMessage(ChatColor.RED + "Lokasi " + rankName.toLowerCase() + " belum diset! Hubungi admin.");
            getLogger().warning("Lokasi " + rankName + " belum diset! Gunakan /mountaint set juara " + rank);
        }
    }

    private void finishEvent() {
        eventFinished = true;
        eventActive = false;

        // Cancel semua timer yang masih berjalan
        for (BukkitTask timer : flagTimers.values()) {
            if (timer != null && !timer.isCancelled()) {
                timer.cancel();
            }
        }

        // Clear jump tracking
        playerLastY.clear();

        // Final announcement
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.broadcastMessage(ChatColor.GOLD + "=====================================");
            Bukkit.broadcastMessage(ChatColor.RED + "🎉 MOUNTAIN EVENT SELESAI! 🎉");
            Bukkit.broadcastMessage(ChatColor.GOLD + "=====================================");

            // Announce winners
            for (int i = 0; i < winners.size(); i++) {
                UUID winnerUUID = winners.get(i);
                String playerName = Bukkit.getOfflinePlayer(winnerUUID).getName();

                ChatColor rankColor = ChatColor.WHITE;
                String trophy = "";

                switch (i + 1) {
                    case 1:
                        rankColor = ChatColor.GOLD;
                        trophy = "🥇";
                        break;
                    case 2:
                        rankColor = ChatColor.YELLOW;
                        trophy = "🥈";
                        break;
                    case 3:
                        rankColor = ChatColor.GRAY;
                        trophy = "🥉";
                        break;
                }

                Bukkit.broadcastMessage(rankColor + trophy + " Juara " + (i + 1) + ": " + playerName);
            }

            Bukkit.broadcastMessage(ChatColor.GOLD + "=====================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "Terima kasih telah berpartisipasi!");
            Bukkit.broadcastMessage(ChatColor.AQUA + "Semua player memiliki kesempatan yang sama!");
            if (noJumpEnabled) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Jumping sekarang diizinkan kembali!");
            }
            Bukkit.broadcastMessage(ChatColor.GOLD + "=====================================");

            // Clear all flags from other players
            for (Player player : Bukkit.getOnlinePlayers()) {
                clearPlayerFlags(player);
            }

        }, 20L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();

        // Cek apakah block yang dihancurkan adalah bendera yang terdaftar
        if (!flagOwners.containsKey(location)) {
            return;
        }

        // Cek apakah block adalah banner
        if (block.getType() != Material.RED_BANNER &&
                block.getType() != Material.RED_WALL_BANNER) {
            return;
        }

        UUID ownerUUID = flagOwners.get(location);
        Player owner = Bukkit.getPlayer(ownerUUID);
        Player breaker = event.getPlayer();

        // Cancel timer
        BukkitTask timer = flagTimers.get(location);
        if (timer != null && !timer.isCancelled()) {
            timer.cancel();
        }

        // Cancel drop normal
        event.setDropItems(false);

        // Broadcast
        if (!breaker.getUniqueId().equals(ownerUUID)) {
            Bukkit.broadcastMessage(ChatColor.RED + breaker.getName() + " menghancurkan bendera " +
                    (owner != null ? owner.getName() : "Unknown") + "!");
        }

        // Buat bendera baru dan berikan ke pemilik
        if (owner != null && owner.isOnline()) {
            if (!hasMountainFlag(owner)) {
                ItemStack flag = createFlag(ownerUUID);
                owner.getInventory().addItem(flag);
            }
        }

        // Hapus dari tracking
        flagOwners.remove(location);
        flagTimers.remove(location);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        if (isMountainFlag(item)) {
            event.setCancelled(true);
        }
    }
}