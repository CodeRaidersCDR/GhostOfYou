package com.coderaiderscdr.ghostofyou.event;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.recording.ActionEvent;
import com.coderaiderscdr.ghostofyou.recording.PlayerRecorder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BlockActionHandler {

    private BlockActionHandler() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ConfigManager.isRecordingEnabled()) return;
        if (!ConfigManager.recordBlockActions()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        PlayerRecorder recorder = PlayerTickHandler.getRecorder(player.getUUID());
        if (recorder == null) return;

        recorder.recordActionEvent(
                ActionEvent.builder(ActionEvent.Type.BREAK_BLOCK)
                        .pos(event.getPos())
                        .blockState(event.getState())
                        .build());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!ConfigManager.isRecordingEnabled()) return;
        if (!ConfigManager.recordBlockActions()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerRecorder recorder = PlayerTickHandler.getRecorder(player.getUUID());
        if (recorder == null) return;

        recorder.recordActionEvent(
                ActionEvent.builder(ActionEvent.Type.PLACE_BLOCK)
                        .pos(event.getPos())
                        .blockState(event.getPlacedBlock())
                        .build());
    }
}
