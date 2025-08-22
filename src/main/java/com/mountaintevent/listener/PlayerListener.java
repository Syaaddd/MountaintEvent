package com.mountaintevent.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.mountaintevent.Mountaintevent;
import com.mountaintevent.config.ConfigValue;
import com.mountaintevent.maps.UtilityMap;
import com.mountaintevent.maps.Utils;
import com.mountaintevent.mechanics.EventManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Player p = event.getPlayer();
        if (!Utils.inScope(p)) return;

        event.setCancelled(true);
    }

    // NO JUMP EVENT HANDLER
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!Utils.inScope(p)) return;

        Location from = e.getFrom();
        Location to = e.getTo();

        // Hanya peduli kalau Y naik
        if (to.getY() <= from.getY() + 1e-6) return;

        // Allow naik legit: air/ladder/vine/bubble/di atas block padat atau step naik (slab/stairs)
        Block feet = to.getBlock();
        Block below = to.clone().add(0, -1, 0).getBlock();

        Material mFeet = feet.getType();
        boolean climbable =
                mFeet == Material.WATER || mFeet == Material.BUBBLE_COLUMN ||
                        mFeet == Material.LADDER || mFeet == Material.VINE ||
                        mFeet.name().endsWith("_STAIRS") || mFeet.name().endsWith("_SLAB");

        boolean steppingUp = below.getType().isSolid(); // naik karena ada block di bawah

        if (climbable || steppingUp) return; // biarkan

        // Selain itu, hard-block kenaikan dengan clamp Y ke from (minim rubberband)
        Location fixed = to.clone();
        fixed.setY(from.getY());
        e.setTo(fixed);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Hanya berikan bendera jika event sedang aktif
        if (Mountaintevent.getInstance().eventActive && !Utils.hasMountainFlag(player)) {
            Bukkit.getScheduler().runTaskLater(Mountaintevent.getInstance(), () -> {
                if (player.isOnline() && !Utils.hasMountainFlag(player)) {
                    // Clear
                    player.getInventory().clear();

                    ItemStack flag = Utils.createFlag(player.getUniqueId());
                    player.getInventory().addItem(flag);

                    // Give oak slab to new joiners during active event
                    ItemStack oakSlab = new ItemStack(Material.OAK_SLAB, 64);
                    player.getInventory().addItem(oakSlab);

                    player.sendMessage(ChatColor.GREEN + "Bendera Merah Putih diberikan!");
                    player.sendMessage(ChatColor.AQUA + "Oak Slab x64 diberikan untuk building!");
                    player.sendMessage(ChatColor.GOLD + "Event Mountain sedang berlangsung!");
                    if (ConfigValue.noJumpEnabled) {
                        player.sendMessage(ChatColor.RED + "⚠️ JUMPING DILARANG SELAMA EVENT! ⚠️");
                    }

                    // Initialize jump tracking for new player
                    UtilityMap.playerLastY.put(player.getUniqueId(), player.getLocation().getY());
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        UtilityMap.trackPlayer.remove(playerId);
        UtilityMap.playerLastY.remove(playerId);
        player.removeScoreboardTag("mountainEvent");

    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        // Cek apakah yang dipasang adalah bendera khusus
        if (!Utils.isMountainFlag(item)) {
            return;
        }

        // Cek apakah event aktif dan belum selesai
        if (!Mountaintevent.getInstance().eventActive || Mountaintevent.getInstance().eventFinished) {
            event.setCancelled(true);
            if (Mountaintevent.getInstance().eventFinished) {
                player.sendMessage(ChatColor.RED + "Event sudah selesai!");
            } else {
                player.sendMessage(ChatColor.RED + "Event belum dimulai!");
            }
            return;
        }

        Location location = event.getBlockPlaced().getLocation();

        // Cek ketinggian
        if (location.getY() < ConfigValue.MIN_HEIGHT) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Ketinggian tidak cukup! Minimal Y = " + ConfigValue.MIN_HEIGHT);
            return;
        }

        // Cek kepemilikan
        UUID flagOwnerUUID = Utils.getFlagOwner(item);
        if (flagOwnerUUID == null || !flagOwnerUUID.equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Bukan bendera Anda!");
            return;
        }

        // Cek apakah player sudah memasang bendera lain
        for (Location loc : UtilityMap.flagOwners.keySet()) {
            if (UtilityMap.flagOwners.get(loc).equals(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Anda sudah memasang bendera!");
                return;
            }
        }

        // Simpan informasi pemilik bendera
        UtilityMap.flagOwners.put(location, player.getUniqueId());

        // Calculate distance from main spawn
        String distanceText = "";
        if (ConfigValue.mainSpawnLocation != null && ConfigValue.mainSpawnLocation.getWorld().equals(location.getWorld())) {
            double distance = location.distance(ConfigValue.mainSpawnLocation);
            distanceText = " (jarak: " + (int)distance + "m)";
        }

        // Broadcast ke semua player
        Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + ChatColor.WHITE +
                " memasang bendera di Y=" + (int)location.getY() + distanceText + "!");
        Bukkit.broadcastMessage(ChatColor.RED + "10 detik untuk menghancurkannya!");

        // Start countdown timer
        startFlagTimer(location, player);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();

        // Cek apakah block yang dihancurkan adalah bendera yang terdaftar
        if (!UtilityMap.flagOwners.containsKey(location)) {
            return;
        }

        // Cek apakah block adalah banner
        if (block.getType() != Material.RED_BANNER &&
                block.getType() != Material.RED_WALL_BANNER) {
            return;
        }

        UUID ownerUUID = UtilityMap.flagOwners.get(location);
        Player owner = Bukkit.getPlayer(ownerUUID);
        Player breaker = event.getPlayer();

        // Cancel timer
        BukkitTask timer = UtilityMap.flagTimers.get(location);
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
            if (!Utils.hasMountainFlag(owner)) {
                ItemStack flag = Utils.createFlag(ownerUUID);
                owner.getInventory().addItem(flag);
            }
        }

        // Hapus dari tracking
        UtilityMap.flagOwners.remove(location);
        UtilityMap.flagTimers.remove(location);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        if (Utils.isMountainFlag(item)) {
            event.setCancelled(true);
        }
    }

    private void startFlagTimer(Location location, Player player) {
        EventManager eventManager = new EventManager();
        BukkitTask timer = Bukkit.getScheduler().runTaskLater(Mountaintevent.getInstance(), () -> {
            // Cek apakah bendera masih ada dan event belum selesai
            if (UtilityMap.flagOwners.containsKey(location) && !Mountaintevent.getInstance().eventFinished) {
                // Player menang!
                UtilityMap.playerWins.put(player.getUniqueId(), UtilityMap.playerWins.getOrDefault(player.getUniqueId(), 0) + 1);
                UtilityMap.winners.add(player.getUniqueId());
                UtilityMap.trackPlayer.remove(player.getUniqueId());

                int winnerRank = UtilityMap.winners.size();

                Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");
                Bukkit.broadcastMessage(ChatColor.GREEN + "🏆 " + player.getName() + " JUARA " + winnerRank + "! 🏆");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Bendera bertahan selama 10 detik!");
                Bukkit.broadcastMessage(ChatColor.GRAY + "Total kemenangan: " + UtilityMap.playerWins.get(player.getUniqueId()));

                // Show distance from main spawn if available
                if (ConfigValue.mainSpawnLocation != null && ConfigValue.mainSpawnLocation.getWorld().equals(location.getWorld())) {
                    double distance = location.distance(ConfigValue.mainSpawnLocation);
                    Bukkit.broadcastMessage(ChatColor.AQUA + "Jarak dari pusat spawn: " + (int)distance + " blok");
                }

                Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");

                // Hapus bendera dari dunia
                Block flagBlock = location.getBlock();
                if (flagBlock.getType() == Material.RED_BANNER || flagBlock.getType() == Material.RED_WALL_BANNER) {
                    flagBlock.setType(Material.AIR);
                }

                // Teleport pemenang ke lokasi juara
                player.setGameMode(GameMode.SPECTATOR);
                player.removeScoreboardTag("mountainEvent");
                if (UtilityMap.trackPlayer.isEmpty()) {
                    teleportWinnerToLocation(player, winnerRank);
                }

                UtilityMap.flagOwners.remove(location);
                UtilityMap.flagTimers.remove(location);

                // Cek apakah sudah ada 3 pemenang
                if (UtilityMap.winners.size() >= 3) {
                    eventManager.finishEvent();
                } else {
                    // Show leaderboard sementara
                    Bukkit.getScheduler().runTaskLater(Mountaintevent.getInstance(), () -> {
                        Bukkit.broadcastMessage(ChatColor.AQUA + "Masih tersisa " + (3 - UtilityMap.winners.size()) + " posisi juara!");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            Utils.showLeaderboard(p);
                        }
                    }, 40L); // 2 detik setelah announcement
                }
            }
        }, 200L); // 10 detik = 200 ticks

        UtilityMap.flagTimers.put(location, timer);
    }

    private void teleportWinnerToLocation(Player player, int rank) {
        Location targetLocation = null;
        String rankName = switch (rank) {
            case 1 -> {
                targetLocation = ConfigValue.juara1Location;
                yield "JUARA 1";
            }
            case 2 -> {
                targetLocation = ConfigValue.juara2Location;
                yield "JUARA 2";
            }
            case 3 -> {
                targetLocation = ConfigValue.juara3Location;
                yield "JUARA 3";
            }
            default -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "spawn " + player.getName());
                yield "JUARA " + rank;
            }
        };

        if (targetLocation != null && player.isOnline()) {
            Location finalTargetLocation = targetLocation;
            Bukkit.getScheduler().runTaskLater(Mountaintevent.getInstance(), () -> {
                player.teleport(finalTargetLocation);
                player.sendTitle(
                        ChatColor.GOLD + "🏆 " + rankName + " 🏆",
                        ChatColor.YELLOW + "Selamat atas kemenangan Anda!",
                        10, 70, 20
                );

                // Give celebration effects
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 2, 0), 50, 2, 2, 2, 0.1);

                // Update jump tracking after teleport
                UtilityMap.playerLastY.put(player.getUniqueId(), finalTargetLocation.getY());

            }, 60L); // 3 detik delay untuk effect
        } else {
            player.sendMessage(ChatColor.RED + "Lokasi " + rankName.toLowerCase() + " belum diset! Hubungi admin.");
            Mountaintevent.getInstance().getLogger().warning("Lokasi " + rankName + " belum diset! Gunakan /mountaint set juara " + rank);
        }
    }
}
