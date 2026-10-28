package dev.featherlight.gui;

import dev.featherlight.Featherlight;
import dev.featherlight.FeatherlightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * A Sodium-style settings screen: page tabs on the left, option rows on the right, and
 * Apply / Undo / Close at the bottom. Changes are staged in a draft and only take effect on Apply.
 *
 * Design note: every interactive element is a plain vanilla widget made invisible (alpha 0) and
 * all visuals are drawn in extractBackground. That keeps this class free of custom input and
 * widget-render overrides, which change often between Minecraft versions.
 */
public final class FeatherlightScreen extends Screen {
    enum Page {
        CULLING("Culling"),
        ADAPTIVE("Adaptive"),
        ABOUT("About");

        final String title;

        Page(String title) {
            this.title = title;
        }
    }

    // Palette (ARGB)
    private static final int DIM = 0xB0080A0E;
    private static final int PANEL = 0xF2121620;
    private static final int SIDEBAR = 0xFF171C28;
    private static final int BORDER = 0xFF2B3345;
    private static final int ROW = 0xFF1B2130;
    private static final int ROW_HOVER = 0xFF252D42;
    private static final int ROW_DARK = 0xFF121722;
    private static final int ACCENT = 0xFF5EEAD4;
    private static final int ACCENT_DIM = 0xFF1B4F49;
    private static final int TEXT = 0xFFE6EAF2;
    private static final int TEXT_DIM = 0xFF8D97AB;
    private static final int WARN = 0xFFF5B342;

    // Layout
    private static final int SIDEBAR_W = 124;
    private static final int HEADER_H = 30;
    private static final int FOOTER_H = 34;
    private static final int ROW_H = 26;
    private static final int ROW_GAP = 4;
    private static final int TOGGLE_W = 64;
    private static final int SLIDER_W = 130;
    private static final int BTN_W = 72;
    private static final int BTN_H = 20;

    private final Screen parent;
    private final Screen vanillaVideo;
    private final FeatherlightConfig draft;
    private Page page;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int rowX;
    private int rowW;
    private int nextRowY;

    private final List<Row> rows = new ArrayList<>();
    private final List<Tab> tabs = new ArrayList<>();
    private Button vanillaButton;
    private Button applyButton;
    private Button undoButton;
    private Button closeButton;

    private FeatherlightScreen(Screen parent, Screen vanillaVideo, Page page, FeatherlightConfig draft) {
        super(Component.literal("Featherlight"));
        this.parent = parent;
        this.vanillaVideo = vanillaVideo;
        this.page = page;
        this.draft = draft;
    }

    static FeatherlightScreen create(Screen parent, Screen vanillaVideo) {
        return new FeatherlightScreen(parent, vanillaVideo, Page.CULLING, Featherlight.config().copy());
    }

    // ---- setup ---------------------------------------------------------------------------

