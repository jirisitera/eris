package com.japicraft.voicechat;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public final class VoiceChatSecret {
    public static final int SECRET_SIZE_BYTES = 16;
    public static final int IV_SIZE_BYTES = 12;
    public static final int TAG_LEN_BITS = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final SecretKeySpec keySpec;

    private VoiceChatSecret(byte[] secret) {
        this.secret = secret;
        this.keySpec = new SecretKeySpec(secret, "AES");
    }

    public static VoiceChatSecret generate() {
        byte[] secret = new byte[SECRET_SIZE_BYTES];
        RANDOM.nextBytes(secret);
        return new VoiceChatSecret(secret);
    }

    public static VoiceChatSecret fromBytes(VoiceChatBuffer buf) {
        byte[] secretBytes = new byte[SECRET_SIZE_BYTES];
        buf.readRawBytes(secretBytes);
        return new VoiceChatSecret(secretBytes);
    }

    public void toBytes(VoiceChatBuffer buf) {
        buf.writeRawBytes(secret);
    }

    public byte[] encrypt(byte[] data) throws GeneralSecurityException {
        byte[] iv = new byte[IV_SIZE_BYTES];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN_BITS, iv));
        byte[] encrypted = cipher.doFinal(data);
        byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        return payload;
    }

    public byte[] decrypt(byte[] payload) throws GeneralSecurityException {
        if (payload.length < IV_SIZE_BYTES) {
            throw new GeneralSecurityException("Payload shorter than IV");
        }
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_SIZE_BYTES);
        byte[] data = Arrays.copyOfRange(payload, IV_SIZE_BYTES, payload.length);
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN_BITS, iv));
        return cipher.doFinal(data);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VoiceChatSecret other)) {
            return false;
        }
        return Arrays.equals(secret, other.secret);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(secret);
    }
}
