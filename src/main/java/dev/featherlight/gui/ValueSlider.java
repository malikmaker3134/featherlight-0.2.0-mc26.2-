package dev.featherlight.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/**
 * Vanilla slider behavior (mouse drag, keyboard, narration) with the visuals drawn by
 * FeatherlightScreen. Snaps to a step and reports the real value to a setter.
 */
final class ValueSlider extends AbstractSliderButton {
    private final double min;
    private final double max;
    private final double step;
    private final DoubleConsumer setter;

    ValueSlider(int x, int y, int width, int height, String name,
                double min, double max, double step, double current, DoubleConsumer setter) {
        super(x, y, width, height, Component.literal(name), (current - min) / (max - min));
        this.min = min;
        this.max = max;
        this.step = step;
        this.setter = setter;
    }

    double normalized() {
        return this.value;
    }

    @Override
    protected void updateMessage() {
        // Visuals are drawn by the screen.
    }

    @Override
    protected void applyValue() {
        double raw = this.min + this.value * (this.max - this.min);
        double snapped = Math.round(raw / this.step) * this.step;
        snapped = Math.max(this.min, Math.min(this.max, snapped));
        this.value = (snapped - this.min) / (this.max - this.min);
        this.setter.accept(snapped);
    }
}
