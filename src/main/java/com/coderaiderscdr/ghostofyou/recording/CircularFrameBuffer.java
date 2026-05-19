package com.coderaiderscdr.ghostofyou.recording;

import java.nio.ByteBuffer;

public class CircularFrameBuffer {

    private final ByteBuffer buffer;
    private final int capacity;

    private int writeHead = 0;

    private int size = 0;

    public CircularFrameBuffer(int capacityFrames) {
        this.capacity = Math.max(1, capacityFrames);
        this.buffer = ByteBuffer.allocateDirect(this.capacity * Frame.BYTES);
    }

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

    public int physicalIndex(int logicalIndex) {
        if (size < capacity) {
            return logicalIndex;
        }
        return (writeHead + logicalIndex) % capacity;
    }

    public int size() { return size; }

    public int capacity() { return capacity; }

    public byte[] toByteArray() {
        byte[] out = new byte[size * Frame.BYTES];
        if (size == 0) return out;

        ByteBuffer view = buffer.duplicate();
        if (size < capacity) {

            view.position(0).limit(size * Frame.BYTES);
            view.get(out, 0, out.length);
        } else {

            int firstBytes = (capacity - writeHead) * Frame.BYTES;
            view.position(writeHead * Frame.BYTES);
            view.get(out, 0, firstBytes);
            view.position(0);
            view.get(out, firstBytes, writeHead * Frame.BYTES);
        }
        return out;
    }

    public void fromByteArray(byte[] data) {
        int frames = data.length / Frame.BYTES;
        int count = Math.min(frames, capacity);
        buffer.clear();
        buffer.put(data, 0, count * Frame.BYTES);
        size = count;
        writeHead = count % capacity;
    }

    public ByteBuffer getBuffer() { return buffer; }
}
