package dev.featherlight;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class Featherlight implements ClientModInitializer {
    public static final String MOD_ID = "featherlight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final long SECOND_NS = 1_000_000_000L;

    private static FeatherlightConfig config = new FeatherlightConfig();
    private static Path configPath;
    private static String version = "dev";

    // Render-thread only state.
    private static double scale = 1.0;
    private static long lastSecondNs = 0L;
    private static int culledThisSecond = 0;
    private static int culledLastSecond = 0;
    private static boolean announced = false;
    private static boolean entityHookSeen = false;
    private static boolean videoHookActive = false;

    @Override
    public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("featherlight.json");
        config = FeatherlightConfig.loadOrCreate(configPath);
        FabricLoader.getInstance().getModContainer(MOD_ID)
                .ifPresent(c -> version = c.getMetadata().getVersion().getFriendlyString());
        LOGGER.info("Featherlight {} loaded (culling {}, adaptive {})", version,
                config.enabled ? "on" : "off", config.adaptive ? "on" : "off");
    }

    // ---- state used by the settings menu -------------------------------------------------

    public static FeatherlightConfig config() {
        return config;
    }

    public static String version() {
        return version;
    }

    /** Applies and saves a config edited in the menu. */
    public static void apply(FeatherlightConfig edited) {
        FeatherlightConfig next = edited.copy();
        next.sanitize();
        config = next;
        scale = Math.max(next.minScale, Math.min(1.0, scale));
        FeatherlightConfig.save(configPath, next);
    }

    public static double scale() {
        return scale;
    }

    public static int culledLastSecond() {
        return culledLastSecond;
    }

    /** True once the entity render hook has run at least once (proves the mixin applied). */
    public static boolean entityHookSeen() {
        return entityHookSeen;
    }

    public static boolean videoHookActive() {
        return videoHookActive;
    }

    public static void markVideoHookActive() {
        videoHookActive = true;
    }

    // ---- culling -------------------------------------------------------------------------

    /**
     * Called from the entity render mixins. Returns true if the entity should be skipped this frame.
     * Cheap on purpose: a few field reads and one squared-distance comparison.
     */
    public static boolean shouldCull(Entity entity, double camX, double camY, double camZ) {
        entityHookSeen = true;
        FeatherlightConfig c = config;
        if (!c.enabled) {
            return false;
        }
        // Never hide players, highlighted entities, or whatever the camera is attached to.
        if (entity instanceof Player || entity.isCurrentlyGlowing()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.getCameraEntity()) {
            return false;
        }

        long now = System.nanoTime();
        if (now - lastSecondNs >= SECOND_NS) {
            lastSecondNs = now;
            culledLastSecond = culledThisSecond;
            culledThisSecond = 0;
            if (c.adaptive) {
                adjustScale(mc, c);
            }
        }

        double base = (entity instanceof ItemEntity || entity instanceof ExperienceOrb)
                ? c.itemCullDistance
                : c.entityCullDistance;
        double size = (entity.getBbWidth() + entity.getBbHeight()) * 0.5;
        double limit = (base + Math.max(0.0, size - 1.0) * c.sizeBonusPerBlock) * scale;

        boolean cull = entity.distanceToSqr(camX, camY, camZ) > limit * limit;
        if (cull) {
            culledThisSecond++;
            if (!announced) {
                announced = true;
                LOGGER.info("Featherlight: entity culling is active");
            }
        }
        return cull;
    }

    /** Once per second, nudge the distance multiplier based on the current FPS. */
    private static void adjustScale(Minecraft mc, FeatherlightConfig c) {
        int fps = mc.getFps();
        if (fps < c.targetFps * 0.9) {
            scale = Math.max(c.minScale, scale - 0.05);
        } else if (fps >= c.targetFps) {
            scale = Math.min(1.0, scale + 0.02);
        }
    }
}
