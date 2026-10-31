package com.japicraft.voicechat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class VoiceChatBuffer {
    private final ByteBuffer reader;
    private final ByteArrayOutputStream writer;

    public VoiceChatBuffer(byte[] data) {
        this.reader = ByteBuffer.wrap(data);
        this.writer = null;
    }

    public VoiceChatBuffer() {
        this.reader = null;
        this.writer = new ByteArrayOutputStream();
    }

    public byte readByte() {
        return reader.get();
    }

    public void writeByte(int value) {
        writer.write(value & 0xFF);
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public void writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
    }

    public int readInt() {
        return reader.getInt();
    }

    public void writeInt(int value) {
        writeByte(value >>> 24);
        writeByte(value >>> 16);
        writeByte(value >>> 8);
        writeByte(value);
    }

    public long readLong() {
        return reader.getLong();
    }

    public void writeLong(long value) {
        writeByte((int) (value >>> 56));
        writeByte((int) (value >>> 48));
        writeByte((int) (value >>> 40));
        writeByte((int) (value >>> 32));
        writeByte((int) (value >>> 24));
        writeByte((int) (value >>> 16));
        writeByte((int) (value >>> 8));
        writeByte((int) value);
    }

    public void writeDouble(double value) {
        writeLong(Double.doubleToRawLongBits(value));
    }

    public UUID readUUID() {
        return new UUID(readLong(), readLong());
    }

    public void writeUUID(UUID uuid) {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    public void readRawBytes(byte[] dst) {
        reader.get(dst);
    }

    public void writeRawBytes(byte[] src) {
        writer.writeBytes(src);
    }

    public int readVarInt() {
        int value = 0;
        int position = 0;
        byte currentByte;
        do {
            currentByte = readByte();
            value |= (currentByte & 0x7F) << (position * 7);
            position++;
            if (position > 5) {
                throw new IllegalArgumentException("VarInt is too big");
            }
        } while ((currentByte & 0x80) != 0);
        return value;
    }

    public void writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeByte(value);
    }

    public byte[] readByteArray(int maxLength) {
        int length = readVarInt();
        if (length < 0 || length > maxLength) {
            throw new IllegalArgumentException("ByteArray with size " + length + " is bigger than allowed " + maxLength);
        }
        byte[] bytes = new byte[length];
        readRawBytes(bytes);
        return bytes;
    }

    public void writeByteArray(byte[] bytes) {
        writeVarInt(bytes.length);
        writeRawBytes(bytes);
    }

    public void writeUtf(String string) {
        writeUtf(string, 32767);
    }

    public void writeUtf(String string, int maxLength) {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxLength * 4) {
            throw new IllegalArgumentException("String too big (was " + bytes.length + " bytes encoded, max " + (maxLength * 4) + ")");
        }
        writeVarInt(bytes.length);
        writeRawBytes(bytes);
    }

    public byte[] toByteArray() {
        return writer.toByteArray();
    }
}
