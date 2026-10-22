package com.osole.controlurdragon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.osole.controlurdragon.behavior.DragonSettings;
import com.osole.controlurdragon.behavior.DragonSettings.Setting;
import com.osole.controlurdragon.network.DragonActionPayload;
import com.osole.controlurdragon.network.DragonActionPayload.Action;
import com.osole.controlurdragon.network.DragonEntry;
import com.osole.controlurdragon.network.DragonListPayload;
import com.osole.controlurdragon.tracking.DragonRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DragonWheelScreen extends Screen {
    private static final float INNER = 24F;
    private static final float OUTER = 70F;
    private static final float LABEL_WIDTH = 56F;
    private static final float LABEL_SCALE = 0.8F;

    private static final int COLOR_BASE = 0x90141414;
    private static final int COLOR_HOVER = 0xC8C86414;
    private static final int COLOR_DISABLED = 0x60141414;
    private static final int COLOR_LOST = 0x90701818;
    private static final int COLOR_DANGER = 0xA0601010;
    private static final int COLOR_CATEGORY = 0x90203048;

    private enum Category {
        MOVEMENT, COMBAT, BEHAVIOR, OTHER
    }

    private enum Kind {
        ACTION, CATEGORY, SETTING, BACK
    }

    private record Option(Kind kind, Action action, int value, Category category, Setting setting, Component label,
                          boolean enabled, boolean active, boolean danger) {
        static Option action(Action action, int value, Component label, boolean enabled) {
            return new Option(Kind.ACTION, action, value, null, null, label, enabled, false, false);
        }

        static Option category(Category category) {
            return new Option(Kind.CATEGORY, null, 0, category, null,
                    Component.translatable("controlurdragon.category." + category.name().toLowerCase(Locale.ROOT)), true, false, false);
        }

        static Option setting(Setting setting, int value, Component label) {
            return new Option(Kind.SETTING, Action.SETTING, value, null, setting, label, true, false, false);
        }

        static Option back() {
            return new Option(Kind.BACK, null, 0, null, null, Component.translatable("controlurdragon.action.back"), true, false, false);
        }

        Option asActive() {
            return new Option(this.kind, this.action, this.value, this.category, this.setting, this.label, false, true, this.danger);
        }

        Option asDanger() {
            return new Option(this.kind, this.action, this.value, this.category, this.setting, this.label, this.enabled, this.active, true);
        }
    }

    private final LivingEntity target;
    private List<DragonEntry> dragons = new ArrayList<>();
    private long restoreCooldownSeconds;
    private long bringCooldownSeconds;
    private boolean debug;
    private DragonEntry selected;
    private Category category;
    private Setting settingMenu;
    private Action confirming;
    private int hovered = -1;

    public DragonWheelScreen(DragonListPayload data, LivingEntity target) {
        super(Component.translatable("controlurdragon.menu.title"));
        this.target = target;
        this.setData(data);
    }

    public void setData(DragonListPayload data) {
        this.dragons = new ArrayList<>(data.dragons());
        this.restoreCooldownSeconds = data.restoreCooldownSeconds();
        this.bringCooldownSeconds = data.bringCooldownSeconds();
        this.debug = data.debug();
        if (this.selected != null) {
            DragonEntry current = null;
            for (DragonEntry entry : this.dragons) if (entry.id().equals(this.selected.id())) current = entry;
            this.selected = current;
            if (current == null) this.category = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private Component withCooldown(String key, long seconds) {
        Component label = Component.translatable(key);
        return seconds > 0 ? Component.translatable("controlurdragon.menu.with_cooldown", label, seconds / 60 + 1) : label;
    }

    private Component confirmable(Action action, Component label) {
        return this.confirming == action ? Component.translatable("controlurdragon.menu.confirm") : label;
    }

    private List<Option> options() {
        if (this.selected == null) return null;
        DragonEntry dragon = this.selected;
        List<Option> list = new ArrayList<>();
        if (!dragon.loaded()) {
            if (dragon.state() == DragonRecord.State.LOST) {
                list.add(Option.action(Action.RESTORE, 0, confirmable(Action.RESTORE,
                        this.withCooldown("controlurdragon.action.restore", this.restoreCooldownSeconds)), this.restoreCooldownSeconds == 0));
            } else {
                list.add(Option.action(Action.BRING, 0, confirmable(Action.BRING,
                                this.withCooldown("controlurdragon.action.bring", this.bringCooldownSeconds)),
                        this.bringCooldownSeconds == 0 && dragon.state() != DragonRecord.State.STORED));
            }
            list.add(Option.back());
            return list;
        }
        if (this.category == null) {
            for (Category c : Category.values()) list.add(Option.category(c));
            list.add(Option.back());
            return list;
        }
        boolean hasTarget = this.target != null && this.target.isAlive();
        String targetName = hasTarget ? shorten(this.target.getName().getString(), 12) : "";
        switch (this.category) {
            case MOVEMENT -> {
                list.add(Option.action(Action.SIT, 0, Component.translatable("controlurdragon.action.sit"), dragon.command() != 1));
                list.add(Option.action(Action.FOLLOW, 0, Component.translatable("controlurdragon.action.follow"), dragon.command() != 2));
                list.add(Option.action(Action.WANDER, 0, Component.translatable("controlurdragon.action.wander"),
                        dragon.command() != 0 || dragon.guarding()));
                list.add(Option.action(Action.BRING, 0, confirmable(Action.BRING,
                        this.withCooldown("controlurdragon.action.bring", this.bringCooldownSeconds)), this.bringCooldownSeconds == 0));
            }
            case COMBAT -> {
                list.add(Option.action(Action.ATTACK, hasTarget ? this.target.getId() : -1, hasTarget
                        ? Component.translatable("controlurdragon.action.attack", targetName)
                        : Component.translatable("controlurdragon.action.attack.none"), hasTarget && dragon.distance() >= 0));
                list.add(Option.action(Action.STOP, 0, Component.translatable("controlurdragon.action.stop"), true));
                list.add(Option.action(Action.GUARD, 0, Component.translatable("controlurdragon.action.guard"), dragon.distance() >= 0));
            }
            case BEHAVIOR -> {
                if (this.settingMenu != null) {
                    Setting setting = this.settingMenu;
                    int current = dragon.setting(setting);
                    for (int i = 0; i < DragonSettings.optionCount(setting); i++) {
                        Option option = Option.setting(setting, i, Component.translatable(DragonSettings.valueKey(setting, i)));
                        list.add(i == current ? option.asActive() : option);
                    }
                } else {
                    for (Setting setting : Setting.values()) {
                        int current = dragon.setting(setting);
                        boolean toggle = DragonSettings.optionCount(setting) == 2;
                        list.add(Option.setting(setting, toggle ? 1 - current : -1,
                                Component.translatable(DragonSettings.nameKey(setting))));
                    }
                }
            }
            case OTHER -> {
                if (!dragon.carrying().isEmpty()) {
                    list.add(Option.action(Action.DROP, 0, Component.translatable("controlurdragon.action.drop",
                            shorten(dragon.carrying(), 12)), true));
                } else {
                    list.add(Option.action(Action.CARRY, hasTarget ? this.target.getId() : -1, hasTarget
                            ? Component.translatable("controlurdragon.action.carry", targetName)
                            : Component.translatable("controlurdragon.action.carry.none"), dragon.distance() >= 0));
                }
                list.add(Option.action(Action.RENAME, 0, Component.translatable("controlurdragon.action.rename"), true));
                if (this.debug) {
                    list.add(Option.action(Action.DELETE, 0, confirmable(Action.DELETE,
                            Component.translatable("controlurdragon.action.delete")), true).asDanger());
                }
            }
        }
        list.add(Option.back());
        return list;
    }

    private int sliceCount() {
        List<Option> options = this.options();
        return options == null ? this.dragons.size() : options.size();
    }

    private int sliceAt(double mouseX, double mouseY, int count) {
        if (count == 0) return -1;
        double dx = mouseX - this.width / 2.0;
        double dy = mouseY - this.height / 2.0;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < INNER * 0.5 || distance > OUTER * 1.8) return -1;
        double angle = Math.atan2(dy, dx) + Math.PI / 2;
        if (angle < 0) angle += Math.PI * 2;
        return Mth.clamp((int) (angle / (Math.PI * 2 / count)), 0, count - 1);
    }

    private void goBack() {
        this.confirming = null;
        if (this.settingMenu != null) this.settingMenu = null;
        else if (this.category != null) this.category = null;
        else if (this.selected != null) this.selected = null;
        else this.onClose();
    }

    private void choose(int index) {
        if (index < 0) return;
        List<Option> options = this.options();
        if (options == null) {
            if (index < this.dragons.size()) {
                this.selected = this.dragons.get(index);
                this.category = null;
                this.settingMenu = null;
                this.confirming = null;
            }
            return;
        }
        if (index >= options.size()) return;
        Option option = options.get(index);
        switch (option.kind()) {
            case BACK -> this.goBack();
            case CATEGORY -> {
                this.category = option.category();
                this.settingMenu = null;
                this.confirming = null;
            }
            case SETTING -> {
                if (!option.enabled()) return;
                if (option.value() < 0) {
                    this.settingMenu = option.setting();
                    return;
                }
                PacketDistributor.sendToServer(new DragonActionPayload(this.selected.id(), Action.SETTING,
                        option.setting().name(), option.value()));
                this.settingMenu = null;
            }
            case ACTION -> {
                if (!option.enabled()) return;
                Action action = option.action();
                if (action == Action.RENAME) {
                    this.minecraft.setScreen(new RenameDragonScreen(this, this.selected));
                    return;
                }
                if ((action == Action.BRING || action == Action.RESTORE || action == Action.DELETE) && this.confirming != action) {
                    this.confirming = action;
                    return;
                }
                PacketDistributor.sendToServer(new DragonActionPayload(this.selected.id(), action, "", option.value()));
                this.onClose();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.choose(this.sliceAt(mouseX, mouseY, this.sliceCount()));
            return true;
        }
        if (button == 1) {
            this.goBack();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.OPEN_MENU.matches(keyCode, scanCode) && this.selected == null && this.hovered >= 0) {
            this.choose(this.hovered);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        float cx = this.width / 2F;
        float cy = this.height / 2F;
        List<Option> options = this.options();
        int count = options == null ? this.dragons.size() : options.size();
        this.hovered = this.sliceAt(mouseX, mouseY, count);

        Component heading = this.selected == null ? this.title : Component.literal(this.selected.name()).withStyle(ChatFormatting.GOLD);
        if (this.category != null) {
            heading = Component.empty().append(heading).append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("controlurdragon.category." + this.category.name().toLowerCase(Locale.ROOT)));
            if (this.settingMenu != null) {
                heading = Component.empty().append(heading).append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable(DragonSettings.nameKey(this.settingMenu)));
            }
        }
        graphics.drawCenteredString(this.font, heading, (int) cx, (int) (cy - OUTER - 14), 0xFFFFFF);

        if (count == 0) {
            graphics.drawCenteredString(this.font, Component.translatable("controlurdragon.menu.empty"), (int) cx, (int) cy - 4, 0xAAAAAA);
            return;
        }

        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float slice = (float) (Math.PI * 2 / count);
        float gap = count > 1 ? 0.025F : 0F;
        for (int i = 0; i < count; i++) {
            int color;
            if (i == this.hovered) color = COLOR_HOVER;
            else if (options == null) color = this.dragons.get(i).state() == DragonRecord.State.LOST ? COLOR_LOST : COLOR_BASE;
            else if (options.get(i).danger()) color = COLOR_DANGER;
            else if (options.get(i).kind() == Kind.CATEGORY) color = COLOR_CATEGORY;
            else color = options.get(i).enabled() || options.get(i).active() ? COLOR_BASE : COLOR_DISABLED;
            float start = i * slice - (float) Math.PI / 2 + gap;
            float end = (i + 1) * slice - (float) Math.PI / 2 - gap;
            drawRingSector(matrix, cx, cy, INNER, OUTER, start, end, color);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        float labelRadius = (INNER + OUTER) / 2F;
        for (int i = 0; i < count; i++) {
            float mid = (i + 0.5F) * slice - (float) Math.PI / 2;
            float x = cx + Mth.cos(mid) * labelRadius;
            float y = cy + Mth.sin(mid) * labelRadius;
            if (options != null) {
                Option option = options.get(i);
                int color = option.active() ? 0x77FF77 : option.enabled() ? 0xFFFFFF : 0x777777;
                if (option.kind() == Kind.SETTING && this.settingMenu == null) {
                    Setting setting = option.setting();
                    this.drawLabel(graphics, option.label(), x, y - 7, color);
                    this.drawLabel(graphics, Component.translatable(DragonSettings.valueKey(setting, this.selected.setting(setting))),
                            x, y + 2, 0xE0B060);
                } else {
                    this.drawLabel(graphics, option.label(), x, y - 3, color);
                }
            } else {
                DragonEntry dragon = this.dragons.get(i);
                this.drawLabel(graphics, Component.literal(shorten(dragon.name(), 14)), x, y - 7, typeColor(dragon.type()));
                this.drawLabel(graphics, stateLine(dragon), x, y + 2, 0xBBBBBB);
            }
        }

        this.renderInfo(graphics, (int) cx, (int) (cy + OUTER + 6), options);
    }

    private void drawLabel(GuiGraphics graphics, Component text, float x, float y, int color) {
        int width = this.font.width(text);
        float scale = width == 0 ? LABEL_SCALE : Math.min(LABEL_SCALE, LABEL_WIDTH / width);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawCenteredString(this.font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    private void renderInfo(GuiGraphics graphics, int cx, int y, List<Option> options) {
        DragonEntry dragon = options == null ? (this.hovered >= 0 ? this.dragons.get(this.hovered) : null) : this.selected;
        if (dragon == null) {
            graphics.drawCenteredString(this.font, Component.translatable("controlurdragon.menu.pick_dragon"), cx, y, 0xAAAAAA);
            return;
        }
        Component first = Component.translatable("controlurdragon.menu.stage", dragon.stage()).append(" · ").append(distanceText(dragon));
        graphics.drawCenteredString(this.font, first, cx, y, 0xDDDDDD);
        if (dragon.loaded()) {
            Component second = stateLine(dragon).copy().append(" · ").append(Component.translatable(
                    DragonSettings.valueKey(Setting.STANCE, dragon.setting(Setting.STANCE))));
            graphics.drawCenteredString(this.font, second, cx, y + 10, 0xAAAAAA);
        }
    }

    private static Component stateLine(DragonEntry dragon) {
        if (dragon.loaded()) {
            if (!dragon.carrying().isEmpty()) return Component.translatable("controlurdragon.command.carrying");
            if (dragon.guarding()) return Component.translatable("controlurdragon.command.guarding");
            String key = switch (dragon.command()) {
                case 1 -> "controlurdragon.command.sitting";
                case 2 -> "controlurdragon.command.following";
                default -> "controlurdragon.command.wandering";
            };
            return Component.translatable(key);
        }
        return Component.translatable("controlurdragon.state." + dragon.state().name().toLowerCase(Locale.ROOT));
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
