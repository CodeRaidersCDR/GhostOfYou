package com.coderaiderscdr.ghostofyou.command;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import com.coderaiderscdr.ghostofyou.config.ModConfig;
import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.coderaiderscdr.ghostofyou.event.PlayerTickHandler;
import com.coderaiderscdr.ghostofyou.recording.PlayerRecorder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * Admin commands for Ghost of You (requires permission level 2).
 *
 * <ul>
 *   <li>{@code /ghostofyou remove player <player>}</li>
 *   <li>{@code /ghostofyou remove all}</li>
 *   <li>{@code /ghostofyou remove nearby <radius>}</li>
 *   <li>{@code /ghostofyou list}</li>
 *   <li>{@code /ghostofyou pause <true|false>}</li>
 *   <li>{@code /ghostofyou stats}</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GhostOfYou.MOD_ID)
public class GhostOfYouCommand {

    private GhostOfYouCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * Register all sub-commands on the Brigadier dispatcher.
     *
     * @param dispatcher the server's command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ghostofyou")
                        .requires(src -> src.hasPermission(2))

                        .then(Commands.literal("remove")
                                .then(Commands.literal("player")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> removeByPlayer(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("all")
                                        .executes(ctx -> removeAll(ctx.getSource())))
                                .then(Commands.literal("nearby")
                                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 1000))
                                                .executes(ctx -> removeNearby(ctx.getSource(),
                                                        DoubleArgumentType.getDouble(ctx, "radius"))))))

                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource())))

                        .then(Commands.literal("pause")
                                .then(Commands.argument("paused", BoolArgumentType.bool())
                                        .executes(ctx -> setPaused(ctx.getSource(),
                                                BoolArgumentType.getBool(ctx, "paused")))))

                        .then(Commands.literal("stats")
                                .executes(ctx -> stats(ctx.getSource())))
        );
    }

    // ------------------------------------------------------------------
    // Remove by player
    // ------------------------------------------------------------------

    private static int removeByPlayer(CommandSourceStack src, ServerPlayer target) {
        UUID ownerUUID = target.getUUID();
        ServerLevel level = src.getLevel();
        int[] count = {0};

        List<GhostEntity> toRemove = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof GhostEntity g && ownerUUID.equals(g.getOwnerUUID())) toRemove.add(g);
        }
        toRemove.forEach(g -> { g.discard(); count[0]++; });

        src.sendSuccess(() -> Component.translatable("ghostofyou.command.removed", count[0]), true);
        return count[0];
    }

    // ------------------------------------------------------------------
    // Remove all
    // ------------------------------------------------------------------

    private static int removeAll(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        int[] count = {0};

        List<GhostEntity> toRemove = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof GhostEntity g) toRemove.add(g);
        }
        toRemove.forEach(g -> { g.discard(); count[0]++; });

        src.sendSuccess(() -> Component.translatable("ghostofyou.command.removed", count[0]), true);
        return count[0];
    }

    // ------------------------------------------------------------------
    // Remove nearby
    // ------------------------------------------------------------------

    private static int removeNearby(CommandSourceStack src, double radius) {
        Vec3 pos = src.getPosition();
        ServerLevel level = src.getLevel();
        AABB box = new AABB(pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);

        List<GhostEntity> ghosts = level.getEntitiesOfClass(GhostEntity.class, box,
                g -> g.distanceToSqr(pos) <= radius * radius);

        ghosts.forEach(g -> g.discard());
        final int count = ghosts.size();

        src.sendSuccess(() -> Component.translatable("ghostofyou.command.removed", count), true);
        return count;
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    private static int list(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        List<GhostEntity> ghosts = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof GhostEntity g) ghosts.add(g);
        }

        src.sendSuccess(() -> Component.translatable("ghostofyou.command.list.header", ghosts.size()), false);
        for (GhostEntity g : ghosts) {
            src.sendSuccess(() -> Component.translatable("ghostofyou.command.list.entry",
                    g.getOwnerName(), g.getX(), g.getY(), g.getZ()), false);
        }
        return ghosts.size();
    }

    // ------------------------------------------------------------------
    // Pause
    // ------------------------------------------------------------------

    private static int setPaused(CommandSourceStack src, boolean paused) {
        ModConfig.COMMON.pauseAllPlayback.set(paused);
        src.sendSuccess(() -> Component.translatable("ghostofyou.command.paused", paused), true);
        return paused ? 1 : 0;
    }

    // ------------------------------------------------------------------
    // Stats
    // ------------------------------------------------------------------

    private static int stats(CommandSourceStack src) {
        src.sendSuccess(() -> Component.translatable("ghostofyou.command.stats.header"), false);

        for (PlayerRecorder recorder : PlayerTickHandler.getAllRecorders()) {
            var buf   = recorder.getFrameBuffer();
            float kb  = buf.size() * 28 / 1024f;
            String name = recorder.getPlayer().getName().getString();
            src.sendSuccess(() -> Component.translatable("ghostofyou.command.stats.entry",
                    name, buf.size(), buf.capacity(), kb), false);
        }

        return 1;
    }
}
