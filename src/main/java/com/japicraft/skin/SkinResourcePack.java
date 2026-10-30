package com.japicraft.skin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SkinResourcePack {
    public static final String SKIN_TEXTURE_PATH = "assets/eris/textures/item/skin.png";
    private static final byte[] PACK_META = """
            {
                "pack": {
                    "description": [
                        {
                            "color": "#FF5533",
                            "text": "Eris Dynamic Modules Pack"
                        }
                    ],
                    "min_format": 97,
                    "max_format": 97
                }
            }
        """.getBytes();

    public static PackData create(UUID packId, byte[] skinPngBytes) throws IOException {
        byte[] zipBytes = packagePack(skinPngBytes, SKIN_TEXTURE_PATH);
        return new PackData(packId, zipBytes, computeSha1(zipBytes));
    }

    public static byte[] packagePack(byte[] skinPngBytes, String skinTexturePath) throws IOException {
        return packagePack(Map.of("pack.mcmeta", PACK_META, skinTexturePath, skinPngBytes));
    }

    public static byte[] packagePack(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try (ZipOutputStream zipStream = new ZipOutputStream(stream)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                zipStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipStream.write(entry.getValue());
                zipStream.closeEntry();
            }
        }
        return stream.toByteArray();
    }

    public static String computeSha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 message digest not available", e);
        }
    }

    public record PackData(UUID packId, byte[] zipBytes, String sha1Hex) {
    }
}
