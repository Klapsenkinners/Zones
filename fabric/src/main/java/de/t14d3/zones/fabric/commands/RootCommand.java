package de.t14d3.zones.fabric.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.t14d3.zones.Region;
import de.t14d3.zones.RegionManager;
import de.t14d3.zones.Zones;
import de.t14d3.zones.ZonesFabric;
import de.t14d3.zones.objects.Player;
import de.t14d3.zones.utils.Messages;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.kyori.adventure.platform.modcommon.impl.NonWrappingComponentSerializer;
import net.kyori.adventure.platform.modcommon.impl.WrappedComponent;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RootCommand {
    private final ZonesFabric mod;
    private final RegionManager regionManager;
    private final CancelCommand cancelCommand;
    private final CreateCommand createCommand;
    private final DeleteCommand deleteCommand;
    private final ListCommand listCommand;
    private final SetCommand setCommand;
    private final InfoCommand infoCommand;
    private final RenameCommand renameCommand;
    private final ExpandCommand expandCommand;

    public RootCommand(ZonesFabric mod) {
        this.mod = mod;
        this.regionManager = mod.getRegionManager();
        this.cancelCommand = new CancelCommand(mod);
        this.createCommand = new CreateCommand(mod);
        this.deleteCommand = new DeleteCommand(mod);
        this.listCommand = new ListCommand(mod);
        this.setCommand = new SetCommand(mod);
        this.infoCommand = new InfoCommand(mod);
        this.renameCommand = new RenameCommand(mod);
        this.expandCommand = new ExpandCommand(mod);
        register();
    }

    protected static CompletableFuture<Suggestions> regionKeySuggestion(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        final Zones zones = Zones.getInstance();
        List<Region> regions = new ArrayList<>();
        Player player = context.getSource().getPlayer() != null ? zones.getPlatform().getPlayer(
                context.getSource().getPlayer().getUuid()) : null;
        if (Permissions.check(context.getSource(), "zones.set.other")) {
            regions.addAll(zones.getRegionManager().regions().values());
        } else if (player != null) {
            for (Region region : zones.getRegionManager().regions().values()) {
                if (region.isMember(player.getUniqueId())) {
                    regions.add(region);
                }
            }
        }
        for (Region region : regions) {
            builder.suggest(region.getKey().toString(), new WrappedComponent(
                    Messages.regionInfo(region, false),
                    null,
                    null,
                    NonWrappingComponentSerializer.INSTANCE
            ));
        }
        return builder.buildFuture();
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("zone")
                            .then(CommandManager.literal("cancel")
                                    .requires(source -> Permissions.check(source, "zones.cancel"))
                                    .executes(cancelCommand::execute))
                            .then(CommandManager.literal("create")
                                    .requires(source -> Permissions.check(source, "zones.create"))
                                    .executes(createCommand::execute))
                            .then(CommandManager.literal("delete")
                                    .requires(source -> Permissions.check(source, "zones.delete"))
                                    .then(CommandManager.argument("key", StringArgumentType.string())
                                            .suggests(RootCommand::regionKeySuggestion)
                                            .executes(deleteCommand::execute)))
                            .then(CommandManager.literal("list")
                                    .requires(source -> Permissions.check(source, "zones.list"))
                                    .executes(context -> listCommand.execute(context, 1))
                                    .then(CommandManager.argument("page", IntegerArgumentType.integer())
                                            .executes(context -> listCommand.execute(context,
                                                    context.getArgument("page", Integer.class)))
                                    ))
                            .then(setCommand.command()
                                    .requires(source -> Permissions.check(source, "zones.set"))
                            )
                            .then(CommandManager.literal("info")
                                    .requires(source -> Permissions.check(source, "zones.info"))
                                    .executes(infoCommand::execute)
                                    .then(CommandManager.argument("key", StringArgumentType.string())
                                            .suggests(RootCommand::regionKeySuggestion)
                                            .executes(infoCommand::execute)))
                            .then(CommandManager.literal("rename")
                                    .requires(source -> Permissions.check(source, "zones.rename"))
                                    .then(CommandManager.argument("key", StringArgumentType.string())
                                            .suggests(RootCommand::regionKeySuggestion)
                                            .then(CommandManager.argument("New Name", StringArgumentType.string())
                                                    .executes(renameCommand::execute))))
                            .then(expandCommand.command()
                                    .requires(source -> Permissions.check(source, "zones.expand")))
            );
        });
    }
}
