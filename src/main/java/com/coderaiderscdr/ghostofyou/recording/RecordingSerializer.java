package com.coderaiderscdr.ghostofyou.recording;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import net.minecraft.nbt.CompoundTag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class RecordingSerializer {

    private static final String KEY_FRAMES      = "frames";
    private static final String KEY_COMPRESSED  = "framesCompressed";
    private static final String KEY_OWNER_NAME  = "ownerName";

    private RecordingSerializer() {}

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
            GhostOfYou.LOGGER.warn("Failed to compress recording frames; storing raw", e);
            stored = raw;
        }

        tag.putByteArray(KEY_FRAMES, stored);
        tag.putBoolean(KEY_COMPRESSED, compressed);
        return tag;
    }

    public static byte[] loadFrameBytes(CompoundTag tag) {
        byte[] stored     = tag.getByteArray(KEY_FRAMES);
        boolean compressed = tag.getBoolean(KEY_COMPRESSED);

        if (!compressed || stored.length == 0) return stored;

        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(stored))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            GhostOfYou.LOGGER.error("Failed to decompress recording frames", e);
            return stored;
        }
    }

    public static String loadOwnerName(CompoundTag tag) {
        return tag.getString(KEY_OWNER_NAME);
    }
}
