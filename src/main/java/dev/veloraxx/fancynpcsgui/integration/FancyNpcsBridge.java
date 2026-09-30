package dev.veloraxx.fancynpcsgui.integration;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.actions.NpcAction;
import de.oliver.fancynpcs.api.actions.NpcAction.NpcActionData;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import de.oliver.fancynpcs.api.events.NpcSpawnEvent;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import de.oliver.fancynpcs.api.events.NpcsLoadedEvent;
import de.oliver.fancynpcs.api.events.NpcRemoveEvent;
import de.oliver.fancynpcs.libs.chatcolorhandler.paper.PaperColor;
import de.oliver.fancysitula.api.entities.FS_RealPlayer;
import de.oliver.fancysitula.api.entities.FS_TextDisplay;
import de.oliver.fancysitula.api.entities.FS_Display.Billboard;
import de.oliver.fancysitula.api.packets.FS_Color;
import de.oliver.fancysitula.api.packets.FS_ClientboundCreateOrUpdateTeamPacket.CreateTeam;
import de.oliver.fancysitula.api.packets.FS_ClientboundCreateOrUpdateTeamPacket.RemoveTeam;
import de.oliver.fancysitula.api.packets.FS_ClientboundPlayerInfoUpdatePacket;
import de.oliver.fancysitula.api.packets.FS_ClientboundSetEntityDataPacket.EntityData;
import de.oliver.fancysitula.api.teams.FS_CollisionRule;
import de.oliver.fancysitula.api.teams.FS_NameTagVisibility;
import de.oliver.fancysitula.api.utils.FS_GameProfile;
import de.oliver.fancysitula.api.utils.FS_GameType;
import de.oliver.fancysitula.api.utils.entityData.FS_EntityData;
import de.oliver.fancysitula.factories.PacketFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class FancyNpcsBridge implements Listener {
    private final FancyNpcsPlugin api = FancyNpcsPlugin.get();
    private final JavaPlugin plugin;
    private final PacketFactory packets = new PacketFactory();
    private final Map<UUID, Map<String, String>> teams = new HashMap<>();
    private final Map<UUID, Map<String, FS_TextDisplay>> nameLabels = new HashMap<>();
    private final Set<UUID> queuedRefreshes = ConcurrentHashMap.newKeySet();

    public FancyNpcsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!ready()) return;
            for (UUID id : new ArrayList<>(nameLabels.keySet())) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) updateNameLabels(player);
            }
        }, 10L, 10L);
    }

    public void checkCompatibility() {
        if (api == null || api.getNpcManager() == null || api.getNpcAdapter() == null
                || api.getActionManager() == null || api.getAttributeManager() == null) {
            throw new IllegalStateException("FancyNpcs 2.x API services are unavailable");
        }
        api.getNpcManager().isLoaded();
        api.getActionManager().getAllActions();
        api.getAttributeManager().getAllAttributes();
    }

    public boolean ready() {
        return api.getNpcManager().isLoaded();
    }

    public List<Npc> all() {
        return api.getNpcManager().getAllNpcs().stream()
                .sorted(Comparator.comparing(n -> n.getData().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Npc find(String id) {
        return api.getNpcManager().getNpcById(id);
    }

    public boolean nameAvailable(String name) {
        return name.matches("[A-Za-z0-9/_-]+") && api.getNpcManager().getNpc(name) == null;
    }

    public Npc create(Player player, String name, EntityType type) {
        if (!ready()) throw new IllegalStateException("FancyNpcs NPC manager is still loading");
        NpcData data = new NpcData(name, player.getUniqueId(), player.getLocation().clone());
        data.setType(type);
        Npc npc = api.getNpcAdapter().apply(data);
        npc.create();
        api.getNpcManager().registerNpc(npc);
        npc.spawnForAll();
        npc.setDirty(true);
        save();
        return npc;
    }

    public void delete(Npc npc) {
        npc.removeForAll();
        api.getNpcManager().removeNpc(npc);
        plugin.getConfig().set("editor-shortcuts." + npc.getData().getId(), null);
        plugin.getConfig().set("name-backgrounds." + npc.getData().getId(), null);
        plugin.saveConfig();
        refreshClients();
        save();
    }

    public boolean copy(Player player, Npc npc, String name) {
        if (!player.hasPermission("fancynpcs.command.npc.copy")) return false;
        if (!Bukkit.dispatchCommand(player, "npc copy " + npc.getData().getId() + " " + name)) return false;
        Npc copy = api.getNpcManager().getNpc(name);
        if (copy == null) return false;
        editorShortcut(copy, editorShortcut(npc));
        nameBackground(copy, nameBackground(npc));
        save();
        return true;
    }

    public boolean skin(Player player, Npc npc, String value, boolean slim) {
        if (!player.hasPermission("fancynpcs.command.npc.skin")) return false;
        return Bukkit.dispatchCommand(player, "npc skin " + npc.getData().getId() + " " + value + (slim ? " --slim" : ""));
    }

    public void changed(Npc npc) {
        npc.updateForAll(false);
        npc.checkAndUpdateVisibilityForAll();
        refreshClients();
        save();
    }

    public void recreated(Npc npc) {
        npc.removeForAll();
        npc.create();
        npc.spawnForAll();
        refreshClients();
        save();
    }

    public void moved(Npc npc, Location location) {
        npc.getData().setLocation(location.clone());
        npc.moveForAll();
        save();
    }

    public void type(Npc npc, EntityType type) {
        NpcData data = npc.getData();
        data.clearAttributes();
        data.setType(type);
        if (type != EntityType.PLAYER) {
            data.setShowInTab(false).setSkinData(null).setMirrorSkin(false);
        }
        recreated(npc);
    }

    public void equipment(Npc npc, NpcEquipmentSlot slot, ItemStack item) {
        // FancyNpcs only sends equipment updates for slots present in this map.
        if (item == null || item.getType().isAir()) npc.getData().addEquipment(slot, new ItemStack(Material.AIR));
        else npc.getData().addEquipment(slot, item.clone());
        npc.getData().setEquipment(npc.getData().getEquipment());
        changed(npc);
    }

    public List<NpcAttribute> attributes(EntityType type) {
        return api.getAttributeManager().getAllAttributesForEntityType(type).stream()
                .sorted(Comparator.comparing(NpcAttribute::getName)).toList();
    }

    public void attribute(Npc npc, NpcAttribute attribute, String value) {
        if (!attributes(npc.getData().getType()).contains(attribute)) throw new IllegalArgumentException("That attribute no longer applies to this NPC.");
        npc.getData().addAttribute(attribute, value);
        attribute.apply(npc, value);
        changed(npc);
    }

    public void resetAttribute(Npc npc, NpcAttribute attribute) {
        npc.getData().removeAttribute(attribute);
        recreated(npc);
    }

    public boolean editorShortcut(Npc npc) {
        return plugin.getConfig().getBoolean("editor-shortcuts." + npc.getData().getId(), true);
    }

    public void editorShortcut(Npc npc, boolean enabled) {
        plugin.getConfig().set("editor-shortcuts." + npc.getData().getId(), enabled ? null : false);
        plugin.saveConfig();
    }

    public boolean nameBackground(Npc npc) {
        return plugin.getConfig().getBoolean("name-backgrounds." + npc.getData().getId(), true);
    }

    public void nameBackground(Npc npc, boolean enabled) {
        plugin.getConfig().set("name-backgrounds." + npc.getData().getId(), enabled ? null : false);
        plugin.saveConfig();
    }

    public void refreshClients() {
        for (Player player : Bukkit.getOnlinePlayers()) refreshClient(player);
    }

    private void refreshClient(Player player) {
        if (!queuedRefreshes.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            queuedRefreshes.remove(player.getUniqueId());
            if (player.isOnline() && ready()) applyClientState(player);
        }, 2L);
    }

    private void applyClientState(Player player) {
        Map<String, String> previous = teams.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        FS_RealPlayer viewer = new FS_RealPlayer(player);
        for (String team : previous.values()) packets.createCreateOrUpdateTeamPacket(team, new RemoveTeam()).send(viewer);
        previous.clear();
        List<Npc> sorted = new ArrayList<>(all());
        sorted.sort(Comparator.comparing((Npc n) -> PlainTextComponentSerializer.plainText().serialize(displayName(n, player)),
                String.CASE_INSENSITIVE_ORDER).thenComparing(n -> n.getData().getId()));
        for (int i = 0; i < sorted.size(); i++) {
            Npc npc = sorted.get(i);
            if (!npc.isShownFor(player)) continue;
            NpcData data = npc.getData();
            Entity entity;
            try {
                Object handle = npc.getClass().getMethod("getNpc").invoke(npc);
                entity = (Entity) handle.getClass().getMethod("getBukkitEntity").invoke(handle);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("This FancyNpcs adapter does not expose its entity", failure);
            }
            if (entity instanceof LivingEntity living) living.setCollidable(data.isCollidable());
            String entry = entity instanceof Player fake ? fake.getName() : entity.getUniqueId().toString();
            Component name = displayName(npc, player);
            boolean hidden = data.getDisplayName().equalsIgnoreCase("<empty>");
            // Vanilla nametags cannot render multiple lines or disable their background.
            boolean label = !nameBackground(npc) || PlainTextComponentSerializer.plainText().serialize(name).contains("\n");
            String team = String.format(java.util.Locale.ROOT, "fg%08d", i);
            CreateTeam settings = new CreateTeam(Component.text(data.getName()), false, false,
                    hidden || label ? FS_NameTagVisibility.NEVER : FS_NameTagVisibility.ALWAYS,
                    data.isCollidable() ? FS_CollisionRule.ALWAYS : FS_CollisionRule.NEVER,
                    FS_Color.valueOf(data.getGlowingColor().toString().toUpperCase(java.util.Locale.ROOT)),
                    entity instanceof Player ? name : Component.empty(), Component.empty(), List.of(entry));
            packets.createCreateOrUpdateTeamPacket(team, settings).send(viewer);
            packets.createSetEntityDataPacket(entity.getEntityId(), List.of(new EntityData(
                    FS_EntityData.CUSTOM_NAME_VISIBLE, !(entity instanceof Player) && !hidden && !label))).send(viewer);
            previous.put(data.getId(), team);
            if (entity instanceof Player fake) {
                FS_GameProfile profile = FS_GameProfile.fromBukkit(data.isMirrorSkin() ? player.getPlayerProfile() : fake.getPlayerProfile());
                profile.setUUID(entity.getUniqueId());
                profile.setName(entry);
                var info = new FS_ClientboundPlayerInfoUpdatePacket.Entry(entity.getUniqueId(),
                        profile, data.isShowInTab(), 0, FS_GameType.SURVIVAL, name);
                var flags = EnumSet.of(FS_ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                        FS_ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
                if (data.isShowInTab()) flags.add(FS_ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
                packets.createPlayerInfoUpdatePacket(flags, List.of(info)).send(viewer);
            }
        }
        updateNameLabels(player);
    }

    private void updateNameLabels(Player player) {
        Map<String, FS_TextDisplay> labels = nameLabels.get(player.getUniqueId());
        FS_RealPlayer viewer = new FS_RealPlayer(player);
        Set<String> visible = new HashSet<>();
        boolean needed = false;
        for (Npc npc : api.getNpcManager().getAllNpcs()) {
            NpcData data = npc.getData();
            Location location = data.getLocation();
            if (!npc.isShownFor(player) || !player.getWorld().equals(location.getWorld())
                    || data.getDisplayName().equalsIgnoreCase("<empty>")) continue;
            Component text = displayName(npc, player);
            String plain = PlainTextComponentSerializer.plainText().serialize(text);
            if (plain.isBlank() || (nameBackground(npc) && !plain.contains("\n"))) continue;
            needed = true;
            if (labels == null) {
                labels = new HashMap<>();
                nameLabels.put(player.getUniqueId(), labels);
            }
            if (player.getLocation().distanceSquared(location) > 4096) continue;
            visible.add(data.getId());
            FS_TextDisplay label = labels.get(data.getId());
            boolean spawn = label == null;
            if (spawn) {
                label = new FS_TextDisplay();
                label.setStyleFlags((byte) 0);
                label.setShadow(true);
                label.setBillboard(Billboard.CENTER);
                label.setBrightnessOverride(0xF000F0);
                label.setViewRange(1);
                label.setLineWidth(1000);
                label.setTextOpacity((byte) 255);
                labels.put(data.getId(), label);
            }
            boolean background = nameBackground(npc);
            boolean changed = spawn || !text.equals(label.getText()) || label.isUsingDefaultBackground() != background;
            label.setText(text);
            label.setBackground(0);
            label.setUseDefaultBackground(background);
            double y = location.getY() + npc.getEyeHeight() + 0.5 * data.getScale();
            if (spawn) {
                label.setLocation(location.getX(), y, location.getZ());
                packets.createAddEntityPacket(label.getId(), label.getUuid(), EntityType.TEXT_DISPLAY,
                        label.getX(), label.getY(), label.getZ(), 0, 0, 0, 0, 0, 0, 0).send(viewer);
            } else if (label.getX() != location.getX() || label.getY() != y || label.getZ() != location.getZ()) {
                label.setLocation(location.getX(), y, location.getZ());
                packets.createTeleportEntityPacket(label.getId(), label.getX(), label.getY(), label.getZ(), 0, 0, false).send(viewer);
            }
            if (changed) packets.createSetEntityDataPacket(label.getId(),
                    label.getEntityData().stream().filter(value -> value.getValue() != null).toList()).send(viewer);
        }
        if (labels == null) return;
        var entries = labels.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (visible.contains(entry.getKey())) continue;
            packets.createRemoveEntitiesPacket(List.of(entry.getValue().getId())).send(viewer);
            entries.remove();
        }
        if (labels.isEmpty() && !needed) nameLabels.remove(player.getUniqueId());
    }

    private Component displayName(Npc npc, Player player) {
        String value = npc.getData().getDisplayName().replace("\\n", "\n");
        return value.equalsIgnoreCase("<empty>") ? Component.text(npc.getData().getName()) : (Component) PaperColor.handler().translate(value, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(NpcSpawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            Map<String, FS_TextDisplay> labels = nameLabels.get(player.getUniqueId());
            if (labels != null) {
                FS_TextDisplay label = labels.remove(event.getNpc().getData().getId());
                if (label != null) packets.createRemoveEntitiesPacket(List.of(label.getId())).send(new FS_RealPlayer(player));
            }
            refreshClient(player);
        });
        // A previous hidden spawn can still have a delayed player-list removal queued.
        long delay = Math.max(1L, api.getFancyNpcConfig().getRemoveNpcsFromPlayerlistDelay() / 50L + 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) refreshClient(event.getPlayer());
        }, delay);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onModify(NpcModifyEvent event) {
        refreshClients();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Npc npc = find(event.getNpc().getData().getId());
            if (npc != null && npc.getData().getDisplayName().contains("\\n")) {
                npc.getData().setDisplayName(npc.getData().getDisplayName().replace("\\n", "\n"));
                npc.updateForAll(false);
            }
            save();
        });
    }

    @EventHandler
    public void onLoad(NpcsLoadedEvent event) {
        refreshClients();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemove(NpcRemoveEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (find(event.getNpc().getData().getId()) == null) {
                plugin.getConfig().set("editor-shortcuts." + event.getNpc().getData().getId(), null);
                plugin.getConfig().set("name-backgrounds." + event.getNpc().getData().getId(), null);
                plugin.saveConfig();
            }
            refreshClients();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teams.remove(event.getPlayer().getUniqueId());
        nameLabels.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        nameLabels.remove(event.getPlayer().getUniqueId());
        refreshClient(event.getPlayer());
    }

    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, String> existing = teams.get(player.getUniqueId());
            if (existing != null) for (String team : existing.values())
                packets.createCreateOrUpdateTeamPacket(team, new RemoveTeam()).send(new FS_RealPlayer(player));
            Map<String, FS_TextDisplay> labels = nameLabels.get(player.getUniqueId());
            if (labels != null && !labels.isEmpty()) packets.createRemoveEntitiesPacket(
                    labels.values().stream().map(FS_TextDisplay::getId).toList()).send(new FS_RealPlayer(player));
        }
        teams.clear();
        nameLabels.clear();
        queuedRefreshes.clear();
        if (Bukkit.getPluginManager().isPluginEnabled("FancyNpcs") && ready()) {
            for (Npc npc : all()) {
                npc.getIsTeamCreated().clear();
                npc.updateForAll(false);
            }
        }
    }

    public List<NpcAction> actionTypes() {
        return api.getActionManager().getAllActions().stream()
                .filter(action -> !action.getName().equals("unknown"))
                .sorted(Comparator.comparingInt((NpcAction action) -> action.getName().contains("command") ? 0
                        : action.getName().equals("message") || action.getName().equals("send_to_server") ? 1 : 2)
                        .thenComparing(NpcAction::getName)).toList();
    }

    public List<NpcActionData> actions(Npc npc, ActionTrigger trigger) {
        return npc.getData().getActions(trigger).stream()
                .sorted(Comparator.comparingInt(NpcActionData::order)).toList();
    }

    public void setActions(Npc npc, ActionTrigger trigger, List<NpcActionData> actions) {
        List<NpcActionData> ordered = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            NpcActionData action = actions.get(i);
            ordered.add(new NpcActionData(i + 1, action.action(), action.value()));
        }
        npc.getData().setActions(trigger, ordered);
        save();
    }

    public List<EntityType> types() {
        List<EntityType> result = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (type == EntityType.PLAYER || type.isSpawnable()) result.add(type);
        }
        result.sort(Comparator.comparing(Enum::name));
        return result;
    }

    public void save() {
        api.getNpcManager().saveNpcs(false);
    }
}
