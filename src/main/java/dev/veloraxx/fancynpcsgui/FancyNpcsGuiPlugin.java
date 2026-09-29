package dev.veloraxx.fancynpcsgui;

import dev.veloraxx.fancynpcsgui.gui.Menus;
import dev.veloraxx.fancynpcsgui.input.ChatInputManager;
import dev.veloraxx.fancynpcsgui.integration.FancyNpcsBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FancyNpcsGuiPlugin extends JavaPlugin {
    private FancyNpcsBridge bridge;
    private ChatInputManager input;

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            getLogger().severe("FancyNpcs is required and must load before FancyNpcsGUI.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            bridge = new FancyNpcsBridge(this);
            bridge.checkCompatibility();
        } catch (LinkageError | RuntimeException failure) {
            getLogger().severe("Incompatible FancyNpcs API: " + failure.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        input = new ChatInputManager(this);
        Menus menus = new Menus(this, bridge, input);
        Bukkit.getPluginManager().registerEvents(input, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(bridge, this);
        bridge.refreshClients();
        PluginCommand command = getCommand("npcgui");
        if (command != null) {
            command.setExecutor((sender, ignored, label, args) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This command is for players.");
                } else if (!player.hasPermission("fancynpcsgui.admin") || !player.hasPermission("fancynpcsgui.use")) {
                    player.sendMessage("You do not have permission to manage NPCs.");
                } else if (!bridge.ready()) {
                    player.sendMessage("FancyNpcs is still loading its NPCs. Try again shortly.");
                } else {
                    menus.browser(player, 0);
                }
                return true;
            });
        }
    }

    @Override
    public void onDisable() {
        if (input != null) input.clear();
        if (bridge != null && input != null) bridge.close();
    }
}