    @Override
    protected void init() {
        rows.clear();
        tabs.clear();

        pw = Math.min(this.width - 24, 600);
        ph = Math.min(this.height - 24, 280);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        int contentX = px + SIDEBAR_W + 1;
        int contentW = pw - SIDEBAR_W - 2;
        rowX = contentX + 8;
        rowW = contentW - 16;
        nextRowY = py + HEADER_H + 10;

        // Sidebar tabs
        int ty = py + HEADER_H + 10;
        for (Page p : Page.values()) {
            Button b = hitbox(px + 6, ty, SIDEBAR_W - 12, 22, p.title, btn -> {
                this.page = p;
                this.rebuildWidgets();
            });
            tabs.add(new Tab(p, b));
            ty += 26;
        }

        if (vanillaVideo != null) {
            vanillaButton = hitbox(px + 6, py + ph - 30, SIDEBAR_W - 12, 22, "Vanilla Video...",
                    btn -> ScreenHook.openVanilla(vanillaVideo));
            vanillaButton.setTooltip(Tooltip.create(Component.literal("Open Minecraft's built-in video settings.")));
        }

        // Page content
        switch (page) {
            case CULLING -> {
                addToggle("Entity culling", "Skip drawing entities beyond the distances below. Entities keep behaving normally.",
                        () -> draft.enabled, v -> draft.enabled = v,
                        () -> draft.enabled != Featherlight.config().enabled);
                addSlider("Mob distance", "How far away (in blocks) average-sized mobs are still drawn.",
                        16, 128, 4, () -> draft.entityCullDistance, v -> draft.entityCullDistance = v,
                        "%.0f blocks", () -> differs(draft.entityCullDistance, Featherlight.config().entityCullDistance));
                addSlider("Item distance", "How far away dropped items and XP orbs are still drawn.",
                        8, 64, 4, () -> draft.itemCullDistance, v -> draft.itemCullDistance = v,
                        "%.0f blocks", () -> differs(draft.itemCullDistance, Featherlight.config().itemCullDistance));
                addSlider("Big mob bonus", "Extra range per block of mob size, so large mobs are not hidden too early.",
                        0, 16, 1, () -> draft.sizeBonusPerBlock, v -> draft.sizeBonusPerBlock = v,
                        "+%.0f / block", () -> differs(draft.sizeBonusPerBlock, Featherlight.config().sizeBonusPerBlock));
            }
            case ADAPTIVE -> {
                addToggle("Adaptive scaling", "Shrink the culling distances automatically when FPS drops below the target.",
                        () -> draft.adaptive, v -> draft.adaptive = v,
                        () -> draft.adaptive != Featherlight.config().adaptive);
                addSlider("Target FPS", "Adaptive scaling tightens culling below 90% of this and relaxes at or above it.",
                        30, 240, 5, () -> draft.targetFps, v -> draft.targetFps = (int) Math.round(v),
                        "%.0f fps", () -> draft.targetFps != Featherlight.config().targetFps);
                addSlider("Minimum scale", "The smallest fraction of the culling distances adaptive scaling may use.",
                        0.25, 1.0, 0.05, () -> draft.minScale, v -> draft.minScale = v,
                        "%.2fx", () -> differs(draft.minScale, Featherlight.config().minScale));
            }
            case ABOUT -> {
                // Drawn as plain text in extractBackground.
            }
        }

        // Footer buttons (right-aligned)
        int fy = py + ph - FOOTER_H + (FOOTER_H - BTN_H) / 2;
        int closeX = px + pw - 10 - BTN_W;
        int undoX = closeX - 6 - BTN_W;
        int applyX = undoX - 6 - BTN_W;
        applyButton = hitbox(applyX, fy, BTN_W, BTN_H, "Apply", btn -> Featherlight.apply(draft));
        undoButton = hitbox(undoX, fy, BTN_W, BTN_H, "Undo", btn -> {
            draft.copyFrom(Featherlight.config());
            this.rebuildWidgets();
        });
        closeButton = hitbox(closeX, fy, BTN_W, BTN_H, "Close", btn -> this.onClose());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    private Button hitbox(int x, int y, int w, int h, String label, Button.OnPress onPress) {
        Button b = Button.builder(Component.literal(label), onPress).bounds(x, y, w, h).build();
        b.setAlpha(0.0F);
        return this.addRenderableWidget(b);
    }

    private void addToggle(String label, String tip, BooleanSupplier get, Consumer<Boolean> set, BooleanSupplier dirty) {
        int y = nextRowY;
        nextRowY += ROW_H + ROW_GAP;
        int cx = rowX + rowW - TOGGLE_W - 6;
        Button b = hitbox(cx, y + 3, TOGGLE_W, ROW_H - 6, label, btn -> set.accept(!get.getAsBoolean()));
        b.setTooltip(Tooltip.create(Component.literal(tip)));
        rows.add(new Row(label, y, dirty, b, get, null, null));
    }

    private void addSlider(String label, String tip, double min, double max, double step,
                           DoubleSupplier get, DoubleConsumer set, String format, BooleanSupplier dirty) {
        int y = nextRowY;
        nextRowY += ROW_H + ROW_GAP;
        int cx = rowX + rowW - SLIDER_W - 6;
        ValueSlider s = new ValueSlider(cx, y + 3, SLIDER_W, ROW_H - 6, label, min, max, step, get.getAsDouble(), set);
        s.setAlpha(0.0F);
        s.setTooltip(Tooltip.create(Component.literal(tip)));
        this.addRenderableWidget(s);
        rows.add(new Row(label, y, dirty, s, null, s, () -> String.format(Locale.ROOT, format, get.getAsDouble())));
    }

    private static boolean differs(double a, double b) {
        return Math.abs(a - b) > 1e-6;
    }

    private boolean hasChanges() {
        return !draft.sameAs(Featherlight.config());
    }

    // ---- drawing -------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        boolean changes = hasChanges();
        applyButton.active = changes;
        undoButton.active = changes;

        g.fill(0, 0, this.width, this.height, DIM);
        box(g, px, py, px + pw, py + ph, PANEL, BORDER);

        // Sidebar + header
        g.fill(px + 1, py + 1, px + SIDEBAR_W, py + ph - 1, SIDEBAR);
        g.fill(px + SIDEBAR_W, py + 1, px + SIDEBAR_W + 1, py + ph - 1, BORDER);
        g.fill(px + 1, py + HEADER_H, px + pw - 1, py + HEADER_H + 1, BORDER);
        g.text(this.font, "Featherlight", px + 12, py + 11, ACCENT, true);
        String version = "v" + Featherlight.version();
        g.text(this.font, version, px + pw - 12 - this.font.width(version), py + 11, TEXT_DIM, false);
        g.text(this.font, page.title, px + SIDEBAR_W + 14, py + 11, TEXT, false);

        for (Tab t : tabs) {
            drawTab(g, t);
        }
        if (vanillaButton != null) {
            int x0 = vanillaButton.getX();
            int y0 = vanillaButton.getY();
            boolean hov = vanillaButton.isHoveredOrFocused();
            box(g, x0, y0, x0 + vanillaButton.getWidth(), y0 + vanillaButton.getHeight(),
                    hov ? ROW_HOVER : ROW_DARK, BORDER);
            g.text(this.font, "Vanilla Video...", x0 + 8, y0 + 7, hov ? TEXT : TEXT_DIM, false);
        }

        if (page == Page.ABOUT) {
            drawAbout(g);
        } else {
            for (Row r : rows) {
                drawRow(g, r);
            }
        }

        // Footer
        int footerY = py + ph - FOOTER_H;
        g.fill(px + SIDEBAR_W + 1, footerY, px + pw - 1, footerY + 1, BORDER);
        String status = String.format(Locale.ROOT, "Scale %.2fx   Culled/s %d", Featherlight.scale(), Featherlight.culledLastSecond());
        g.text(this.font, status, px + SIDEBAR_W + 14, footerY + (FOOTER_H - 8) / 2, TEXT_DIM, false);
        drawFooterButton(g, applyButton, "Apply", true);
        drawFooterButton(g, undoButton, "Undo", false);
        drawFooterButton(g, closeButton, "Close", false);
    }

