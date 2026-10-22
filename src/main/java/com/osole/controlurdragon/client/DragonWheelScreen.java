package com.osole.controlurdragon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.osole.controlurdragon.network.DragonActionPayload;
import com.osole.controlurdragon.network.DragonEntry;
import com.osole.controlurdragon.network.DragonListPayload;
import com.osole.controlurdragon.tracking.DragonRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Iron's Spells style wheel. First ring: your dragons. Pick one (click, or release the menu key over it)
 * and the wheel switches to that dragon's orders. Right click or Esc goes back.
 */
public class DragonWheelScreen extends Screen {
    private static final float INNER = 42F;
    private static final float OUTER = 110F;

    private static final int COLOR_BASE = 0xA0141414;
    private static final int COLOR_HOVER = 0xD0C86414;
    private static final int COLOR_DISABLED = 0x80141414;
    private static final int COLOR_LOST = 0xA0701818;

    /** One slice of the wheel. action == null means "back". */
    private record Option(DragonActionPayload.Action action, Component label, boolean enabled) {
    }

    private List<DragonEntry> dragons = new ArrayList<>();
    private long restoreCooldownSeconds;
    private DragonEntry selected;
    private int hovered = -1;

    public DragonWheelScreen(DragonListPayload data) {
        super(Component.translatable("controlurdragon.menu.title"));
        this.setData(data);
    }

