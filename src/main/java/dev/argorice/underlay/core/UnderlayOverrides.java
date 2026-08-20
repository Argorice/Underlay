package dev.argorice.underlay.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * User configuration is an <em>override on top of tags</em>, not a replacement.
 * Tags define the base set; overrides add and remove individual blocks.
 * This way a mod update that extends the default tags never gets silently
 * shadowed by an old user config.
 *
 * <p>Two instances exist at runtime:
 * <ul>
 * <li>the authoritative one, owned by the (integrated or dedicated) server and
 * persisted to {@code config/underlay-overrides.json};</li>
 * <li>a client-side synced copy used only for GUI display and optimistic checks.</li>
 * </ul>
 */
public final class UnderlayOverrides {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "underlay-overrides.json";

    private static volatile UnderlayOverrides server;
    private static volatile UnderlayOverrides clientSynced = new UnderlayOverrides();

    private final Set<ResourceLocation> added = new LinkedHashSet<>();
    private final Set<ResourceLocation> removed = new LinkedHashSet<>();

    public UnderlayOverrides() {}

    public UnderlayOverrides(Set<ResourceLocation> added, Set<ResourceLocation> removed) {
        this.added.addAll(added);
        this.removed.addAll(removed);
    }

    public Set<ResourceLocation> added() {
        return Collections.unmodifiableSet(added);
    }

    public Set<ResourceLocation> removed() {
        return Collections.unmodifiableSet(removed);
    }

    public synchronized void setAll(Set<ResourceLocation> newAdded, Set<ResourceLocation> newRemoved) {
        added.clear();
        added.addAll(newAdded);
        removed.clear();
        removed.addAll(newRemoved);
    }

    // --- static accessors -------------------------------------------------

    /** The authoritative server-side overrides, loaded lazily from disk. */
    public static UnderlayOverrides server() {
        UnderlayOverrides result = server;
        if (result == null) {
            synchronized (UnderlayOverrides.class) {
                result = server;
                if (result == null) {
                    result = load();
                    server = result;
                }
            }
        }
        return result;
    }

    /** The copy last synced from the server, for client-side GUI display. */
    public static UnderlayOverrides clientView() {
        return clientSynced;
    }

    public static void setClientView(Set<ResourceLocation> added, Set<ResourceLocation> removed) {
        clientSynced = new UnderlayOverrides(added, removed);
    }

    // --- persistence ------------------------------------------------------

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static UnderlayOverrides load() {
        UnderlayOverrides result = new UnderlayOverrides();
        Path path = file();
        if (!Files.exists(path)) {
            return result;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
            readInto(json, "added", result.added);
            readInto(json, "removed", result.removed);
        } catch (Exception e) {
            LOGGER.error("Failed to read {}, starting with empty overrides", path, e);
        }
        return result;
    }

    private static void readInto(JsonObject json, String key, Set<ResourceLocation> target) {
        if (json == null || !json.has(key)) {
            return;
        }
        for (JsonElement el : json.getAsJsonArray(key)) {
            ResourceLocation id = ResourceLocation.tryParse(el.getAsString());
            if (id != null) {
                target.add(id);
            }
        }
    }

    public synchronized void save() {
        JsonObject json = new JsonObject();
        json.add("added", toArray(added));
        json.add("removed", toArray(removed));
        try {
            Files.writeString(file(), GSON.toJson(json));
        } catch (IOException e) {
            LOGGER.error("Failed to save underlay overrides", e);
        }
    }

    private static JsonArray toArray(Set<ResourceLocation> set) {
        JsonArray array = new JsonArray();
        for (ResourceLocation id : set) {
            array.add(id.toString());
        }
        return array;
    }
}
