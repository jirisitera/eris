package com.japicraft.server;

import com.japicraft.Eris;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public class GlobalResourcePack {
    private static String calculateHash(URI uri) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            // download file
            HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder().uri(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            // calculate SHA1 hash
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(response.body()));
        } catch (InterruptedException | IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static UUID generateUniqueId(String hash) {
        return UUID.nameUUIDFromBytes(hash.getBytes());
    }

    public static void register() {
        // prepare resource pack
        URI packURI = URI.create("https://github.com/jirisitera/eris/releases/latest/download/eris.zip");
        String packHash = GlobalResourcePack.calculateHash(packURI);
        if (packHash == null) {
            return;
        }
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
            .prompt(Component.text("This server requires a resource pack. Would you like to download it?"))
            .packs(ResourcePackInfo.resourcePackInfo(GlobalResourcePack.generateUniqueId(packHash), packURI, packHash))
            .required(true)
            .build();
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> event.getPlayer().sendResourcePacks(request));
        Eris.LOGGER.atInfo().log(Component.text("Loaded global resource pack."));
    }
}