    private void drawTab(GuiGraphicsExtractor g, Tab t) {
        boolean selected = t.page == page;
        boolean hov = t.button.isHoveredOrFocused();
        int x0 = t.button.getX();
        int y0 = t.button.getY();
        int x1 = x0 + t.button.getWidth();
        int y1 = y0 + t.button.getHeight();
        if (selected) {
            g.fill(x0, y0, x1, y1, ROW_HOVER);
            g.fill(x0, y0, x0 + 2, y1, ACCENT);
        } else if (hov) {
            g.fill(x0, y0, x1, y1, ROW);
        }
        g.text(this.font, t.page.title, x0 + 10, y0 + (t.button.getHeight() - 8) / 2,
                selected ? TEXT : TEXT_DIM, false);
    }

    private void drawRow(GuiGraphicsExtractor g, Row r) {
        boolean hov = r.control.isHoveredOrFocused();
        g.fill(rowX, r.y, rowX + rowW, r.y + ROW_H, hov ? ROW_HOVER : ROW);
        if (r.dirty.getAsBoolean()) {
            g.fill(rowX, r.y, rowX + 2, r.y + ROW_H, ACCENT); // pending change marker
        }
        g.text(this.font, r.label, rowX + 10, r.y + (ROW_H - 8) / 2, TEXT, false);

        int cx = r.control.getX();
        int cy = r.control.getY();
        int cw = r.control.getWidth();
        int ch = r.control.getHeight();
        if (r.slider == null) {
            boolean on = r.on.getAsBoolean();
            box(g, cx, cy, cx + cw, cy + ch, on ? ACCENT_DIM : ROW_DARK, on ? ACCENT : BORDER);
            String s = on ? "ON" : "OFF";
            g.text(this.font, s, cx + (cw - this.font.width(s)) / 2, cy + (ch - 8) / 2, on ? ACCENT : TEXT_DIM, false);
        } else {
            int t0 = cx + 4;
            int t1 = cx + cw - 4;
            int mid = cy + ch / 2;
            int hx = t0 + (int) Math.round((t1 - t0) * r.slider.normalized());
            g.fill(t0, mid - 1, t1, mid + 1, BORDER);
            g.fill(t0, mid - 1, hx, mid + 1, ACCENT);
            g.fill(hx - 2, cy + 3, hx + 2, cy + ch - 3, hov ? TEXT : ACCENT);
            String s = r.valueText.get();
            g.text(this.font, s, cx - 10 - this.font.width(s), r.y + (ROW_H - 8) / 2, TEXT_DIM, false);
        }
    }

