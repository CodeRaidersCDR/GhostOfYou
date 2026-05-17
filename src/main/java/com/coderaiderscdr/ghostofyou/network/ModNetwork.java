package com.coderaiderscdr.ghostofyou.network;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import com.coderaiderscdr.ghostofyou.network.packet.BanishGhostC2SPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Registers the mod's {@link SimpleChannel} and all network packets.
 * Called during {@code FMLCommonSetupEvent} via {@code event.enqueueWork}.
 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    /** The mod's single network channel. */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GhostOfYou.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    /** Called once from {@code FMLCommonSetupEvent}. */
    public static void register() {
        CHANNEL.messageBuilder(BanishGhostC2SPacket.class, nextId++,
                        net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .decoder(BanishGhostC2SPacket::decode)
                .encoder(BanishGhostC2SPacket::encode)
                .consumerMainThread(BanishGhostC2SPacket::handle)
                .add();
    }

    private ModNetwork() {}
}
