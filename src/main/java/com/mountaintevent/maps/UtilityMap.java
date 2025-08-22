package com.mountaintevent.maps;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class UtilityMap {

    public static final Map<Location, UUID> flagOwners = new HashMap<>();
    public static final Map<Location, BukkitTask> flagTimers = new HashMap<>();
    public static final Map<UUID, Integer> playerWins = new HashMap<>();
    public static final Map<UUID, Double> playerLastY = new HashMap<>();
    public static final List<UUID> winners = new ArrayList<>();
    public static final List<UUID> trackPlayer = new ArrayList<>();

}
