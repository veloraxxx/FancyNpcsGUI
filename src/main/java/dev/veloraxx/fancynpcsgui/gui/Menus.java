package dev.veloraxx.fancynpcsgui.gui;

import com.destroystokyo.paper.profile.ProfileProperty;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.actions.NpcAction;
import de.oliver.fancynpcs.api.actions.NpcAction.NpcActionData;
import de.oliver.fancynpcs.api.data.property.NpcVisibility;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import de.oliver.fancynpcs.api.events.NpcPreInteractEvent;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import dev.veloraxx.fancynpcsgui.input.ChatInputManager;
import dev.veloraxx.fancynpcsgui.integration.FancyNpcsBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class Menus implements Listener {
    private final JavaPlugin plugin;
    private final FancyNpcsBridge bridge;
    private final ChatInputManager input;

    public Menus(JavaPlugin plugin, FancyNpcsBridge bridge, ChatInputManager input) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.input = input;
    }

    public void browser(Player player, int page) {
        input.cancel(player);
        browser(player, page, "");
    }

    private void browser(Player player, int page, String search) {
        List<Npc> npcs = bridge.all().stream().filter(n -> n.getData().getName().toLowerCase(Locale.ROOT)
                .contains(search.toLowerCase(Locale.ROOT))).toList();
        int current = clamp(page, npcs.size(), 45);
        Menu menu = menu("NPC Browser · " + (current + 1), 54);
        for (int i = current * 45; i < Math.min(npcs.size(), (current + 1) * 45); i++) {
            Npc npc = npcs.get(i);
            NpcData data = npc.getData();
            Location location = data.getLocation();
            int slot = i - current * 45;
            menu.button(slot, icon(data.getType()), data.getName(),
                    "Type: " + data.getType().name(),
                    "Display: " + shortText(data.getDisplayName()),
                    "World: " + world(location),
                    "Position: " + coords(location),
                    "Left: edit · Right: teleport", click -> {
                Npc live = bridge.find(data.getId());
                if (live == null) { missing(player); return; }
                if (click.isRightClick()) {
                    guard(player, "edit", () -> teleport(player, live));
                } else {
                    editor(player, data.getId());
                }
            });
            menu.npcHead(slot, npc, player);
        }
        if (current > 0) menu.button(45, Material.ARROW, "Previous page", "Open earlier NPCs", c -> browser(player, current - 1, search));
        menu.button(47, Material.COMPASS, "Search", search.isEmpty() ? "Search by internal name" : "Filter: " + search,
                c -> input.ask(player, "Enter a name fragment, or @none to clear:", value -> {
                    browser(player, 0, value.equalsIgnoreCase("@none") ? "" : value);
                    return null;
                }, () -> {}, () -> browser(player, current, search)));
        menu.button(49, Material.CLOCK, "Refresh", "Read live NPC data", c -> browser(player, current, search));
        menu.button(51, Material.EMERALD, "Create NPC", "Choose a name and entity type", c -> guard(player, "create", () -> create(player)));
        menu.button(53, Material.BARRIER, "Close", "Close this menu", c -> player.closeInventory());
        if ((current + 1) * 45 < npcs.size()) menu.button(52, Material.ARROW, "Next page", "Open later NPCs", c -> browser(player, current + 1, search));
        menu.open(player);
    }

    private void create(Player player) {
        input.ask(player, "Enter a unique NPC name (letters, digits, /, _ or -):", name -> {
            if (!bridge.ready()) return "FancyNpcs is still loading.";
            if (!bridge.nameAvailable(name)) return "Invalid or already used NPC name.";
            types(player, 0, type -> guard(player, "create", () -> {
                if (!bridge.nameAvailable(name)) { player.sendMessage("NPC name is now taken."); return; }
                Npc npc = bridge.create(player, name, type);
                feedback(player, "NPC created.");
                editor(player, npc.getData().getId());
            }), () -> browser(player, 0));
            return null;
        }, () -> {}, () -> browser(player, 0));
    }

    private void types(Player player, int page, Consumer<EntityType> selected, Runnable back) {
        List<EntityType> types = bridge.types();
        int current = clamp(page, types.size(), 45);
        Menu menu = menu("Entity type · " + (current + 1), 54);
        for (int i = current * 45; i < Math.min(types.size(), (current + 1) * 45); i++) {
            EntityType type = types.get(i);
            menu.button(i - current * 45, icon(type), label(type.name()), "Select this type", c -> selected.accept(type));
        }
        if (current > 0) menu.button(45, Material.ARROW, "Previous page", "", c -> types(player, current - 1, selected, back));
        menu.button(47, Material.PLAYER_HEAD, "Player (default)", "Select a player NPC", c -> selected.accept(EntityType.PLAYER));
        menu.button(49, Material.OAK_DOOR, "Back", "Return", c -> back.run());
        if ((current + 1) * 45 < types.size()) menu.button(53, Material.ARROW, "Next page", "", c -> types(player, current + 1, selected, back));
        menu.open(player);
    }

    private void editor(Player player, String id) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        NpcData data = npc.getData();
        Menu menu = menu("Edit · " + data.getName(), 54);
        menu.button(4, icon(data.getType()), data.getName(), "Type: " + data.getType(), "World: " + world(data.getLocation()),
                "Position: " + coords(data.getLocation()), c -> {});
        menu.npcHead(4, npc, player);
        menu.button(19, Material.PAINTING, "Appearance", "Name, type, skin and glow", c -> appearance(player, id));
        menu.button(20, Material.DIAMOND_CHESTPLATE, "Equipment", "Copy items into six slots", c -> equipment(player, id));
        menu.button(21, Material.COMPASS, "Behaviour", "Turning, visibility and cooldown", c -> behaviour(player, id));
        menu.button(22, Material.ENDER_PEARL, "Position / Rotation", "Move or rotate the NPC", c -> position(player, id));
        menu.button(23, Material.REPEATER, "Interactions", "Left-click, right-click and shared actions", c -> guard(player, "actions", () -> triggers(player, id)));
        menu.button(24, Material.WRITABLE_BOOK, "Attributes", "Entity-specific values", c -> attributes(player, id, 0));
        menu.button(25, Material.ANVIL, "Advanced / Management", "Copy or delete this NPC", c -> management(player, id));
        menu.button(45, Material.ARROW, "Back", "NPC browser", c -> browser(player, 0));
        menu.button(49, Material.CLOCK, "Refresh", "Read live data", c -> editor(player, id));
        menu.open(player);
    }

    private void appearance(Player player, String id) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        NpcData data = npc.getData();
        Menu menu = menu("Appearance · " + data.getName(), 36);
        menu.button(10, Material.NAME_TAG, "Display name", "Current: " + shortText(data.getDisplayName().replace("\n", "\\n")), "MiniMessage and \\n supported; @none clears", c -> edit(player, () ->
                input.ask(player, "Enter a display name (\\n for a new line), or @none to hide it:", value -> {
                    String error = change(id, n -> n.getData().setDisplayName(value.equalsIgnoreCase("@none") ? "<empty>" : value.replace("\\n", "\n")));
                    if (error == null) feedback(player, "Display name updated.");
                    return error;
                }, () -> appearance(player, id))));
        menu.button(11, Material.GLASS, "Name background", "Current: " + bridge.nameBackground(npc), "Show the box behind the display name", "Click to toggle",
                c -> change(id, n -> bridge.nameBackground(n, !bridge.nameBackground(n)), player, () -> appearance(player, id)));
        menu.button(12, Material.PLAYER_HEAD, "Skin", "Player NPCs only", c -> skin(player, id));
        menu.npcHead(12, npc, player);
        menu.button(13, icon(data.getType()), "Entity type", "Current: " + data.getType(), c -> edit(player, () -> types(player, 0,
                type -> change(id, n -> bridge.type(n, type), player, () -> appearance(player, id)), () -> appearance(player, id))));
        menu.npcHead(13, npc, player);
        menu.button(14, Material.GLOW_INK_SAC, "Glowing", "Current: " + data.isGlowing(), "Click to toggle", c -> change(id, n -> n.getData().setGlowing(!n.getData().isGlowing()), player, () -> appearance(player, id)));
        menu.button(15, Material.LIGHT_BLUE_DYE, "Glow color", "Current: " + data.getGlowingColor(), c -> colors(player, id));
        menu.button(16, Material.SLIME_BALL, "Scale", "Current: " + data.getScale(), c -> edit(player, () -> number(player, id, "Enter scale above 0 (maximum 16):", value -> {
            if (value <= 0 || value > 16) return "Scale must be between 0 and 16.";
            return change(id, n -> n.getData().setScale(value.floatValue()));
        }, () -> appearance(player, id))));
        menu.button(27, Material.ARROW, "Back", "Editor", c -> editor(player, id));
        menu.open(player);
    }

    private void skin(Player player, String id) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        if (npc.getData().getType() != EntityType.PLAYER) { player.sendMessage("Skins apply only to PLAYER NPCs."); return; }
        Menu menu = menu("Skin · " + npc.getData().getName(), 27);
        menu.refresh = () -> skin(player, id);
        menu.button(4, Material.PAINTING, "Saved skin", "Current: " + (npc.getData().getSkinData() == null ? "Default" : shortText(npc.getData().getSkinData().getIdentifier())),
                "Mirroring keeps this skin for later", c -> {});
        menu.button(10, Material.PLAYER_HEAD, "Player name", "Left: normal · Right: slim", c -> skinInput(player, id, "Player name", "", c.isRightClick()));
        menu.npcHead(10, npc, player);
        menu.button(11, Material.NAME_TAG, "UUID", "Left: normal · Right: slim", c -> skinInput(player, id, "UUID", "", c.isRightClick()));
        menu.button(12, Material.MAP, "Image URL", "Left: normal · Right: slim", c -> skinInput(player, id, "URL", "", c.isRightClick()));
        menu.button(13, Material.PAPER, "Local skin file", "From FancyNpcs/skins; no spaces", "Left: normal · Right: slim", c -> skinInput(player, id, "File", "file ", c.isRightClick()));
        menu.button(15, Material.ENDER_EYE, "Mirror player skin", "Current: " + npc.getData().isMirrorSkin(), "Each viewer sees their own skin", "Disable to restore the saved skin", c -> change(id, n -> {
            if (n.getData().getType() != EntityType.PLAYER) throw new IllegalArgumentException("Skins apply only to player NPCs.");
            n.getData().setMirrorSkin(!n.getData().isMirrorSkin());
            bridge.recreated(n);
        }, player, () -> skin(player, id)));
        menu.button(16, Material.BARRIER, "Clear skin", "Remove the saved skin and disable mirroring", c -> change(id, n -> {
            if (n.getData().getType() != EntityType.PLAYER) throw new IllegalArgumentException("Skins apply only to player NPCs.");
            n.getData().setMirrorSkin(false).setSkinData(null);
            bridge.recreated(n);
            feedback(player, "Skin cleared.");
        }, player, () -> skin(player, id)));
        menu.button(18, Material.ARROW, "Back", "Appearance", c -> appearance(player, id));
        menu.open(player);
    }

    private void skinInput(Player player, String id, String format, String prefix, boolean slim) {
        edit(player, () -> input.ask(player, "Enter skin " + format + ":", value -> {
            if (value.isBlank() || value.contains(" ") || value.contains("\n")) return "Use one non-empty value without spaces.";
            if (format.equals("UUID")) try { java.util.UUID.fromString(value); } catch (IllegalArgumentException ex) { return "Invalid UUID."; }
            if (format.equals("URL") && !(value.startsWith("https://") || value.startsWith("http://"))) return "Use an HTTP or HTTPS URL.";
            Npc npc = bridge.find(id);
            if (npc == null) return "NPC was removed.";
            if (!bridge.skin(player, npc, prefix + value, slim)) return "FancyNpcs rejected this skin command or permission.";
            return null;
        }, () -> skin(player, id)));
    }

    private void colors(Player player, String id) {
        Menu menu = menu("Glow color", 36);
        List<String> colors = new ArrayList<>(NamedTextColor.NAMES.keys());
        colors.sort(String::compareTo);
        for (int i = 0; i < Math.min(colors.size(), 45); i++) {
            String name = colors.get(i);
            Material dye = Material.matchMaterial(name.replace("dark_", "").replace("aqua", "cyan").replace("purple", "magenta") + "_DYE");
            menu.button(i, dye == null ? Material.GLOW_INK_SAC : dye, label(name), "Set glow color and enable glowing", c -> change(id, n -> {
                n.getData().setGlowingColor(NamedTextColor.NAMES.value(name)).setGlowing(true);
                feedback(player, "Glow color set to " + name.toUpperCase(Locale.ROOT) + ".");
            }, player, () -> appearance(player, id)));
        }
        menu.button(27, Material.ARROW, "Back", "Appearance", c -> appearance(player, id));
        menu.open(player);
    }

    private void equipment(Player player, String id) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        Menu menu = menu("Equipment · " + npc.getData().getName(), 45);
        NpcEquipmentSlot[] slots = {NpcEquipmentSlot.MAINHAND, NpcEquipmentSlot.OFFHAND,
                NpcEquipmentSlot.HEAD, NpcEquipmentSlot.CHEST, NpcEquipmentSlot.LEGS, NpcEquipmentSlot.FEET};
        menu.allowInventory = true;
        int[] positions = {11, 15, 4, 13, 22, 31};
        Material[] icons = {Material.IRON_SWORD, Material.SHIELD, Material.IRON_HELMET,
                Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS};
        String[] names = {"Main hand", "Off hand", "Helmet", "Chest", "Legs", "Feet"};
        for (int i = 0; i < slots.length; i++) {
            NpcEquipmentSlot slot = slots[i];
            ItemStack worn = npc.getData().getEquipment().get(slot);
            menu.button(positions[i], icons[i], names[i], "Current: " + (worn == null || worn.getType().isAir() ? "Empty" : label(worn.getType().name())),
                    "Left: copy cursor item, or main-hand item", "Right: clear slot", "Your item is not consumed", c -> edit(player, () -> {
                        ItemStack item = c.isRightClick() ? null : !c.getCursor().getType().isAir() ? c.getCursor() : player.getInventory().getItemInMainHand();
                        if (!c.isRightClick() && (item == null || item.getType().isAir())) { player.sendMessage("Hold an item or place one on the cursor."); return; }
                        change(id, n -> bridge.equipment(n, slot, item), player, () -> equipment(player, id));
                    }));
        }
        menu.button(36, Material.ARROW, "Back", "Editor", c -> editor(player, id));
        menu.open(player);
    }

    private void behaviour(Player player, String id) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        NpcData data = npc.getData();
        Menu menu = menu("Behaviour · " + data.getName(), 36);
        menu.button(10, Material.COMPASS, "Turn to player", "Current: " + data.isTurnToPlayer(), c -> change(id,
                n -> {
                    n.getData().setTurnToPlayer(!n.getData().isTurnToPlayer());
                    feedback(player, "Turn to player " + (n.getData().isTurnToPlayer() ? "enabled." : "disabled."));
                }, player, () -> behaviour(player, id)));
        menu.button(11, Material.SPYGLASS, "Turn distance", "Current: " + data.getTurnToPlayerDistance(), c -> edit(player, () ->
                number(player, id, "Enter turn distance in blocks (-1 uses FancyNpcs default):", value -> {
                    if (value < -1 || value > 128 || value % 1 != 0) return "Use a whole number from -1 to 128.";
                    return change(id, n -> n.getData().setTurnToPlayerDistance(value.intValue()));
                }, () -> behaviour(player, id))));
        menu.button(12, Material.ENDER_EYE, "Visibility distance", "Current: " + data.getVisibilityDistance(), c -> edit(player, () ->
                number(player, id, "Enter visibility distance in blocks (1-256):", value -> {
                    if (value < 1 || value > 256 || value % 1 != 0) return "Use a whole number from 1 to 256.";
                    return change(id, n -> n.getData().setVisibilityDistance(value.intValue()));
                }, () -> behaviour(player, id))));
        menu.button(13, Material.CLOCK, "Interaction cooldown", "Current: " + data.getInteractionCooldown() + " seconds", c -> edit(player, () ->
                number(player, id, "Enter cooldown seconds (0 disables):", value -> {
                    if (value < 0 || value > 3600) return "Use 0 to 3600 seconds.";
                    return change(id, n -> n.getData().setInteractionCooldown(value.floatValue()));
                }, () -> behaviour(player, id))));
        menu.button(15, Material.SHIELD, "Collidable", "Current: " + data.isCollidable(), c -> change(id,
                n -> n.getData().setCollidable(!n.getData().isCollidable()), player, () -> behaviour(player, id)));
        menu.button(16, Material.WRITABLE_BOOK, "Sneak-click editor", "Current: " + bridge.editorShortcut(npc),
                "Sneak + right-click opens this NPC's editor", "Requires GUI access and edit permission", c -> change(id,
                n -> bridge.editorShortcut(n, !bridge.editorShortcut(n)), player, () -> behaviour(player, id)));
        menu.button(22, Material.GLASS, "Visibility mode", "Current: " + label(data.getVisibility().name()), c -> visibility(player, id));
        menu.button(27, Material.ARROW, "Back", "Editor", c -> editor(player, id));
        menu.open(player);
    }

    private void visibility(Player player, String id) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        Menu menu = menu("Visibility mode", 27);
        int slot = 11;
        for (NpcVisibility visibility : NpcVisibility.values()) {
            String description = switch (visibility) {
                case ALL -> "Visible to everyone within viewing distance";
                case PERMISSION_REQUIRED -> "Visible only to players with permission";
                case MANUAL -> "Shown or hidden by another plugin";
            };
            String detail = switch (visibility) {
                case ALL -> "No permission needed";
                case PERMISSION_REQUIRED -> "Permission: fancynpcs.npc." + npc.getData().getName() + ".see";
                case MANUAL -> "Visibility is controlled through the API";
            };
            menu.button(slot, Material.GLASS, visibility == NpcVisibility.ALL ? "Everyone" : label(visibility.name()),
                    npc.getData().getVisibility() == visibility ? "Selected" : "Click to select", description, detail, c -> change(id,
                    n -> n.getData().setVisibility(visibility), player, () -> behaviour(player, id)));
            slot += 2;
        }
        menu.button(18, Material.ARROW, "Back", "Behaviour", c -> behaviour(player, id));
        menu.open(player);
    }

    private void position(Player player, String id) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        Location location = npc.getData().getLocation();
        Menu menu = menu("Position · " + npc.getData().getName(), 36);
        menu.button(4, Material.COMPASS, "Current position", "World: " + world(location), "XYZ: " + coords(location),
                "Yaw: " + location.getYaw() + " · Pitch: " + location.getPitch(), c -> {});
        menu.button(19, Material.ENDER_PEARL, "Teleport to NPC", "Move yourself to the NPC", c -> guard(player, "edit", () -> {
            Npc live = bridge.find(id);
            if (live == null) missing(player); else teleport(player, live);
        }));
        menu.button(20, Material.LODESTONE, "Move NPC here", "Use your current position and look", c -> change(id,
                n -> bridge.moved(n, player.getLocation()), player, () -> position(player, id)));
        menu.button(21, Material.TARGET, "Center NPC", "Center X and Z on the current block", c -> change(id, n -> {
            Location centered = n.getData().getLocation().clone();
            centered.setX(centered.getBlockX() + 0.5);
            centered.setZ(centered.getBlockZ() + 0.5);
            bridge.moved(n, centered);
        }, player, () -> position(player, id)));
        menu.button(23, Material.COMPASS, "Face my direction", "Copy your yaw and pitch", c -> change(id, n -> {
            Location rotated = n.getData().getLocation().clone();
            rotated.setYaw(player.getLocation().getYaw());
            rotated.setPitch(player.getLocation().getPitch());
            bridge.moved(n, rotated);
        }, player, () -> position(player, id)));
        menu.button(24, Material.WRITABLE_BOOK, "Exact XYZ", "Enter x y z in the same world", c -> edit(player, () ->
                input.ask(player, "Enter x y z separated by spaces:", value -> {
                    String[] parts = value.split("\\s+");
                    if (parts.length != 3) return "Enter exactly three numbers.";
                    try {
                        double x = Double.parseDouble(parts[0]), y = Double.parseDouble(parts[1]), z = Double.parseDouble(parts[2]);
                        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 30000000 || Math.abs(z) > 30000000) return "Coordinates are out of range.";
                        return change(id, n -> {
                            Location moved = n.getData().getLocation().clone();
                            moved.set(x, y, z);
                            bridge.moved(n, moved);
                        });
                    } catch (NumberFormatException ex) { return "Enter valid coordinates."; }
                }, () -> position(player, id))));
        menu.button(25, Material.CLOCK, "Exact yaw / pitch", "Enter yaw pitch in degrees", c -> edit(player, () ->
                input.ask(player, "Enter yaw pitch separated by a space:", value -> {
                    String[] parts = value.split("\\s+");
                    if (parts.length != 2) return "Enter exactly two numbers.";
                    try {
                        float yaw = Float.parseFloat(parts[0]), pitch = Float.parseFloat(parts[1]);
                        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) return "Pitch must be -90 to 90; yaw must be finite.";
                        return change(id, n -> {
                            Location rotated = n.getData().getLocation().clone();
                            rotated.setYaw(yaw);
                            rotated.setPitch(pitch);
                            bridge.moved(n, rotated);
                        });
                    } catch (NumberFormatException ex) { return "Enter valid angles."; }
                }, () -> position(player, id))));
        menu.button(27, Material.ARROW, "Back", "Editor", c -> editor(player, id));
        menu.open(player);
    }

    private void attributes(Player player, String id, int page) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        List<NpcAttribute> attributes = bridge.attributes(npc.getData().getType());
        int current = clamp(page, attributes.size(), 45);
        Menu menu = menu("Attributes · " + (current + 1), Math.max(27, ((Math.min(attributes.size(), 45) + 8) / 9 + 1) * 9));
        int navigation = menu.inventory.getSize() - 9;
        for (int i = current * 45; i < Math.min(attributes.size(), (current + 1) * 45); i++) {
            NpcAttribute attribute = attributes.get(i);
            String value = npc.getData().getAttributes().get(attribute);
            menu.button(i - current * 45, Material.WRITABLE_BOOK, label(attribute.getName()),
                    "Current: " + (value == null ? "default" : shortText(value)),
                    "Left: edit · Right: reset", c -> edit(player, () -> {
                        if (c.isRightClick()) {
                            change(id, n -> bridge.resetAttribute(n, attribute), player, () -> attributes(player, id, current));
                        } else {
                            attributeValues(player, id, attribute, 0, current);
                        }
                    }));
        }
        if (current > 0) menu.button(navigation, Material.ARROW, "Previous page", "", c -> attributes(player, id, current - 1));
        menu.button(navigation + 4, Material.OAK_DOOR, "Back", "Editor", c -> editor(player, id));
        if ((current + 1) * 45 < attributes.size()) menu.button(navigation + 8, Material.ARROW, "Next page", "", c -> attributes(player, id, current + 1));
        menu.open(player);
    }

    private void attributeValues(Player player, String id, NpcAttribute attribute, int page, int attributePage) {
        if (attribute.getName().equals("use_item")) {
            Npc npc = bridge.find(id);
            if (npc == null) { missing(player); return; }
            Menu menu = menu("Use equipped item", 27);
            String selected = npc.getData().getAttributes().getOrDefault(attribute, "none");
            String[] values = {"main_hand", "off_hand", "none"};
            Material[] icons = {Material.IRON_SWORD, Material.SHIELD, Material.BARRIER};
            String[] descriptions = {"Use the equipped main-hand item", "Use the equipped off-hand item", "Stop using either item"};
            for (int i = 0; i < values.length; i++) {
                String value = values[i];
                NpcEquipmentSlot hand = i == 0 ? NpcEquipmentSlot.MAINHAND : NpcEquipmentSlot.OFFHAND;
                ItemStack held = npc.getData().getEquipment().get(hand);
                menu.button(10 + i * 3, icons[i], i == 2 ? "Stop using item" : label(value),
                        "Current: " + selected.equals(value), descriptions[i],
                        i == 2 ? "Ends the held-item use animation" : "Item: " + (held == null || held.getType().isAir() ? "Empty; equip a usable item first" : label(held.getType().name())),
                        i == 2 ? "Equipment stays unchanged" : "Shows the item's use animation (e.g. blocking)",
                        c -> change(id, n -> bridge.attribute(n, attribute, value), player, () -> attributeValues(player, id, attribute, 0, attributePage)));
            }
            menu.button(18, Material.ARROW, "Back", "Attributes", c -> attributes(player, id, attributePage));
            menu.open(player);
            return;
        }
        List<String> values = attribute.getPossibleValues();
        int current = clamp(page, values.size(), 45);
        Menu menu = menu("Attribute · " + label(attribute.getName()), Math.max(27, ((Math.min(values.size(), 45) + 8) / 9 + 1) * 9));
        int navigation = menu.inventory.getSize() - 9;
        for (int i = current * 45; i < Math.min(values.size(), (current + 1) * 45); i++) {
            String value = values.get(i);
            menu.button(i - current * 45, value.equalsIgnoreCase("true") ? Material.LIME_DYE : value.equalsIgnoreCase("false") ? Material.RED_DYE : Material.PAPER,
                    value.equalsIgnoreCase("true") ? "Enabled" : value.equalsIgnoreCase("false") ? "Disabled" : label(shortText(value)), "Set this value", c -> change(id,
                    n -> bridge.attribute(n, attribute, value), player, () -> attributes(player, id, attributePage)));
        }
        if (current > 0) menu.button(navigation, Material.ARROW, "Previous page", "", c -> attributeValues(player, id, attribute, current - 1, attributePage));
        menu.button(navigation + 2, Material.WRITABLE_BOOK, "Enter value", "Use a valid FancyNpcs value", c -> edit(player, () ->
                input.ask(player, "Enter " + attribute.getName() + " value:", value -> {
                    if (!attribute.isValidValue(value)) return "FancyNpcs does not accept that value.";
                    return change(id, n -> bridge.attribute(n, attribute, value));
                }, () -> attributes(player, id, attributePage))));
        menu.button(navigation + 4, Material.OAK_DOOR, "Back", "Attributes", c -> attributes(player, id, attributePage));
        if ((current + 1) * 45 < values.size()) menu.button(navigation + 8, Material.ARROW, "Next page", "", c -> attributeValues(player, id, attribute, current + 1, attributePage));
        menu.open(player);
    }

    private void management(Player player, String id) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        Menu menu = menu("Management", 27);
        menu.button(11, Material.CHEST, "Duplicate NPC", "Ask for a new internal name", c -> guard(player, "create", () ->
                input.ask(player, "Enter a unique name for the copy:", name -> {
                    if (!bridge.nameAvailable(name)) return "Invalid or already used NPC name.";
                    Npc live = bridge.find(id);
                    if (live == null) return "NPC was removed.";
                    if (!bridge.copy(player, live, name)) return "FancyNpcs denied copying this NPC.";
                    feedback(player, "NPC duplicated.");
                    return null;
                }, () -> browser(player, 0))));
        menu.button(15, Material.BARRIER, "Delete NPC", "Requires confirmation", c -> guard(player, "delete", () -> confirm(player,
                "Delete " + npc.getData().getName() + "?", () -> {
                    if (!player.hasPermission("fancynpcsgui.delete")) { player.sendMessage("You no longer have permission to delete NPCs."); return; }
                    Npc live = bridge.find(id);
                    if (live != null) {
                        bridge.delete(live);
                        feedback(player, "NPC deleted.");
                    }
                    browser(player, 0);
                }, () -> management(player, id))));
        menu.button(18, Material.ARROW, "Back", "Editor", c -> editor(player, id));
        menu.open(player);
    }

    private void triggers(Player player, String id) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        Menu menu = menu("Interactions", 27);
        ActionTrigger[] triggers = {ActionTrigger.LEFT_CLICK, ActionTrigger.ANY_CLICK, ActionTrigger.RIGHT_CLICK};
        int[] slots = {10, 13, 16};
        Material[] icons = {Material.IRON_SWORD, Material.COMPARATOR, Material.CARROT_ON_A_STICK};
        for (int i = 0; i < triggers.length; i++) {
            ActionTrigger trigger = triggers[i];
            menu.button(slots[i], icons[i], label(trigger.name()) + " actions",
                    "Actions: " + bridge.actions(npc, trigger).size(),
                    trigger == ActionTrigger.ANY_CLICK ? "Runs on either left or right click" : "Runs on " + label(trigger.name()).toLowerCase(Locale.ROOT),
                    "Click to edit the execution order", c -> guard(player, "actions", () -> actions(player, id, trigger, 0)));
        }
        menu.button(18, Material.ARROW, "Back", "NPC editor", c -> editor(player, id));
        menu.open(player);
    }

    private void actions(Player player, String id, ActionTrigger trigger, int page) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        List<NpcActionData> actions = bridge.actions(npc, trigger);
        int current = clamp(page, actions.size(), 45);
        Menu menu = menu(label(trigger.name()) + " · " + (current + 1), Math.max(27, ((Math.min(actions.size(), 45) + 8) / 9 + 1) * 9));
        int navigation = menu.inventory.getSize() - 9;
        menu.shiftClicks = true;
        for (int i = current * 45; i < Math.min(actions.size(), (current + 1) * 45); i++) {
            int index = i;
            NpcActionData shown = actions.get(i);
            menu.button(i - current * 45, actionIcon(shown.action().getName()),
                    (i + 1) + ". " + label(shown.action().getName()),
                    shown.action().getName().equals("wait") ? "Duration: " + shortText(shown.value()) + " seconds" : "Value: " + shortText(shown.value()),
                    "Left: replace · Right: remove",
                    "Shift-click: move up or down", c -> guard(player, "actions", () -> {
                        Npc live = bridge.find(id);
                        if (live == null) { missing(player); return; }
                        List<NpcActionData> updated = new ArrayList<>(bridge.actions(live, trigger));
                        if (index >= updated.size() || !sameAction(shown, updated.get(index))) {
                            player.sendMessage("Action chain changed; refreshing.");
                            actions(player, id, trigger, current);
                            return;
                        }
                        if (c.getClick() == ClickType.SHIFT_LEFT || c.getClick() == ClickType.SHIFT_RIGHT) {
                            moveAction(player, id, trigger, index, current, shown);
                        } else if (c.isRightClick()) {
                            confirm(player, "Remove action " + (index + 1) + "?", () -> {
                                Npc fresh = bridge.find(id);
                                if (fresh == null) { missing(player); return; }
                                if (!player.hasPermission("fancynpcsgui.actions")) { player.sendMessage("You no longer have permission to edit actions."); return; }
                                List<NpcActionData> list = new ArrayList<>(bridge.actions(fresh, trigger));
                                if (index < list.size() && sameAction(shown, list.get(index))) {
                                    list.remove(index);
                                    bridge.setActions(fresh, trigger, list);
                                    feedback(player, "Action removed.");
                                }
                                actions(player, id, trigger, current);
                            }, () -> actions(player, id, trigger, current));
                        } else {
                            actionTypes(player, id, trigger, index, 0, current, shown);
                        }
                    }));
        }
        if (actions.isEmpty()) menu.button(4, Material.WRITABLE_BOOK, "No actions yet", "Use Add action to build this chain", c -> {});
        if (current > 0) menu.button(navigation, Material.ARROW, "Previous page", "", c -> actions(player, id, trigger, current - 1));
        menu.button(navigation + 2, Material.EMERALD, "Add action", "Choose what happens when clicked", c -> guard(player, "actions", () -> actionTypes(player, id, trigger, -1, 0, current, null)));
        menu.button(navigation + 4, Material.OAK_DOOR, "Back", "Interactions", c -> triggers(player, id));
        if (!actions.isEmpty()) menu.button(navigation + 6, Material.BARRIER, "Clear chain", "Remove every action with confirmation", c -> guard(player, "actions", () -> confirm(player,
                "Clear " + label(trigger.name()).toLowerCase(Locale.ROOT) + " actions?", () -> {
                    Npc live = bridge.find(id);
                    if (live != null) {
                        if (!player.hasPermission("fancynpcsgui.actions")) { player.sendMessage("You no longer have permission to edit actions."); return; }
                        if (!bridge.actions(live, trigger).equals(actions)) {
                            player.sendMessage("Action chain changed; refreshing.");
                            actions(player, id, trigger, current);
                            return;
                        }
                        bridge.setActions(live, trigger, List.of());
                        feedback(player, "Actions cleared.");
                    }
                    actions(player, id, trigger, 0);
                }, () -> actions(player, id, trigger, current))));
        if ((current + 1) * 45 < actions.size()) menu.button(navigation + 8, Material.ARROW, "Next page", "", c -> actions(player, id, trigger, current + 1));
        menu.open(player);
    }

    private void actionTypes(Player player, String id, ActionTrigger trigger, int replace, int page, int returnPage, NpcActionData expected) {
        if (bridge.find(id) == null) { missing(player); return; }
        List<NpcAction> types = bridge.actionTypes();
        int[] slots = {10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25};
        int current = clamp(page, types.size(), slots.length);
        Menu menu = menu(replace < 0 ? "Add action" : "Replace action", 36);
        menu.button(4, Material.COMPASS, "Choose an action", "Commands · Messages · Flow control", c -> {});
        for (int i = current * slots.length; i < Math.min(types.size(), (current + 1) * slots.length); i++) {
            NpcAction action = types.get(i);
            boolean dangerous = action.getName().equals("player_command_as_op");
            menu.button(slots[i - current * slots.length], actionIcon(action.getName()),
                    label(action.getName()), actionDescription(action.getName()),
                    dangerous ? "WARNING: runs with operator privileges" : action.requiresValue() ? "Click to enter a value" : "Click to add this action",
                    c -> guard(player, "actions", () -> {
                        Runnable choose = () -> actionValue(player, id, trigger, replace, action, returnPage, expected);
                        if (dangerous) confirm(player, "Add OP command action?", choose,
                                () -> actionTypes(player, id, trigger, replace, current, returnPage, expected));
                        else choose.run();
                    }));
        }
        if (current > 0) menu.button(27, Material.ARROW, "Previous page", "", c -> actionTypes(player, id, trigger, replace, current - 1, returnPage, expected));
        menu.button(31, Material.OAK_DOOR, "Back", "Action chain", c -> actions(player, id, trigger, returnPage));
        if ((current + 1) * slots.length < types.size()) menu.button(35, Material.ARROW, "Next page", "", c -> actionTypes(player, id, trigger, replace, current + 1, returnPage, expected));
        menu.open(player);
    }

    private void moveAction(Player player, String id, ActionTrigger trigger, int index, int page, NpcActionData expected) {
        Npc npc = bridge.find(id);
        if (npc == null) { missing(player); return; }
        List<NpcActionData> list = bridge.actions(npc, trigger);
        if (index >= list.size() || !sameAction(expected, list.get(index))) {
            actions(player, id, trigger, page);
            return;
        }
        Menu menu = menu("Move action", 27);
        menu.button(4, actionIcon(expected.action().getName()), (index + 1) + ". " + label(expected.action().getName()),
                "Choose the new execution position", c -> {});
        for (int direction : new int[] {-1, 1}) {
            boolean available = index + direction >= 0 && index + direction < list.size();
            menu.button(direction < 0 ? 11 : 15, available ? Material.ARROW : Material.GRAY_DYE,
                    direction < 0 ? "Move up" : "Move down",
                    available ? "Move one position " + (direction < 0 ? "earlier" : "later") : "Already " + (direction < 0 ? "first" : "last"),
                    c -> guard(player, "actions", () -> {
                        Npc live = bridge.find(id);
                        if (live == null) { missing(player); return; }
                        List<NpcActionData> updated = new ArrayList<>(bridge.actions(live, trigger));
                        int target = index + direction;
                        if (index < updated.size() && sameAction(expected, updated.get(index))) {
                            if (target >= 0 && target < updated.size()) {
                                java.util.Collections.swap(updated, index, target);
                                bridge.setActions(live, trigger, updated);
                            }
                        } else player.sendMessage("Action chain changed; refreshing.");
                        actions(player, id, trigger, page);
                    }));
        }
        menu.button(18, Material.ARROW, "Back", "Action chain", c -> actions(player, id, trigger, page));
        menu.open(player);
    }

    private void actionValue(Player player, String id, ActionTrigger trigger, int replace, NpcAction action, int returnPage, NpcActionData expected) {
        if (!player.hasPermission("fancynpcsgui.actions")) { player.sendMessage("You no longer have permission to edit actions."); return; }
        if (!action.requiresValue()) {
            String error = setAction(id, trigger, replace, action, null, expected);
            if (error != null) player.sendMessage(error);
            else feedback(player, replace < 0 ? "Action added." : "Action updated.");
            actions(player, id, trigger, returnPage);
            return;
        }
        input.ask(player, action.getName().equals("wait") ? "Enter wait duration in whole seconds:" : "Enter value for " + label(action.getName()) + ":", value -> {
            if (!player.hasPermission("fancynpcsgui.actions")) return "You no longer have permission to edit actions.";
            if (value.isBlank()) return "Value cannot be empty.";
            if (action.getName().equals("wait")) {
                try {
                    if (Integer.parseInt(value) < 0) return "Use a non-negative whole number of seconds.";
                } catch (NumberFormatException ex) { return "Use a non-negative whole number of seconds."; }
            }
            String error = setAction(id, trigger, replace, action, value, expected);
            if (error == null) feedback(player, replace < 0 ? "Action added." : "Action updated.");
            return error;
        }, () -> actions(player, id, trigger, returnPage));
    }

    private String setAction(String id, ActionTrigger trigger, int replace, NpcAction action, String value, NpcActionData expected) {
        Npc npc = bridge.find(id);
        if (npc == null) return "NPC was removed.";
        List<NpcActionData> list = new ArrayList<>(bridge.actions(npc, trigger));
        NpcActionData updated = new NpcActionData(replace < 0 ? list.size() + 1 : replace + 1, action, value);
        if (replace < 0) list.add(updated);
        else if (replace < list.size() && sameAction(expected, list.get(replace))) list.set(replace, updated);
        else return "Action chain changed. Open it again.";
        bridge.setActions(npc, trigger, list);
        return null;
    }

    private boolean sameAction(NpcActionData first, NpcActionData second) {
        return first.action().getName().equals(second.action().getName())
                && java.util.Objects.equals(first.value(), second.value());
    }

    private void confirm(Player player, String question, Runnable yes, Runnable no) {
        Menu menu = menu("Confirm action", 27);
        menu.button(11, Material.LIME_CONCRETE, "Confirm", question, c -> yes.run());
        menu.button(15, Material.RED_CONCRETE, "Cancel", "Return without changes", c -> no.run());
        menu.open(player);
    }

    private void teleport(Player player, Npc npc) {
        Location location = npc.getData().getLocation();
        if (location.getWorld() == null) { player.sendMessage("NPC world is unavailable."); return; }
        player.teleport(location);
        player.closeInventory();
    }

    private void number(Player player, String id, String prompt, Function<Double, String> apply, Runnable back) {
        input.ask(player, prompt, raw -> {
            try {
                double value = Double.parseDouble(raw);
                if (!Double.isFinite(value)) return "Enter a finite number.";
                return apply.apply(value);
            } catch (NumberFormatException ex) {
                return "Enter a valid number.";
            }
        }, back);
    }

    private String change(String id, Consumer<Npc> change) {
        Npc npc = bridge.find(id);
        if (npc == null) return "NPC was removed.";
        try {
            change.accept(npc);
            bridge.changed(npc);
            return null;
        } catch (RuntimeException | LinkageError failure) {
            return failure.getMessage() == null ? "FancyNpcs could not apply this change." : failure.getMessage();
        }
    }

    private void change(String id, Consumer<Npc> change, Player player, Runnable back) {
        edit(player, () -> {
            String error = change(id, change);
            if (error != null) player.sendMessage(Component.text(error, NamedTextColor.RED));
            back.run();
        });
    }

    private void edit(Player player, Runnable action) {
        guard(player, "edit", action);
    }

    private void guard(Player player, String permission, Runnable action) {
        if (!player.hasPermission("fancynpcsgui.admin") || !player.hasPermission("fancynpcsgui." + permission)) {
            player.sendMessage("You do not have permission for this operation.");
            return;
        }
        run(player, action);
    }

    private void run(Player player, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().warning("NPC operation failed: " + failure);
            player.sendMessage("FancyNpcs could not complete that operation. See console.");
        }
    }

    private void missing(Player player) {
        player.sendMessage("That NPC no longer exists.");
        browser(player, 0);
    }

    private static int clamp(int page, int size, int pageSize) {
        return Math.max(0, Math.min(page, Math.max(0, (size - 1) / pageSize)));
    }

    private static String label(String text) {
        String value = text.toLowerCase(Locale.ROOT).replace('_', ' ');
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static Material actionIcon(String name) {
        return switch (name) {
            case "console_command" -> Material.COMMAND_BLOCK;
            case "player_command" -> Material.WRITABLE_BOOK;
            case "player_command_as_op" -> Material.BARRIER;
            case "message" -> Material.PAPER;
            case "wait" -> Material.CLOCK;
            case "need_permission" -> Material.TRIPWIRE_HOOK;
            case "send_to_server" -> Material.ENDER_PEARL;
            case "execute_random_action" -> Material.DROPPER;
            default -> Material.REPEATER;
        };
    }

    private static String actionDescription(String name) {
        return switch (name) {
            case "console_command" -> "Run a command as the server console";
            case "player_command" -> "Run a command as the clicking player";
            case "player_command_as_op" -> "Run a player command with operator access";
            case "message" -> "Send a message to the clicking player";
            case "wait" -> "Pause this chain for whole seconds";
            case "need_permission" -> "Continue only if the player has a permission";
            case "send_to_server" -> "Connect the player to another proxy server";
            case "execute_random_action" -> "Choose randomly from the configured actions";
            default -> "Custom action: " + label(name);
        };
    }

    private void feedback(Player player, String message) {
        player.sendMessage(Component.text("FancyNpcs » ", NamedTextColor.AQUA)
                .append(Component.text(message, NamedTextColor.GREEN)));
    }

    private static String shortText(String text) {
        if (text == null || text.isBlank()) return "none";
        if (text.equalsIgnoreCase("<empty>")) return "Hidden";
        return text.length() > 42 ? text.substring(0, 39) + "..." : text;
    }

    private static String world(Location location) {
        return location.getWorld() == null ? "unavailable" : location.getWorld().getName();
    }

    private static String coords(Location location) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
    }

    private static Material icon(EntityType type) {
        if (type == EntityType.PLAYER) return Material.PLAYER_HEAD;
        Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return egg == null ? Material.ARMOR_STAND : egg;
    }

    private static Menu menu(String title, int size) {
        return new Menu(title, size);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0) return;
        if (event.getRawSlot() >= menu.inventory.getSize()) {
            if (menu.allowInventory && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) event.setCancelled(false);
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT
                && event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) return;
        if (event.isShiftClick() && !menu.shiftClicks) return;
        Consumer<InventoryClickEvent> handler = menu.handlers.get(event.getRawSlot());
        if (handler != null) Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != menu.inventory) return;
            if (!player.hasPermission("fancynpcsgui.admin") || !player.hasPermission("fancynpcsgui.use")) {
                player.closeInventory();
                return;
            }
            run(player, () -> handler.accept(event));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcClick(NpcPreInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getInteractionType() != ActionTrigger.RIGHT_CLICK || !player.isSneaking()
                || !bridge.editorShortcut(event.getNpc()) || !player.hasPermission("fancynpcsgui.admin")
                || !player.hasPermission("fancynpcsgui.use") || !player.hasPermission("fancynpcsgui.edit")) return;
        event.setCancelled(true);
        String id = event.getNpc().getData().getId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Npc live = bridge.find(id);
            if (player.isOnline() && live != null && bridge.editorShortcut(live)) guard(player, "edit", () -> {
                input.cancel(player);
                editor(player, id);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcModify(NpcModifyEvent event) {
        if (!(event.getModifier() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu && menu.refresh != null) menu.refresh.run();
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    private static final class Menu implements InventoryHolder {
        private final Inventory inventory;
        private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new java.util.HashMap<>();
        private boolean shiftClicks;
        private boolean allowInventory;
        private Runnable refresh;

        private Menu(String title, int size) {
            inventory = Bukkit.createInventory(this, size, Component.text(title.length() > 30 ? title.substring(0, 27) + "…" : title, NamedTextColor.AQUA));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void open(Player player) {
            player.openInventory(inventory);
        }

        private void npcHead(int slot, Npc npc, Player viewer) {
            ItemStack item = inventory.getItem(slot);
            if (npc.getData().getType() != EntityType.PLAYER || item == null || !(item.getItemMeta() instanceof SkullMeta meta)) return;
            var data = npc.getData();
            if (data.isMirrorSkin()) {
                meta.setPlayerProfile(viewer.getPlayerProfile());
            } else {
                var skin = data.getSkinData();
                if (skin == null || !skin.hasTexture()) return;
                var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(data.getId().getBytes(StandardCharsets.UTF_8)));
                profile.setProperty(new ProfileProperty("textures", skin.getTextureValue(), skin.getTextureSignature()));
                meta.setPlayerProfile(profile);
            }
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }

        private void button(int slot, Material material, String title, String lore, Consumer<InventoryClickEvent> click) {
            put(slot, material, title, click, lore);
        }

        private void button(int slot, Material material, String title, String first, String second, Consumer<InventoryClickEvent> click) {
            put(slot, material, title, click, first, second);
        }

        private void button(int slot, Material material, String title, String first, String second, String third, Consumer<InventoryClickEvent> click) {
            put(slot, material, title, click, first, second, third);
        }

        private void button(int slot, Material material, String title, String first, String second, String third, String fourth, Consumer<InventoryClickEvent> click) {
            put(slot, material, title, click, first, second, third, fourth);
        }

        private void button(int slot, Material material, String title, String first, String second, String third, String fourth, String fifth, Consumer<InventoryClickEvent> click) {
            put(slot, material, title, click, first, second, third, fourth, fifth);
        }

        private void button(int slot, Material material, String title, String first, String second, String third, String fourth, String fifth, String sixth, Consumer<InventoryClickEvent> click) {
            put(slot, material, title, click, first, second, third, fourth, fifth, sixth);
        }

        private void put(int slot, Material material, String title, Consumer<InventoryClickEvent> click, String... lore) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            NamedTextColor titleColor = material == Material.BARRIER || material == Material.RED_CONCRETE ? NamedTextColor.RED
                    : material == Material.LIME_CONCRETE || material == Material.EMERALD ? NamedTextColor.GREEN : NamedTextColor.AQUA;
            meta.displayName(Component.text(title, titleColor).decoration(TextDecoration.ITALIC, false));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                if (line == null || line.isBlank()) continue;
                NamedTextColor color = NamedTextColor.GRAY;
                if (line.equals("Selected")) color = NamedTextColor.GREEN;
                else if (line.equals("Current: true")) { line = "Enabled"; color = NamedTextColor.GREEN; }
                else if (line.equals("Current: false")) { line = "Disabled"; color = NamedTextColor.RED; }
                else if (line.startsWith("WARNING:")) color = NamedTextColor.RED;
                else if (line.startsWith("Current:") || line.startsWith("Value:") || line.startsWith("Duration:")
                        || line.startsWith("Actions:") || line.startsWith("Item:")) color = NamedTextColor.YELLOW;
                else if (line.startsWith("Left:") || line.startsWith("Right:") || line.startsWith("Shift-")) color = NamedTextColor.WHITE;
                lines.add(Component.text(line, color).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            handlers.put(slot, click);
        }
    }
}
