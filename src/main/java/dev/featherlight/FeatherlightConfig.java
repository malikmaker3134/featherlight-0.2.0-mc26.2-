package dev.featherlight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Plain JSON config stored at config/featherlight.json. */
public final class FeatherlightConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final double EPS = 1e-6;

    /** Master switch for entity culling. */
    public boolean enabled = true;
    /** Base cull distance in blocks for average-sized mobs. */
    public double entityCullDistance = 48.0;
    /** Base cull distance in blocks for dropped items and XP orbs. */
    public double itemCullDistance = 24.0;
    /** Extra blocks of range per block of bounding-box size above 1 (keeps big mobs visible). */
    public double sizeBonusPerBlock = 8.0;
    /** Tighten culling automatically when FPS is below the target. */
    public boolean adaptive = true;
    public int targetFps = 60;
    /** Lowest multiplier the adaptive mode may apply to cull distances (0.25 - 1.0). */
    public double minScale = 0.5;
    /** Show the Featherlight menu when opening Options > Video Settings. */
    public boolean replaceVideoSettings = true;

    public FeatherlightConfig copy() {
        FeatherlightConfig c = new FeatherlightConfig();
        c.copyFrom(this);
        return c;
    }

    public void copyFrom(FeatherlightConfig o) {
        enabled = o.enabled;
        entityCullDistance = o.entityCullDistance;
        itemCullDistance = o.itemCullDistance;
        sizeBonusPerBlock = o.sizeBonusPerBlock;
        adaptive = o.adaptive;
        targetFps = o.targetFps;
        minScale = o.minScale;
        replaceVideoSettings = o.replaceVideoSettings;
    }

    public boolean sameAs(FeatherlightConfig o) {
        return enabled == o.enabled
                && Math.abs(entityCullDistance - o.entityCullDistance) < EPS
                && Math.abs(itemCullDistance - o.itemCullDistance) < EPS
                && Math.abs(sizeBonusPerBlock - o.sizeBonusPerBlock) < EPS
                && adaptive == o.adaptive
                && targetFps == o.targetFps
                && Math.abs(minScale - o.minScale) < EPS
                && replaceVideoSettings == o.replaceVideoSettings;
    }

    public static FeatherlightConfig loadOrCreate(Path path) {
        FeatherlightConfig cfg = new FeatherlightConfig();
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    FeatherlightConfig loaded = GSON.fromJson(reader, FeatherlightConfig.class);
                    if (loaded != null) {
                        cfg = loaded;
                    }
                }
            }
            cfg.sanitize();
            save(path, cfg);
        } catch (IOException | RuntimeException e) {
            Featherlight.LOGGER.warn("Could not read or write {}, using defaults", path, e);
            cfg = new FeatherlightConfig();
        }
        return cfg;
    }

    public static void save(Path path, FeatherlightConfig cfg) {
        if (path == null) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(cfg, writer);
        } catch (IOException e) {
            Featherlight.LOGGER.warn("Could not save {}", path, e);
        }
    }

    void sanitize() {
        entityCullDistance = Math.max(16.0, entityCullDistance);
        itemCullDistance = Math.max(8.0, itemCullDistance);
        sizeBonusPerBlock = Math.max(0.0, sizeBonusPerBlock);
        targetFps = Math.max(20, targetFps);
        minScale = Math.min(1.0, Math.max(0.25, minScale));
    }
}