    private void drawFooterButton(GuiGraphicsExtractor g, Button b, String label, boolean primary) {
        int x0 = b.getX();
        int y0 = b.getY();
        int x1 = x0 + b.getWidth();
        int y1 = y0 + b.getHeight();
        boolean hov = b.active && b.isHoveredOrFocused();
        int textColor;
        if (!b.active && !label.equals("Close")) {
            box(g, x0, y0, x1, y1, ROW_DARK, BORDER);
            textColor = TEXT_DIM;
        } else if (primary) {
            box(g, x0, y0, x1, y1, hov ? ACCENT : ACCENT_DIM, ACCENT);
            textColor = hov ? 0xFF0B1414 : ACCENT;
        } else {
            box(g, x0, y0, x1, y1, hov ? ROW_HOVER : ROW, BORDER);
            textColor = TEXT;
        }
        g.text(this.font, label, x0 + (b.getWidth() - this.font.width(label)) / 2, y0 + (b.getHeight() - 8) / 2,
                textColor, false);
    }

    private void drawAbout(GuiGraphicsExtractor g) {
        int x = rowX + 4;
        int y = py + HEADER_H + 14;
        g.text(this.font, "Featherlight " + Featherlight.version(), x, y, TEXT, true);
        y += 16;
        String[] lines = {
                "Small, safe client-side render optimizations.",
                "Entity culling skips far-away entities (size-aware).",
                "Adaptive scaling tightens it when FPS is below target.",
                "Settings are staged: nothing changes until you press Apply.",
        };
        for (String line : lines) {
            g.text(this.font, line, x, y, TEXT_DIM, false);
            y += 12;
        }
        y += 8;
        g.text(this.font, "Status", x, y, TEXT, false);
        y += 14;
        drawStatus(g, x, y, "Entity render hook", Featherlight.entityHookSeen(),
                "running", "waiting for entities");
        y += 14;
        drawStatus(g, x, y, "Video settings hook", Featherlight.videoHookActive(), "running", "inactive");
        y += 18;
        g.text(this.font, "Config: config/featherlight.json", x, y, TEXT_DIM, false);
    }

    private void drawStatus(GuiGraphicsExtractor g, int x, int y, String label, boolean ok, String okText, String badText) {
        g.fill(x, y + 1, x + 6, y + 7, ok ? ACCENT : WARN);
        g.text(this.font, label + ": " + (ok ? okText : badText), x + 12, y, ok ? TEXT_DIM : WARN, false);
    }

    private static void box(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int fill, int border) {
        g.fill(x0, y0, x1, y1, border);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
    }

    // ---- small records -------------------------------------------------------------------

    private record Tab(Page page, Button button) {
    }

    private static final class Row {
        final String label;
        final int y;
        final BooleanSupplier dirty;
        final AbstractWidget control;
        final BooleanSupplier on;
        final ValueSlider slider;
        final Supplier<String> valueText;

        Row(String label, int y, BooleanSupplier dirty, AbstractWidget control,
            BooleanSupplier on, ValueSlider slider, Supplier<String> valueText) {
            this.label = label;
            this.y = y;
            this.dirty = dirty;
            this.control = control;
            this.on = on;
            this.slider = slider;
            this.valueText = valueText;
        }
    }
}
