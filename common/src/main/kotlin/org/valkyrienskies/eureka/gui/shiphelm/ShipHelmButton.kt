package org.valkyrienskies.eureka.gui.shiphelm

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

class ShipHelmButton(x: Int, y: Int, text: Component, private val font: Font, onPress: OnPress) :
    Button(x, y, 156, 23, text, onPress, DEFAULT_NARRATION) {

    var isPressed = false

    init {
        active = true
    }

    // 1.21.11: AbstractButton.renderWidget is now final; custom buttons override renderContents.
    // The RenderSystem.setShader/setShaderTexture/blend calls were removed — GuiGraphics.blit
    // binds the pipeline and texture itself.
    override fun renderContents(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (!isHovered) isPressed = false

        if (this.isPressed || !this.active) {
            guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED, ShipHelmScreen.TEXTURE, x, y,
                BUTTON_P_X.toFloat(), BUTTON_P_Y.toFloat(), width, height, 256, 256
            )
        } else if (this.isHovered) {
            guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED, ShipHelmScreen.TEXTURE, x, y,
                BUTTON_H_X.toFloat(), BUTTON_H_Y.toFloat(), width, height, 256, 256
            )
        }

        // 1.21.11: GuiGraphics.drawString skips text whose color has alpha 0
        // (ARGB.alpha(color) != 0 gate). Must pass a fully-opaque ARGB color.
        val color = 0xFF404040.toInt()
        val formattedCharSequence: FormattedCharSequence = message.visualOrderText
        guiGraphics.drawString(
            font,
            formattedCharSequence,
            ((x + width / 2) - font.width(formattedCharSequence) / 2),
            (y + (height - 8) / 2),
            color,
            false
        )
    }

    override fun onClick(mouseButtonEvent: MouseButtonEvent, bl: Boolean) {
        isPressed = true
        super.onClick(mouseButtonEvent, bl)
    }

    override fun onRelease(mouseButtonEvent: MouseButtonEvent) {
        isPressed = false
    }

    companion object {
        private const val BUTTON_H_X = 0
        private const val BUTTON_H_Y = 166
        private const val BUTTON_P_X = 0
        private const val BUTTON_P_Y = 189
    }
}
