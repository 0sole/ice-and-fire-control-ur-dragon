package com.osole.controlurdragon.client;

import com.osole.controlurdragon.network.DragonActionPayload;
import com.osole.controlurdragon.network.DragonEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Small text box to give a dragon a name. An empty name removes the name.
 */
public class RenameDragonScreen extends Screen {
    private final Screen parent;
    private final DragonEntry dragon;
    private EditBox nameBox;

    public RenameDragonScreen(Screen parent, DragonEntry dragon) {
        super(Component.translatable("controlurdragon.rename.title"));
        this.parent = parent;
        this.dragon = dragon;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.nameBox = new EditBox(this.font, cx - 100, cy - 10, 200, 20, Component.translatable("controlurdragon.rename.title"));
        this.nameBox.setMaxLength(DragonActionPayload.MAX_NAME_LENGTH);
        this.nameBox.setValue(this.dragon.name());
        this.addRenderableWidget(this.nameBox);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.confirm())
                .bounds(cx - 100, cy + 18, 98, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(cx + 2, cy + 18, 98, 20).build());
        this.setInitialFocus(this.nameBox);
    }

    private void confirm() {
        PacketDistributor.sendToServer(new DragonActionPayload(this.dragon.id(), DragonActionPayload.Action.RENAME, this.nameBox.getValue()));
        this.minecraft.setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("controlurdragon.rename.hint"), this.width / 2, this.height / 2 + 44, 0x999999);
    }
}
