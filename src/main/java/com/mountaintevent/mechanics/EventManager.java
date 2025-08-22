package com.mountaintevent.mechanics;

import com.mountaintevent.Mountaintevent;
import com.mountaintevent.config.ConfigValue;
import com.mountaintevent.maps.UtilityMap;
import com.mountaintevent.maps.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class EventManager {

    private final Random random = new Random();

    public void startEvent() {
        // Check if main spawn is set
        if (ConfigValue.mainSpawnLocation == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "Error: Main spawn belum diset!");
            Bukkit.broadcastMessage(ChatColor.RED + "Gunakan /mountaint set spawn untuk set titik spawn utama!");
            Mountaintevent.getInstance().getLogger().severe("Main spawn belum diset! Event dibatalkan.");
            return;
        }

        // Reset everything
        Utils.clearAllFlags();
        UtilityMap.playerWins.clear();
        UtilityMap.winners.clear();
        UtilityMap.playerLastY.clear(); // Clear jump tracking
        Mountaintevent.getInstance().eventActive = true;
        Mountaintevent.getInstance().eventFinished = false;

        // Get event world
        World eventWorld = ConfigValue.mainSpawnLocation.getWorld();
        if (eventWorld == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "Error: Event world tidak ditemukan!");
            Mountaintevent.getInstance().getLogger().severe("Event world tidak ditemukan!");
            Mountaintevent.getInstance().eventActive = false;
            return;
        }

        // Check world border
        WorldBorder border = eventWorld.getWorldBorder();
        double borderSize = border.getSize();
        double effectiveRadius = Math.min(ConfigValue.rtpRadius, (borderSize / 2.0) - 50);

        if (effectiveRadius <= 0) {
            Bukkit.broadcastMessage(ChatColor.RED + "Error: World border terlalu kecil untuk RTP!");
            Bukkit.broadcastMessage(ChatColor.RED + "Border size: " + (int)borderSize + ", Required: " + (ConfigValue.rtpRadius * 2 + 100));
            Mountaintevent.getInstance().getLogger().severe("World border terlalu kecil! Border: " + borderSize + ", Required: " + (ConfigValue.rtpRadius * 2 + 100));
            Mountaintevent.getInstance().eventActive = false;
            return;
        }

        Mountaintevent.getInstance().getLogger().info("Using main spawn: " + Utils.formatLocation(ConfigValue.mainSpawnLocation));
        Mountaintevent.getInstance().getLogger().info("World border info - Size: " + borderSize + ", Effective RTP radius: " + effectiveRadius);

        // Get all online players
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "Tidak ada player online!");
            Mountaintevent.getInstance().eventActive = false;
            return;
        }

        // Generate random locations for all players around main spawn
        List<Location> rtpLocations = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Location rtpLoc = generateRandomLocationAroundSpawn(eventWorld, 25,175, 100);
            rtpLocations.add(rtpLoc);
        }

        // Broadcast start
        Bukkit.broadcastMessage(ChatColor.GOLD + "================================");
        Bukkit.broadcastMessage(ChatColor.RED + "🏔️ MOUNTAIN EVENT DIMULAI! 🏔️");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Mempersiapkan arena event...");
        Bukkit.broadcastMessage(ChatColor.GREEN + "Loading chunks untuk " + players.size() + " player...");
        Bukkit.broadcastMessage(ChatColor.AQUA + "Spawn center: " + Utils.formatLocation(ConfigValue.mainSpawnLocation));
        if (ConfigValue.noJumpEnabled) {
            Bukkit.broadcastMessage(ChatColor.RED + "⚠️ JUMPING DILARANG SELAMA EVENT! ⚠️");
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "================================");

        // Preload chunks async, then batch process players
        preloadAllChunksAsync(rtpLocations).thenRun(() -> {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Arena siap! Teleporting players...");

            // Batch process: teleport players and give flags
            batchProcess(players, ConfigValue.batchSize, ConfigValue.batchInterval, (player, idx) -> {
                // Clear existing flags
                Utils.clearPlayerFlags(player);

                // Give player a scoreboardTag
                player.addScoreboardTag("mountainEvent");

                // Clear inventory
                player.getInventory().clear();

                // Feed and heal player
                player.setFoodLevel(20);
                player.setSaturation(20);
                player.setHealth(20);

                // Track player
                UtilityMap.trackPlayer.add(player.getUniqueId());

                // Teleport player with safety check
                Location targetLoc = rtpLocations.get(idx);

                // Final safety check before teleport
                Location finalLoc = validateSafeLocation(targetLoc);

                // Teleport using sync method for compatibility
                Bukkit.getScheduler().runTask(Mountaintevent.getInstance(), () -> {
                    player.teleport(finalLoc);

                    // Initialize player's Y position for jump tracking
                    UtilityMap.playerLastY.put(player.getUniqueId(), finalLoc.getY());

                    // Give flag after successful teleport
                    Bukkit.getScheduler().runTaskLater(Mountaintevent.getInstance(), () -> {
                        if (player.isOnline()) {
                            // Double-check player isn't in block after teleport
                            Location playerLoc = player.getLocation();
                            if (isPlayerInBlock(playerLoc)) {
                                // Emergency fix: move player up
                                playerLoc.add(0, 2, 0);
                                player.teleport(playerLoc);
                                UtilityMap.playerLastY.put(player.getUniqueId(), playerLoc.getY()); // Update tracking
                                Mountaintevent.getInstance().getLogger().info("Emergency fix: Moved " + player.getName() + " up 2 blocks to avoid suffocation");
                            }

                            ItemStack flag = Utils.createFlag(player.getUniqueId());
                            player.getInventory().addItem(flag);

                            // Give oak slab after teleport
                            ItemStack oakSlab = new ItemStack(Material.OAK_SLAB, 64);
                            player.getInventory().addItem(oakSlab);

                            player.sendMessage(ChatColor.GREEN + "Teleport berhasil! Bendera diberikan!");
                            player.sendMessage(ChatColor.AQUA + "Oak Slab x64 diberikan untuk building!");
                            player.sendMessage(ChatColor.GOLD + "Ketinggian minimal: Y >= " + ConfigValue.MIN_HEIGHT);

                            if (ConfigValue.noJumpEnabled) {
                                player.sendMessage(ChatColor.RED + "⚠️ JUMPING DILARANG! ⚠️");
                            }

                            // Show distance from spawn
                            double distance = finalLoc.distance(ConfigValue.mainSpawnLocation);
                            player.sendMessage(ChatColor.AQUA + "Jarak dari pusat: " + (int)distance + " blok");
                        }
                    }, 10L);
                });
            });

            // Final announcement after all batches
            Bukkit.getScheduler().runTaskLater(Mountaintevent.getInstance(), () -> {
                Bukkit.broadcastMessage(ChatColor.GOLD + "================================");
                Bukkit.broadcastMessage(ChatColor.GREEN + "Semua player siap di arena!");
                Bukkit.broadcastMessage(ChatColor.RED + "EVENT DIMULAI SEKARANG!");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Bertahan 10 detik untuk menang!");
                Bukkit.broadcastMessage(ChatColor.AQUA + "Semua player spawn melingkar dari titik yang sama!");
                if (ConfigValue.noJumpEnabled) {
                    Bukkit.broadcastMessage(ChatColor.RED + "⚠️ JUMPING DILARANG! ⚠️");
                }
                Bukkit.broadcastMessage(ChatColor.GOLD + "================================");
            }, 40L); // 2 seconds after last batch
        });

        Mountaintevent.getInstance().getLogger().info("Mountain Event started! Preparing " + players.size() + " players...");
        Mountaintevent.getInstance().getLogger().info("No-Jump feature: " + (ConfigValue.noJumpEnabled ? "ENABLED" : "DISABLED"));
    }

    private Location generateRandomLocationAroundSpawn(World world,
                                                       int paddingFromBorder, // distance kept from border edge
                                                       int bandThickness,     // thickness of the green ring
                                                       int attempts) {
        if (world == null) return null;

        WorldBorder border = world.getWorldBorder();
        int borderHalf = (int) Math.floor(border.getSize() / 2.0); // half side of the border square

        // half side of OUTER and INNER squares of the ring
        int outerHalf = borderHalf - Math.max(0, paddingFromBorder);
        if (outerHalf <= 0) return null;

        int innerHalf = Math.max(0, outerHalf - Math.max(1, bandThickness));
        if (innerHalf >= outerHalf) return null;

        // center on the BORDER center to guarantee it fits
        Location c = border.getCenter();
        int cx = c.getBlockX();
        int cz = c.getBlockZ();

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (int i = 0; i < Math.max(1, attempts); i++) {
            int x = rnd.nextInt(cx - outerHalf, cx + outerHalf + 1); // nextInt upper bound is exclusive
            int z = rnd.nextInt(cz - outerHalf, cz + outerHalf + 1);

            // offsets from center
            int ox = x - cx;
            int oz = z - cz;

            // reject if inside the inner square (the white hole)
            if (Math.abs(ox) <= innerHalf && Math.abs(oz) <= innerHalf) continue;

            int y = world.getHighestBlockYAt(x, z);
            Block ground = world.getBlockAt(x, y - 1, z);
            if (!isSafeSpawnBlock(ground)) continue;

            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            if (!border.isInside(loc)) continue; // extra safety

            return loc;
        }
        return null;
    }


    /*private Location generateRandomLocationAroundSpawn(World world, Location centerSpawn) {
        int attempts = 0;
        int maxAttempts = 100;

        // Get world border info
        WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize();
        double borderRadius = borderSize / 2.0;

        while (attempts < maxAttempts) {
            double x, z;

            // Calculate effective radius (smaller of RTP radius or border radius - buffer)
            double effectiveRadius = Math.min(ConfigValue.rtpRadius, borderRadius - 50); // 50 block buffer from border

            if (effectiveRadius <= 0) {
                Mountaintevent.getInstance().getLogger().warning("World border terlalu kecil untuk RTP! Border size: " + borderSize);
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
                    Mountaintevent.getInstance().getLogger().info("RTP location generated: " + Utils.formatLocation(safeLoc) +
                            " (distance: " + (int)distFromCenter + ", attempt: " + (attempts + 1) + ")");
                    return safeLoc;
                }
            }

            attempts++;
        }

        // Fallback: safe location near main spawn
        Location safeSpawn = findSafeLocationNearSpawn(world, centerSpawn, border);

        Mountaintevent.getInstance().getLogger().warning("Could not find valid RTP location after " + maxAttempts + " attempts, using safe spawn area");
        return safeSpawn;
    }*/

    private Location findSafeSpawnLocation(World world, Location startLoc, WorldBorder border) {
        int startX = startLoc.getBlockX();
        int startZ = startLoc.getBlockZ();

        // Load chunk first
        world.getChunkAt(startX >> 4, startZ >> 4);

        // Find safe location around the preferred Y range from config
        int searchStartY = Math.max(ConfigValue.rtpMaxY + 20, world.getMaxHeight() - 1);
        int searchEndY = Math.max(ConfigValue.rtpMinY - 20, world.getMinHeight());

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
                if (y >= ConfigValue.rtpMinY && y <= ConfigValue.rtpMaxY + 10) {
                    return new Location(world, startX + 0.5, y + 1, startZ + 0.5);
                }
                // Accept other safe locations as fallback
                else if (y >= world.getMinHeight() + 10 && y <= world.getMaxHeight() - 10) {
                    // But try to get closer to preferred range
                    int targetY = (ConfigValue.rtpMinY + ConfigValue.rtpMaxY) / 2;
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
            return switch (type) {
                case CACTUS, FIRE, SOUL_FIRE, WITHER_ROSE, SWEET_BERRY_BUSH, POINTED_DRIPSTONE,
                     POWDER_SNOW -> false;
                default -> true;
            };
        }

        return false;
    }

    private boolean isAirBlock(Block block) {
        Material type = block.getType();

        // Blocks that are safe to spawn in (air-like)
        return switch (type) {
            case AIR, CAVE_AIR, VOID_AIR, GRASS_BLOCK, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH, DANDELION, POPPY,
                 BLUE_ORCHID, ALLIUM, AZURE_BLUET, RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP, OXEYE_DAISY,
                 CORNFLOWER, LILY_OF_THE_VALLEY, WITHER_ROSE, SUNFLOWER, LILAC, ROSE_BUSH, PEONY, SNOW -> true;
            default -> false;
        };
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
        return !(loc.getY() < world.getMinHeight()) && !(loc.getY() > world.getMaxHeight());
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
        lastResort.setY(Math.max(spawn.getY() + 5, ConfigValue.rtpMinY)); // 5 blocks above spawn or rtpMinY
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

    @FunctionalInterface
    interface BiConsumerWithIndex<T> {
        void accept(T t, int index);
    }

    // Batch utility generic
    private <T> void batchProcess(List<T> list, int batchSize, long intervalTicks, BiConsumerWithIndex<T> consumer) {
        Iterator<T> it = list.iterator();
        final int[] idx = {0};

        Bukkit.getScheduler().runTaskTimer(Mountaintevent.getInstance(), task -> {
            for (int i = 0; i < batchSize && it.hasNext(); i++) {
                T t = it.next();
                consumer.accept(t, idx[0]++);
            }
            if (!it.hasNext()) {
                task.cancel();
            }
        }, 0L, intervalTicks);
    }

    public void finishEvent() {
        Mountaintevent.getInstance().eventFinished = true;
        Mountaintevent.getInstance().eventActive = false;

        // Cancel semua timer yang masih berjalan
        for (BukkitTask timer : UtilityMap.flagTimers.values()) {
            if (timer != null && !timer.isCancelled()) {
                timer.cancel();
            }
        }

        // Clear jump tracking
        UtilityMap.playerLastY.clear();
        UtilityMap.trackPlayer.clear();

        // Final announcement
        Bukkit.getScheduler().runTaskLater(Mountaintevent.getInstance(), () -> {
            Bukkit.broadcastMessage(ChatColor.GOLD + "=====================================");
            Bukkit.broadcastMessage(ChatColor.RED + "🎉 MOUNTAIN EVENT SELESAI! 🎉");
            Bukkit.broadcastMessage(ChatColor.GOLD + "=====================================");

            // Announce winners
            for (int i = 0; i < UtilityMap.winners.size(); i++) {
                UUID winnerUUID = UtilityMap.winners.get(i);
                String playerName = Bukkit.getOfflinePlayer(winnerUUID).getName();

                ChatColor rankColor = ChatColor.WHITE;
                String trophy = switch (i + 1) {
                    case 1 -> {
                        rankColor = ChatColor.GOLD;
                        yield "🥇";
                    }
                    case 2 -> {
                        rankColor = ChatColor.YELLOW;
                        yield "🥈";
                    }
                    case 3 -> {
                        rankColor = ChatColor.GRAY;
                        yield "🥉";
                    }
                    default -> "";
                };

                Bukkit.broadcastMessage(rankColor + trophy + " Juara " + (i + 1) + ": " + playerName);
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!UtilityMap.winners.contains(player.getUniqueId())) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "spawn " + player.getName());
                    player.getInventory().clear();
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }

            Bukkit.broadcastMessage(ChatColor.GOLD + "=====================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "Terima kasih telah berpartisipasi!");
            Bukkit.broadcastMessage(ChatColor.AQUA + "Semua player memiliki kesempatan yang sama!");
            if (ConfigValue.noJumpEnabled) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Jumping sekarang diizinkan kembali!");
            }
            Bukkit.broadcastMessage(ChatColor.GOLD + "=====================================");

            // Clear all flags from other players
            for (Player player : Bukkit.getOnlinePlayers()) {
                Utils.clearPlayerFlags(player);
            }

        }, 20L);
    }
}
