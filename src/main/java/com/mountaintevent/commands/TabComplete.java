package com.mountaintevent.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TabComplete implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
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
}
