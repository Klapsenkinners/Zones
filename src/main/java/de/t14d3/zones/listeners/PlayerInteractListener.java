package de.t14d3.zones.listeners;

import de.t14d3.zones.PermissionManager;
import de.t14d3.zones.Region;
import de.t14d3.zones.RegionManager;
import de.t14d3.zones.Zones;
import de.t14d3.zones.utils.Actions;
import de.t14d3.zones.utils.Messages;
import de.t14d3.zones.utils.Utils;
import de.t14d3.zones.visuals.BeaconUtils;
import it.unimi.dsi.fastutil.Pair;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.t14d3.zones.visuals.BeaconUtils.resetBeacon;
import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed;

public class PlayerInteractListener implements Listener {

    private final RegionManager regionManager;
    private final PermissionManager permissionManager;
    private final Zones plugin;
    private final BeaconUtils beaconUtils;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Messages messages;

    public PlayerInteractListener(RegionManager regionManager, PermissionManager permissionManager, Zones plugin) {
        this.plugin = plugin;
        this.regionManager = regionManager;
        this.permissionManager = permissionManager;
        this.beaconUtils = plugin.getBeaconUtils();
        this.messages = plugin.getMessages();

    }
    @EventHandler
    private void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() == null) {
            return; // No block clicked, exit early
        }

        Location location = event.getClickedBlock().getLocation();
        UUID playerUUID = player.getUniqueId();

        if (plugin.selection.containsKey(playerUUID)) {
            if (event.getHand() == EquipmentSlot.OFF_HAND) {
                return;
            }
            event.setCancelled(true);

            Pair<Location, Location> selection = plugin.selection.get(playerUUID);
            Location min = selection.first(); // Current minimum location
            Location max = selection.second(); // Current maximum location

            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                resetBeacon(player, min);
                min = location;
                beaconUtils.createBeacon(player, min, DyeColor.GREEN);
                player.sendMessage(miniMessage.deserialize(messages.get("create.primary")
                        , parsed("x", String.valueOf(location.getBlockX()))
                        , parsed("y", String.valueOf(location.getBlockY()))
                        , parsed("z", String.valueOf(location.getBlockZ()))
                ));
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                resetBeacon(player, max);
                max = location;
                beaconUtils.createBeacon(player, max, DyeColor.RED);
                player.sendMessage(miniMessage.deserialize(messages.get("create.secondary")
                        , parsed("x", String.valueOf(location.getBlockX()))
                        , parsed("y", String.valueOf(location.getBlockY()))
                        , parsed("z", String.valueOf(location.getBlockZ()))
                ));
            }

            plugin.selection.put(playerUUID, Pair.of(min, max));
            if (min != null && max != null) {
                Utils.Modes mode = Utils.Modes.getPlayerMode(player);
                if (mode == Utils.Modes.CUBOID_3D) {
                    plugin.particles.put(playerUUID, BoundingBox.of(min.toBlockLocation(), max.toBlockLocation()));
                } else {
                    min.setY(-63);
                    max.setY(319);
                    plugin.particles.put(playerUUID, BoundingBox.of(min.toBlockLocation(), max.toBlockLocation()));
                }

            }
            return;
        }

        List<Actions> requiredPermissions = new ArrayList<>(); // Collect required permissions

        // Interactable blocks
        if ((Utils.isContainer(event.getClickedBlock().getState(false)) || Utils.isPowerable(event.getClickedBlock().getBlockData())) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            requiredPermissions.add(Actions.INTERACT);
            if (Utils.isContainer(event.getClickedBlock().getState(false))) {
                requiredPermissions.add(Actions.CONTAINER);
            }
            if (Utils.isPowerable(event.getClickedBlock().getBlockData())) {
                requiredPermissions.add(Actions.REDSTONE);
            }
            if (event.getClickedBlock().getType() == Material.TNT) {
                requiredPermissions.add(Actions.IGNITE);
            }
        }
        else return;
        for (Actions action : requiredPermissions) {
            if (!permissionManager.canInteract(location, playerUUID, action, event.getClickedBlock().getType().name())) {
                event.setCancelled(true);
                actionBar(player, location, requiredPermissions, event.getClickedBlock().getType().name());
            }
        }
    }

    @EventHandler
    private void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String type = event.getBlockPlaced().getType().name();
        Location location = event.getBlockPlaced().getLocation();
        List<Actions> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Actions.PLACE);
        if (Utils.isContainer(event.getBlockPlaced().getState(false))) {
            requiredPermissions.add(Actions.CONTAINER);
        }
        if (Utils.isPowerable(event.getBlockPlaced().getBlockData())) {
            requiredPermissions.add(Actions.REDSTONE);
        }
        for (Actions action : requiredPermissions) {
            if (!permissionManager.canInteract(event.getBlockPlaced().getLocation(), player.getUniqueId(), action, type)) {
                event.setCancelled(true);
                actionBar(player, location, requiredPermissions, type);
            }
        }

    }

    @EventHandler
    private void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String type = event.getBlock().getType().name();
        Location location = event.getBlock().getLocation();
        List<Actions> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Actions.BREAK);
        if (Utils.isContainer(event.getBlock().getState(false))) {
            requiredPermissions.add(Actions.CONTAINER);
        }
        if (Utils.isPowerable(event.getBlock().getBlockData())) {
            requiredPermissions.add(Actions.REDSTONE);
        }
        for (Actions action : requiredPermissions) {
            if (!permissionManager.canInteract(event.getBlock().getLocation(), player.getUniqueId(), action, type)) {
                event.setCancelled(true);
                actionBar(player, location, requiredPermissions, type);
            }
        }

    }


    @EventHandler
    private void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Location location = event.getRightClicked().getLocation();
        List<Actions> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Actions.INTERACT);
        requiredPermissions.add(Actions.ENTITY);
        String type = event.getRightClicked().getType().name();
        for (Actions action : requiredPermissions) {
            if (!permissionManager.canInteract(location, player.getUniqueId(), action, type)) {
                event.setCancelled(true);
                actionBar(player, location, requiredPermissions, type);
            }
        }
    }

    @EventHandler
    private void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            Location location = event.getEntity().getLocation();
            List<Actions> requiredPermissions = new ArrayList<>();
            requiredPermissions.add(Actions.DAMAGE);
            String type = event.getEntity().getType().name();
            for (Actions action : requiredPermissions) {
                if (!permissionManager.canInteract(location, player.getUniqueId(), action, type)) {
                    event.setCancelled(true);
                    actionBar(player, location, requiredPermissions, type);
                }
            }
        }
    }

    @EventHandler
    private void onVehicleDamage(VehicleDamageEvent event) {
        if (event.getAttacker() instanceof Player player) {
            Location location = event.getVehicle().getLocation();
            List<Actions> requiredPermissions = new ArrayList<>();
            requiredPermissions.add(Actions.DAMAGE);
            String type = event.getVehicle().getType().name();
            for (Actions action : requiredPermissions) {
                if (!permissionManager.canInteract(location, player.getUniqueId(), action, type)) {
                    event.setCancelled(true);
                    actionBar(player, location, requiredPermissions, type);
                }
            }
        }
    }

    @EventHandler
    private void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        Location location = event.getRightClicked().getLocation();
        List<Actions> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Actions.ENTITY);
        String type = event.getRightClicked().getType().name();
        requiredPermissions.add(Actions.CONTAINER);
        for (Actions action : requiredPermissions) {
            if (!permissionManager.canInteract(location, player.getUniqueId(), action, type)) {
                event.setCancelled(true);
                actionBar(player, location, requiredPermissions, type);
            }
        }
    }

    @EventHandler
    private void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        Location location = event.getEntity().getLocation();
        List<Actions> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Actions.PLACE);
        String type = event.getEntity().getType().name();
        for (Actions action : requiredPermissions) {
            if (!permissionManager.canInteract(location, player.getUniqueId(), action, type)) {
                event.setCancelled(true);
                actionBar(player, location, requiredPermissions, type);
            }
        }
    }

    @EventHandler
    private void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = (Player) event.getRemover();
        Location location = event.getEntity().getLocation();
        List<Actions> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Actions.BREAK);
        requiredPermissions.add(Actions.ENTITY);
        String type = event.getEntity().getType().name();
        for (Actions action : requiredPermissions) {
            if (!permissionManager.canInteract(location, player.getUniqueId(), action, type)) {
                event.setCancelled(true);
                actionBar(player, location, requiredPermissions, type);
            }
        }
    }

    @EventHandler
    private void onEntityPlace(EntityPlaceEvent event) {
        if (event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        Location location = event.getEntity().getLocation();
        List<Actions> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Actions.PLACE);
        requiredPermissions.add(Actions.ENTITY);
        String type = event.getEntity().getType().name();
        for (Actions action : requiredPermissions) {
            if (!permissionManager.canInteract(location, player.getUniqueId(), action, type)) {
                event.setCancelled(true);
                actionBar(player, location, requiredPermissions, type);
            }
        }
    }

    // Small util for message
    private void actionBar(Player player, Location location, List<Actions> actions, String type) {
        List<Region> regions = regionManager.getRegionsAt(location);
        String regionNames = regions.stream().map(Region::getName).collect(Collectors.joining(", "));

        StringBuilder permissionsString = new StringBuilder();
        for (Actions action : actions) {
            permissionsString.append(action).append(", ");
        }
        permissionsString.deleteCharAt(permissionsString.length() - 2); // Remove trailing ", "
        permissionsString.deleteCharAt(permissionsString.length() - 1); // Remove trailing ", "

        player.sendActionBar(miniMessage.deserialize(messages.get("region.no-interact-permission"),
                parsed("region", regionNames),
                parsed("actions", permissionsString.toString()),
                parsed("type", type)));
    }
}