    public void setData(DragonListPayload data) {
        this.dragons = new ArrayList<>(data.dragons());
        this.restoreCooldownSeconds = data.restoreCooldownSeconds();
        if (this.selected != null) {
            DragonEntry current = null;
            for (DragonEntry entry : this.dragons) if (entry.id().equals(this.selected.id())) current = entry;
            this.selected = current;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- what the wheel contains -------------------------------------------------------------

    private List<Option> commandOptions(DragonEntry dragon) {
        List<Option> options = new ArrayList<>();
        if (dragon.loaded()) {
            options.add(new Option(DragonActionPayload.Action.SIT, Component.translatable("controlurdragon.action.sit"), dragon.command() != 1));
            options.add(new Option(DragonActionPayload.Action.FOLLOW, Component.translatable("controlurdragon.action.follow"), dragon.command() != 2));
            options.add(new Option(DragonActionPayload.Action.WANDER, Component.translatable("controlurdragon.action.wander"), dragon.command() != 0));
            options.add(new Option(DragonActionPayload.Action.COME, Component.translatable("controlurdragon.action.come"), dragon.distance() >= 0));
            options.add(new Option(DragonActionPayload.Action.RENAME, Component.translatable("controlurdragon.action.rename"), true));
        } else if (dragon.state() == DragonRecord.State.LOST) {
            options.add(new Option(DragonActionPayload.Action.RESTORE, Component.translatable("controlurdragon.action.restore"), this.restoreCooldownSeconds == 0));
        }
        options.add(new Option(null, Component.translatable("controlurdragon.action.back"), true));
        return options;
    }

    private int sliceCount() {
        return this.selected == null ? this.dragons.size() : this.commandOptions(this.selected).size();
    }

    // ---- input --------------------------------------------------------------------------------

    private int sliceAt(double mouseX, double mouseY, int count) {
        if (count == 0) return -1;
        double dx = mouseX - this.width / 2.0;
        double dy = mouseY - this.height / 2.0;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < INNER * 0.6 || distance > OUTER * 1.6) return -1;
        // 0 = straight up, clockwise
        double angle = Math.atan2(dy, dx) + Math.PI / 2;
        if (angle < 0) angle += Math.PI * 2;
        return Mth.clamp((int) (angle / (Math.PI * 2 / count)), 0, count - 1);
    }

    private void choose(int index) {
        if (index < 0) return;
        if (this.selected == null) {
            if (index < this.dragons.size()) this.selected = this.dragons.get(index);
            return;
        }
        List<Option> options = this.commandOptions(this.selected);
        if (index >= options.size()) return;
        Option option = options.get(index);
        if (option.action() == null) {
            this.selected = null;
            return;
        }
        if (!option.enabled()) return;
        if (option.action() == DragonActionPayload.Action.RENAME) {
            this.minecraft.setScreen(new RenameDragonScreen(this, this.selected));
            return;
        }
        PacketDistributor.sendToServer(new DragonActionPayload(this.selected.id(), option.action(), ""));
        this.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.choose(this.sliceAt(mouseX, mouseY, this.sliceCount()));
            return true;
        }
        if (button == 1) {
            if (this.selected != null) this.selected = null;
            else this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // Iron's Spells feel: hold the key, point at a dragon, release to pick it.
        if (ClientEvents.OPEN_MENU.matches(keyCode, scanCode) && this.selected == null && this.hovered >= 0) {
            this.choose(this.hovered);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    // ---- drawing ------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        float cx = this.width / 2F;
        float cy = this.height / 2F;
        int count = this.sliceCount();
        this.hovered = this.sliceAt(mouseX, mouseY, count);

        graphics.drawCenteredString(this.font, this.selected == null ? this.title
                : Component.literal(this.selected.name()).withStyle(ChatFormatting.GOLD), (int) cx, (int) (cy - OUTER - 24), 0xFFFFFF);

        if (count == 0) {
            graphics.drawCenteredString(this.font, Component.translatable("controlurdragon.menu.empty"), (int) cx, (int) cy - 4, 0xAAAAAA);
            return;
        }

        // Slices
        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float slice = (float) (Math.PI * 2 / count);
        float gap = count > 1 ? 0.02F : 0F;
        List<Option> options = this.selected == null ? null : this.commandOptions(this.selected);
        for (int i = 0; i < count; i++) {
            int color;
            if (i == this.hovered) color = COLOR_HOVER;
            else if (options != null) color = options.get(i).enabled() ? COLOR_BASE : COLOR_DISABLED;
            else color = this.dragons.get(i).state() == DragonRecord.State.LOST ? COLOR_LOST : COLOR_BASE;
            float start = i * slice - (float) Math.PI / 2 + gap;
            float end = (i + 1) * slice - (float) Math.PI / 2 - gap;
            drawRingSector(matrix, cx, cy, INNER, OUTER, start, end, color);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // Labels
        float labelRadius = (INNER + OUTER) / 2F;
        for (int i = 0; i < count; i++) {
            float mid = (i + 0.5F) * slice - (float) Math.PI / 2;
            int x = (int) (cx + Mth.cos(mid) * labelRadius);
            int y = (int) (cy + Mth.sin(mid) * labelRadius);
            if (options != null) {
                Option option = options.get(i);
                graphics.drawCenteredString(this.font, option.label(), x, y - 4, option.enabled() ? 0xFFFFFF : 0x777777);
            } else {
                DragonEntry dragon = this.dragons.get(i);
                graphics.drawCenteredString(this.font, shorten(dragon.name(), 14), x, y - 9, typeColor(dragon.type()));
                graphics.drawCenteredString(this.font, stateLine(dragon), x, y + 2, 0xBBBBBB);
            }
        }

        // Centre: details of what the mouse points at
        this.renderCentre(graphics, (int) cx, (int) cy, options);
    }

    private void renderCentre(GuiGraphics graphics, int cx, int cy, List<Option> options) {
        if (options == null) {
            if (this.hovered < 0) {
                graphics.drawCenteredString(this.font, Component.translatable("controlurdragon.menu.pick_dragon"), cx, cy - 4, 0xAAAAAA);
                return;
            }
            DragonEntry dragon = this.dragons.get(this.hovered);
            graphics.drawCenteredString(this.font, Component.translatable("controlurdragon.menu.stage", dragon.stage()), cx, cy - 9, 0xFFFFFF);
            graphics.drawCenteredString(this.font, distanceText(dragon), cx, cy + 2, 0xBBBBBB);
        } else {
            DragonEntry dragon = this.selected;
            graphics.drawCenteredString(this.font, Component.translatable("controlurdragon.state." + dragon.state().name().toLowerCase()), cx, cy - 9, 0xFFFFFF);
            if (dragon.state() == DragonRecord.State.LOST && this.restoreCooldownSeconds > 0)
                graphics.drawCenteredString(this.font, Component.translatable("controlurdragon.menu.cooldown", this.restoreCooldownSeconds / 60 + 1), cx, cy + 2, 0xFF7777);
            else
                graphics.drawCenteredString(this.font, distanceText(dragon), cx, cy + 2, 0xBBBBBB);
        }
    }

    private static Component stateLine(DragonEntry dragon) {
        if (dragon.loaded()) {
            String key = switch (dragon.command()) {
                case 1 -> "controlurdragon.command.sitting";
                case 2 -> "controlurdragon.command.following";
                default -> "controlurdragon.command.wandering";
            };
            return Component.translatable(key);
        }
        return Component.translatable("controlurdragon.state." + dragon.state().name().toLowerCase());
    }

    private static Component distanceText(DragonEntry dragon) {
        return dragon.distance() < 0 ? Component.translatable("controlurdragon.menu.other_dimension")
                : Component.translatable("controlurdragon.menu.distance", dragon.distance());
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static int typeColor(String type) {
        if (type.contains("ice")) return 0x8CD8FF;
        if (type.contains("lightning")) return 0xC89CFF;
        if (type.contains("fire")) return 0xFF9C6E;
        return 0xFFFFFF;
    }

    private static void drawRingSector(Matrix4f matrix, float cx, float cy, float inner, float outer,
                                       float start, float end, int argb) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int steps = Math.max(2, (int) ((end - start) / 0.06F));
        for (int s = 0; s < steps; s++) {
            float a0 = start + (end - start) * s / steps;
            float a1 = start + (end - start) * (s + 1) / steps;
            float ix0 = cx + Mth.cos(a0) * inner, iy0 = cy + Mth.sin(a0) * inner;
            float ox0 = cx + Mth.cos(a0) * outer, oy0 = cy + Mth.sin(a0) * outer;
            float ix1 = cx + Mth.cos(a1) * inner, iy1 = cy + Mth.sin(a1) * inner;
            float ox1 = cx + Mth.cos(a1) * outer, oy1 = cy + Mth.sin(a1) * outer;
            buffer.addVertex(matrix, ix0, iy0, 0).setColor(argb);
            buffer.addVertex(matrix, ox0, oy0, 0).setColor(argb);
            buffer.addVertex(matrix, ox1, oy1, 0).setColor(argb);
            buffer.addVertex(matrix, ix0, iy0, 0).setColor(argb);
            buffer.addVertex(matrix, ox1, oy1, 0).setColor(argb);
            buffer.addVertex(matrix, ix1, iy1, 0).setColor(argb);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }
}
