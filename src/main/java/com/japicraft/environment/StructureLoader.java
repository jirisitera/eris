package com.japicraft.environment;

import com.japicraft.Eris;
import net.hollowcube.schem.Schematic;
import net.hollowcube.schem.reader.SchematicReader;
import net.kyori.adventure.text.Component;
import net.minestom.server.instance.InstanceContainer;

import java.io.IOException;
import java.io.InputStream;

public final class StructureLoader {

    public static void load(ClassLoader loader, String path, InstanceContainer instance) {
        instance.loadChunk(0, 0);
        instance.loadChunk(1, 0);
        instance.loadChunk(0, 1);
        instance.loadChunk(1, 1);
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new RuntimeException("Could not find structure file in resources!");
            }
            byte[] data = stream.readAllBytes();
            Schematic structure = SchematicReader.structure().read(data);
            structure.createBatch().apply(instance, 0, 10, 0, _ -> Eris.LOGGER.atInfo().log(Component.text("Loaded structure " + path + " into the primary instance.")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
