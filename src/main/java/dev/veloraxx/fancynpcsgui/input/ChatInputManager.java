package dev.veloraxx.fancynpcsgui.input;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ChatInputManager implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ChatInputManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, String prompt, Function<String, String> apply, Runnable returnToMenu) {
        ask(player, prompt, apply, returnToMenu, returnToMenu);
    }

    public void ask(Player player, String prompt, Function<String, String> apply, Runnable onSuccess, Runnable onCancel) {
        cancel(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(Component.text(prompt, NamedTextColor.AQUA)
                .append(Component.text(" (Type cancel to return; 60 seconds.)", NamedTextColor.GRAY)));
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session expired = sessions.remove(player.getUniqueId());
            if (expired != null && player.isOnline()) {
                player.sendMessage(Component.text("Input timed out.", NamedTextColor.GRAY));
                expired.onCancel.run();
            }
        }, 1200L);
        sessions.put(player.getUniqueId(), new Session(apply, onSuccess, onCancel, timeout));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        event.viewers().clear();
        String value = PlainTextComponentSerializer.plainText().serialize(event.originalMessage()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.get(player.getUniqueId()) != session || !player.isOnline()) return;
            if (!player.hasPermission("fancynpcsgui.admin") || !player.hasPermission("fancynpcsgui.use")) {
                cancel(player.getUniqueId());
                return;
            }
            if (value.equalsIgnoreCase("cancel")) {
                cancel(player.getUniqueId());
                session.onCancel.run();
                return;
            }
            String error;
            try {
                error = session.apply.apply(value);
            } catch (RuntimeException failure) {
                error = failure.getMessage() == null ? "That value could not be applied." : failure.getMessage();
            }
            if (error != null) {
                player.sendMessage(Component.text(error, NamedTextColor.RED)
                        .append(Component.text(" Try again or type cancel.", NamedTextColor.GRAY)));
                return;
            }
            cancel(player.getUniqueId());
            session.onSuccess.run();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    private void cancel(UUID id) {
        Session session = sessions.remove(id);
        if (session != null) session.timeout.cancel();
    }

    public void cancel(Player player) {
        cancel(player.getUniqueId());
    }

    public void clear() {
        for (UUID id : sessions.keySet()) cancel(id);
    }

    private record Session(Function<String, String> apply, Runnable onSuccess, Runnable onCancel, BukkitTask timeout) {}
}
