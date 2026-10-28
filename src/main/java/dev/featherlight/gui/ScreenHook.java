package dev.featherlight.gui;

import dev.featherlight.Featherlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Decides when the Featherlight menu replaces the vanilla Video Settings screen. */
public final class ScreenHook {
    private static boolean bypass = false;
    private static boolean failed = false;

    private ScreenHook() {
    }

    public static Screen maybeReplace(Screen screen) {
        if (screen == null || bypass || failed) {
            return screen;
        }
        try {
            if (!Featherlight.config().replaceVideoSettings) {
                return screen;
            }
            // Matched by name so a rename can never break compilation, only this hook.
            if (!"VideoSettingsScreen".equals(screen.getClass().getSimpleName())) {
                return screen;
            }
            Featherlight.markVideoHookActive();
            Screen parent = Minecraft.getInstance().gui.screen();
            return FeatherlightScreen.create(parent, screen);
        } catch (Throwable t) {
            failed = true;
            Featherlight.LOGGER.error("Featherlight menu failed, falling back to vanilla video settings", t);
            return screen;
        }
    }

    /** Opens the original vanilla screen without being intercepted again. */
    static void openVanilla(Screen vanilla) {
        bypass = true;
        try {
            Minecraft.getInstance().gui.setScreen(vanilla);
        } finally {
            bypass = false;
        }
    }
}
