package com.coderaiderscdr.ghostofyou.recording;

import com.coderaiderscdr.ghostofyou.util.ModLogger;
import net.minecraft.nbt.CompoundTag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Serialises / deserialises a recording (frame buffer + event log) to/from
 * NBT. Frame data is GZIP-compressed to minimise chunk file sizes.
 */
public final class RecordingSerializer {

    private static final String KEY_FRAMES      = "frames";
    private static final String KEY_COMPRESSED  = "framesCompressed";
    private static final String KEY_OWNER_NAME  = "ownerName";

    private RecordingSerializer() {}

    // ------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------

    /**
     * Serialise a recording to a {@link CompoundTag}.
     *
     * @param buffer    the frame buffer (all recorded frames)
     * @param ownerName the player's display name at the time of recording
     * @return NBT compound ready to embed in {@code GhostEntity} NBT
     */
    public static CompoundTag save(CircularFrameBuffer buffer, String ownerName) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_OWNER_NAME, ownerName);

        byte[] raw = buffer.toByteArray();
        byte[] stored;
        boolean compressed = false;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(raw.length / 3 + 64);
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(raw);
            }
            stored     = baos.toByteArray();
            compressed = true;
        } catch (IOException e) {
            ModLogger.RECORDING.warn("Failed to compress recording frames; storing raw", e);
            stored = raw;
        }

        tag.putByteArray(KEY_FRAMES, stored);
        tag.putBoolean(KEY_COMPRESSED, compressed);
        return tag;
    }

    // ------------------------------------------------------------------
    // Load
    // ------------------------------------------------------------------

    /**
     * Deserialise frame bytes from an NBT compound.
     *
     * @param tag the recording compound previously created by {@link #save}
     * @return raw frame bytes (oldest → newest), may be empty
     */
    public static byte[] loadFrameBytes(CompoundTag tag) {
        byte[] stored     = tag.getByteArray(KEY_FRAMES);
        boolean compressed = tag.getBoolean(KEY_COMPRESSED);

        if (!compressed || stored.length == 0) return stored;

        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(stored))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            ModLogger.RECORDING.error("Failed to decompress recording frames", e);
            return stored; // fallback: treat as uncompressed
        }
    }

    /** Extract the owner display name from a recording tag. */
    public static String loadOwnerName(CompoundTag tag) {
        return tag.getString(KEY_OWNER_NAME);
    }
}
