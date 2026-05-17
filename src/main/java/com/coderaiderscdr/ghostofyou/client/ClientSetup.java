package com.coderaiderscdr.ghostofyou.client;

/**
 * Additional client setup performed during {@code FMLClientSetupEvent}.
 * Currently a placeholder; extend as needed (e.g. register key bindings,
 * config screen overrides).
 */
public final class ClientSetup {

    private ClientSetup() {}

    /** Called during {@code FMLClientSetupEvent} via {@code event.enqueueWork}. */
    public static void setup() {
        // Key binding registration and other client-only setup goes here.
    }
}
