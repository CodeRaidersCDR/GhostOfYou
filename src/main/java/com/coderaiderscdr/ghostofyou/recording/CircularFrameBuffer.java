package com.coderaiderscdr.ghostofyou.recording;

import java.nio.ByteBuffer;

/**
 * Off-heap circular buffer for player frame recording.
 *
 * <p>Uses {@link ByteBuffer#allocateDirect} so the buffer lives entirely
 * outside the JVM heap — zero GC pressure regardless of buffer size.
 *
 * <p>Each slot is exactly {@link Frame#BYTES} (28) bytes.
 * When the buffer is full the oldest slot is silently overwritten.
 */
public class CircularFrameBuffer {

    private final ByteBuffer buffer;  // direct (off-heap)
    private final int capacity;       // number of frame slots

    /** Index of the NEXT slot to be written (also oldest slot when full). */
    private int writeHead = 0;
    /** Number of frames actually stored (≤ capacity). */
    private int size = 0;

    /**
     * Allocate a new off-heap circular buffer.
     *
     * @param capacityFrames maximum number of 28-byte frames that can be stored
     */
    public CircularFrameBuffer(int capacityFrames) {
        this.capacity = Math.max(1, capacityFrames);
        this.buffer = ByteBuffer.allocateDirect(this.capacity * Frame.BYTES);
    }

    // ------------------------------------------------------------------
    // Write
    // ------------------------------------------------------------------

    /**
     * Write one frame at the current write-head and advance it.
     * If the buffer is full the oldest frame is overwritten (circular).
     * HOT PATH — called once per sample interval per online player.
     */
    public void write(short tickDelta, float deltaX, float deltaZ, float deltaY,
                      short yawCenti, short pitchCenti, byte flags,
                      byte heldSlot, int actionEventId, int blockStateId) {
        Frame.write(buffer, writeHead,
                tickDelta, deltaX, deltaZ, deltaY,
                yawCenti, pitchCenti, flags,
                heldSlot, actionEventId, blockStateId);
        writeHead = (writeHead + 1) % capacity;
        if (size < capacity) size++;
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    /**
     * Convert a logical index (0 = oldest, {@code size-1} = newest) to the
     * physical slot index inside the buffer.
     */
    public int physicalIndex(int logicalIndex) {
        if (size < capacity) {
            return logicalIndex;
        }
        return (writeHead + logicalIndex) % capacity;
    }

    /** Number of frames currently stored. */
    public int size() { return size; }

    /** Maximum number of frames the buffer can hold. */
    public int capacity() { return capacity; }

    // ------------------------------------------------------------------
    // Serialisation helpers
    // ------------------------------------------------------------------

    /**
     * Export all stored frames as an ordered byte array (oldest → newest).
     * This involves a heap allocation; call only during save/death events.
     */
    public byte[] toByteArray() {
        byte[] out = new byte[size * Frame.BYTES];
        if (size == 0) return out;

        ByteBuffer view = buffer.duplicate();  // shares memory, independent position
        if (size < capacity) {
            // Buffer not yet wrapped — contiguous from slot 0
            view.position(0).limit(size * Frame.BYTES);
            view.get(out, 0, out.length);
        } else {
            // Buffer wrapped — two segments: [writeHead..capacity) then [0..writeHead)
            int firstBytes = (capacity - writeHead) * Frame.BYTES;
            view.position(writeHead * Frame.BYTES);
            view.get(out, 0, firstBytes);
            view.position(0);
            view.get(out, firstBytes, writeHead * Frame.BYTES);
        }
        return out;
    }

    /**
     * Load data from a byte array (oldest → newest).
     * Replaces any existing content; the buffer is reset to a non-wrapped state.
     *
     * @param data raw frame bytes — must be a multiple of {@link Frame#BYTES}
     */
    public void fromByteArray(byte[] data) {
        int frames = data.length / Frame.BYTES;
        int count = Math.min(frames, capacity);
        buffer.clear();
        buffer.put(data, 0, count * Frame.BYTES);
        size = count;
        writeHead = count % capacity;
    }

    /** Direct access to the underlying buffer (read-only for callers). */
    public ByteBuffer getBuffer() { return buffer; }
}
