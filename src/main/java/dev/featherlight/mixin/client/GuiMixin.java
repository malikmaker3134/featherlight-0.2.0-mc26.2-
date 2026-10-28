package dev.featherlight.mixin.client;

import dev.featherlight.gui.ScreenHook;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps the vanilla Video Settings screen for the Featherlight menu (like Sodium does).
 * Since 26.2 setScreen lives on Gui. If it moves, the hook is skipped and the menu is unreachable.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @ModifyVariable(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen featherlight$swapVideoSettings(Screen screen) {
        return ScreenHook.maybeReplace(screen);
    }
}
