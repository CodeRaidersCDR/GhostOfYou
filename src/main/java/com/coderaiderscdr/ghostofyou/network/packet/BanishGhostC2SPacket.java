package com.coderaiderscdr.ghostofyou.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server packet sent when a client explicitly requests to banish a ghost.
 * (Currently unused during normal banishing, which is handled server-side via
 * entity interaction, but available for extension — e.g. a GUI "banish" button.)
 */
public class BanishGhostC2SPacket {

    private final UUID ghostUUID;

    public BanishGhostC2SPacket(UUID ghostUUID) {
        this.ghostUUID = ghostUUID;
    }

    // ------------------------------------------------------------------
    // Codec
    // ------------------------------------------------------------------

    public static BanishGhostC2SPacket decode(FriendlyByteBuf buf) {
        return new BanishGhostC2SPacket(buf.readUUID());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(ghostUUID);
    }

    // ------------------------------------------------------------------
    // Handler (runs on the server main thread)
    // ------------------------------------------------------------------

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctx.getSender();
            if (player == null) return;

            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            net.minecraft.world.entity.Entity entity = level.getEntity(ghostUUID);
            if (entity instanceof com.coderaiderscdr.ghostofyou.entity.GhostEntity ghost
                    && ghost.isAlive()) {
                // Verify player is close enough (prevent exploit: remote removal)
                if (player.distanceToSqr(ghost) > 16.0 * 16.0) return;
                ghost.discard();
            }
        });
        ctx.setPacketHandled(true);
    }

    public UUID getGhostUUID() { return ghostUUID; }
}
