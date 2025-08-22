package com.mountaintevent;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.mountaintevent.commands.MainCommands;
import com.mountaintevent.commands.TabComplete;
import com.mountaintevent.config.ConfigValue;
import com.mountaintevent.listener.PlayerListener;
import com.mountaintevent.maps.UtilityMap;
import com.mountaintevent.maps.Utils;
import net.kyori.adventure.text.Component;
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
import org.bukkit.event.player.PlayerQuitEvent;
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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

public class Mountaintevent extends JavaPlugin {

    private static Mountaintevent instance;

    public NamespacedKey flagKey;
    public NamespacedKey ownerKey;

    public boolean eventActive = false;
    public boolean eventFinished = false;

    @Override
    public void onEnable() {
        instance = this;
        this.flagKey = new NamespacedKey(this, "mountain_flag");
        this.ownerKey = new NamespacedKey(this, "flag_owner");

        // Create config if doesn't exist
        ConfigValue.createDefaultConfig();
        ConfigValue.loadConfigValues();

        getServer().getPluginManager().registerEvents(new PlayerListener(), this);

        // Register tab completer
        this.getCommand("mountaint").setExecutor(new MainCommands());
        this.getCommand("mountaint").setTabCompleter(new TabComplete());

        getLogger().info("MountainEvent plugin telah aktif!");
        getLogger().info("Gunakan /mtstart untuk memulai event!");
        getLogger().info("Ketinggian minimal: Y >= " + ConfigValue.MIN_HEIGHT);
        getLogger().info("Event World: " + ConfigValue.eventWorldName);
        getLogger().info("No Jump: " + (ConfigValue.noJumpEnabled ? "ENABLED" : "DISABLED"));
        if (ConfigValue.mainSpawnLocation != null) {
            getLogger().info("Main Spawn: " + Utils.formatLocation(ConfigValue.mainSpawnLocation));
        } else {
            getLogger().warning("Main spawn belum diset! Gunakan /mountaint set spawn");
        }
    }

    @Override
    public void onDisable() {
        // Cancel semua timer yang masih berjalan
        for (BukkitTask task : UtilityMap.flagTimers.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        UtilityMap.flagTimers.clear();

        // Clear tracking data
        UtilityMap.playerLastY.clear();

        getLogger().info("MountainEvent plugin telah dinonaktifkan!");
    }

    public static Mountaintevent getInstance() {
        return instance;
    }
}