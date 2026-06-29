package org.valkyrienskies.eureka.gui.shiphelm

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.phys.BlockHitResult
import org.lwjgl.glfw.GLFW
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.EurekaConfigLoader
import org.valkyrienskies.eureka.EurekaMod
import org.valkyrienskies.mod.common.getShipManagingPos

class ShipHelmScreen(handler: ShipHelmScreenMenu, playerInventory: Inventory, text: Component) :
    AbstractContainerScreen<ShipHelmScreenMenu>(handler, playerInventory, text) {

    private lateinit var assembleButton: ShipHelmButton
    private lateinit var alignButton: ShipHelmButton
    private lateinit var disassembleButton: ShipHelmButton
    private lateinit var keepActiveCheckbox: ShipHelmCheckbox
    private lateinit var waterLockCheckbox: ShipHelmCheckbox
    private lateinit var vanillaCheckbox: ShipHelmCheckbox
    private lateinit var displayHudCheckbox: ShipHelmCheckbox
    private lateinit var displaySpeedCheckbox: ShipHelmCheckbox
    private lateinit var displayAltitudeCheckbox: ShipHelmCheckbox
    private lateinit var displayHeadingCheckbox: ShipHelmCheckbox

    // Rename: a small button in the title strip toggles an inline EditBox over the name. Typing is routed by
    // the base Screen to the focused EditBox; the same button commits (it becomes "Save") by firing the VS
    // rename command. Server-side ships are renamed via "/vs rename <slug> <name>" (op level 2).
    private lateinit var renameButton: ShipHelmIconButton
    private lateinit var renameBox: EditBox
    private var renaming = false

    private var pos = (Minecraft.getInstance().hitResult as? BlockHitResult)?.blockPos
    private var ship: Ship? = pos?.let { Minecraft.getInstance().level?.getShipManagingPos(it) }

    init {
        titleLabelX = 6
        titleLabelY = 6
        // The panel texture is rendered PANEL_HEIGHT tall (extended 10px below the default 166) so the
        // button stack can sit lower without crowding the "Vanilla Controls" checkbox row; keep imageHeight
        // in sync so the panel stays vertically centered and renderBg blits the full extended art.
        imageHeight = PANEL_HEIGHT
    }

    override fun init() {
        super.init()
        val x = (width - imageWidth) / 2
        val y = (height - imageHeight) / 2

        assembleButton = addRenderableWidget(
            ShipHelmButton(x + BUTTON_1_X, y + BUTTON_1_Y, ASSEMBLE_TEXT, font) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 0)
            }
        )

        alignButton = addRenderableWidget(
            ShipHelmButton(x + BUTTON_2_X, y + BUTTON_2_Y, ALIGN_TEXT, font) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 1)
            }
        )

        disassembleButton = addRenderableWidget(
            ShipHelmButton(x + BUTTON_3_X, y + BUTTON_3_Y, DISSEMBLE_TEXT, font) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 3)
            }
        )

        // Right-hand checkbox column. "Display HUD" is the master for the top-center readouts; Speed/Altitude/
        // Heading are indented sub-toggles that grey out (and stop responding) while the master is off. Each
        // HUD toggle is a pure client config (persisted immediately). "Water Lock" flips the global server
        // water-altitude-hold (menu button 5) and "Keep Active" the per-ship keep-active flag (menu button 4).
        displayHudCheckbox = addRenderableWidget(
            ShipHelmCheckbox(
                x + CHECKBOX_X, y + checkboxRowY(0), checkboxWidth(DISPLAY_HUD_TEXT),
                DISPLAY_HUD_TEXT, font, { EurekaConfig.CLIENT.displayHud }
            ) {
                EurekaConfig.CLIENT.displayHud = !EurekaConfig.CLIENT.displayHud
                EurekaConfigLoader.save()
            }
        )
        displaySpeedCheckbox = addRenderableWidget(
            ShipHelmCheckbox(
                x + CHECKBOX_SUB_X, y + checkboxRowY(1), checkboxWidth(SPEED_TEXT),
                SPEED_TEXT, font, { EurekaConfig.CLIENT.displaySpeed }
            ) {
                EurekaConfig.CLIENT.displaySpeed = !EurekaConfig.CLIENT.displaySpeed
                EurekaConfigLoader.save()
            }
        )
        displayAltitudeCheckbox = addRenderableWidget(
            ShipHelmCheckbox(
                x + CHECKBOX_SUB_X, y + checkboxRowY(2), checkboxWidth(ALTITUDE_TEXT),
                ALTITUDE_TEXT, font, { EurekaConfig.CLIENT.displayAltitude }
            ) {
                EurekaConfig.CLIENT.displayAltitude = !EurekaConfig.CLIENT.displayAltitude
                EurekaConfigLoader.save()
            }
        )
        displayHeadingCheckbox = addRenderableWidget(
            ShipHelmCheckbox(
                x + CHECKBOX_SUB_X, y + checkboxRowY(3), checkboxWidth(HEADING_TEXT),
                HEADING_TEXT, font, { EurekaConfig.CLIENT.displayHeading }
            ) {
                EurekaConfig.CLIENT.displayHeading = !EurekaConfig.CLIENT.displayHeading
                EurekaConfigLoader.save()
            }
        )
        waterLockCheckbox = addRenderableWidget(
            ShipHelmCheckbox(
                x + CHECKBOX_X, y + checkboxRowY(4), checkboxWidth(WATER_LOCK_TEXT),
                WATER_LOCK_TEXT, font, { menu.waterAltitudeHold }
            ) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 5)
            }
        )
        keepActiveCheckbox = addRenderableWidget(
            ShipHelmCheckbox(
                x + CHECKBOX_X, y + checkboxRowY(5), checkboxWidth(KEEP_ACTIVE_TEXT),
                KEEP_ACTIVE_TEXT, font, { menu.keepActive }
            ) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 4)
            }
        )
        // "Vanilla Controls" -> flips THIS ship's per-ship control mode (menu button 6). Per-ship, so it greys
        // out when not looking at a ship (gated like keepActive in updateButtons). Row 6 -- the panel was
        // extended 10px and the buttons pushed down to give this row clear air (see PANEL_HEIGHT / BUTTON_*_Y).
        vanillaCheckbox = addRenderableWidget(
            ShipHelmCheckbox(
                x + CHECKBOX_X, y + checkboxRowY(6), checkboxWidth(VANILLA_TEXT),
                VANILLA_TEXT, font, { menu.vanillaControls }
            ) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 6)
            }
        )

        // Inline rename: the edit box sits over the name; the button toggles it open and commits.
        renameBox = addRenderableWidget(
            EditBox(font, x + RENAME_BOX_X, y + RENAME_BOX_Y, RENAME_BOX_W, RENAME_BOX_H, RENAME_TEXT)
        ).also {
            it.setMaxLength(48)
            it.visible = false
        }
        renameButton = addRenderableWidget(
            ShipHelmIconButton(x + RENAME_BTN_X, y + RENAME_BTN_Y, RENAME_BTN_W, RENAME_BTN_H, RENAME_TEXT, font) {
                toggleRename()
            }
        )

        disassembleButton.active = EurekaConfig.SERVER.allowDisassembly
        updateButtons()
    }

    private fun toggleRename() {
        val s = ship ?: return
        if (!renaming) {
            // Seed with the human-readable form (hyphens shown as spaces) so the user edits a clean name.
            renameBox.value = (pendingNames[s.id] ?: s.slug)?.replace('-', ' ') ?: ""
            renameBox.visible = true
            this.focused = renameBox
            renameBox.isFocused = true
            renaming = true
            renameButton.message = SAVE_TEXT
        } else {
            // Store the slug with hyphens instead of spaces so it stays command-friendly (slugs/commands break on
            // spaces); the helm renders the hyphens back as spaces for a clean look.
            val slugName = renameBox.value.trim().replace("\"", "").replace(' ', '-')
            val shown = pendingNames[s.id] ?: s.slug
            if (slugName.isNotEmpty() && slugName != shown) {
                // Target the ship by its STABLE id (@v[id=...]), NOT the slug: the slug changes on rename and the
                // client's copy isn't re-synced until reload, so a slug-based command would fail ("ship not found")
                // on a second rename. Cache the new name (keyed by id) so the helm title shows it immediately and
                // across reopens; the rename also persists server-side. (vs-core never pushes a changed slug back
                // to the client, so /vs command tab-complete only lists the new name after a world reload.)
                minecraft?.player?.connection?.sendCommand("vs rename @v[id=${s.id}] \"$slugName\"")
                pendingNames[s.id] = slugName
            }
            cancelRename()
        }
    }

    private fun cancelRename() {
        renaming = false
        renameBox.visible = false
        renameBox.isFocused = false
        if (this.focused === renameBox) this.focused = null
        renameButton.message = RENAME_TEXT
    }

    private fun updateButtons() {
        val newPos = (Minecraft.getInstance().hitResult as? BlockHitResult)?.blockPos
        if (newPos != null){
            pos = newPos
        }
        val newShip = pos?.let { Minecraft.getInstance().level?.getShipManagingPos(it) }
        if (newShip != null){
            ship = newShip
        }

        // Once the authoritative client slug catches up to our optimistic name (after a reload re-syncs it),
        // drop the cached entry so it can't linger and mask a later external rename.
        ship?.let { sh -> if (pendingNames[sh.id] == sh.slug) pendingNames.remove(sh.id) }

        val isLookingAtShip = ship != null

        assembleButton.active = !isLookingAtShip
        disassembleButton.active = EurekaConfig.SERVER.allowDisassembly && isLookingAtShip
        alignButton.active = disassembleButton.active
        // Keep-active is per-ship, so only when looking at one. Water lock is a global setting -> always usable.
        keepActiveCheckbox.active = isLookingAtShip
        waterLockCheckbox.active = true
        // Vanilla controls is per-ship, so only usable when looking at a ship.
        vanillaCheckbox.active = isLookingAtShip

        // HUD sub-toggles grey out (and ignore clicks via Button.active) while the master is off.
        val hudOn = EurekaConfig.CLIENT.displayHud
        displaySpeedCheckbox.active = hudOn
        displayAltitudeCheckbox.active = hudOn
        displayHeadingCheckbox.active = hudOn

        // Rename only applies to an assembled ship.
        renameButton.visible = isLookingAtShip
        renameButton.active = isLookingAtShip
        if (!isLookingAtShip && renaming) cancelRename()
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTicks: Float, mouseX: Int, mouseY: Int) {
        updateButtons()

        val x = (width - imageWidth) / 2
        val y = (height - imageHeight) / 2
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0f, 0f, imageWidth, imageHeight, 256, 256)
    }

    override fun renderLabels(guiGraphics: GuiGraphics, i: Int, j: Int) {
        if (this.menu.aligning) {
            alignButton.message = ALIGNING_TEXT
            alignButton.active = false
        } else {
            alignButton.message = ALIGN_TEXT
            alignButton.active = true
        }

        val s = ship ?: return

        // Name in the title strip (hidden while the rename box is open, since the box overlays it). Prefer the
        // optimistic cached name so a just-applied rename shows without waiting for the client slug re-sync.
        if (!renaming) {
            // Display hyphens as spaces so a command-friendly slug ("my-ship") reads cleanly ("my ship").
            (pendingNames[s.id] ?: s.slug)?.let { guiGraphics.drawString(font, it.replace('-', ' '), titleLabelX, titleLabelY, INFO_TEXT, false) }
        }

        // The three grey info boxes baked into the texture (top-left). All three share ONE font scale -- the
        // smallest needed so the WIDEST line (usually Top Speed) fits -- so the readouts are uniform size.
        val topLine = "Top Speed: ${menu.topSpeed}m/s~"
        val blockLine = "Blocks: " + String.format("%,d", menu.blockCount)
        val dimLine = dimensionsText(s)
        val widest = maxOf(font.width(topLine), font.width(blockLine), font.width(dimLine)).toFloat()
        val infoScale = if (widest <= 0f) INFO_MAX_SCALE else minOf(INFO_MAX_SCALE, (INFO_W - 2) / widest)
        drawBoxText(guiGraphics, topLine, BOX1_Y, infoScale)
        drawBoxText(guiGraphics, blockLine, BOX2_Y, infoScale)
        drawBoxText(guiGraphics, dimLine, BOX3_Y, infoScale)
    }

    private fun dimensionsText(s: Ship): String {
        val a = s.shipAABB ?: return "H:- W:- L:-"
        val h = a.maxY() - a.minY() + 1
        // W/L by MAGNITUDE, not a fixed axis: width = the shorter horizontal extent, length = the longer.
        // A fixed X/Z mapping reads right for one hull but flips for a hull built along the other axis (there
        // is no ship-local axis that is always "width"); magnitude matches the boat-shaped intuition
        // (length >= width) regardless of how the ship was oriented when assembled.
        val xExt = a.maxX() - a.minX() + 1
        val zExt = a.maxZ() - a.minZ() + 1
        val w = minOf(xExt, zExt)
        val l = maxOf(xExt, zExt)
        return "H:$h W:$w L:$l"
    }

    // Draw dark text inside a grey info box (x = INFO_X..INFO_X+INFO_W) at the given shared scale.
    private fun drawBoxText(guiGraphics: GuiGraphics, text: String, topY: Int, scale: Float) {
        val pose = guiGraphics.pose()
        pose.pushMatrix()
        pose.scale(scale, scale)
        val tx = (INFO_X + 1) / scale
        val ty = (topY + (INFO_BOX_H - font.lineHeight * scale) / 2f) / scale
        guiGraphics.drawString(font, text, Math.round(tx), Math.round(ty), INFO_TEXT, false)
        pose.popMatrix()
    }

    // While renaming, keep keystrokes away from AbstractContainerScreen's inventory-key handler -- otherwise the
    // inventory key (default 'e') closes the helm mid-word instead of typing. Characters still arrive via the base
    // charTyped routing to the focused edit box; here we just swallow the key presses (and add Esc/Enter).
    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (renaming) {
            when (keyEvent.key()) {
                GLFW.GLFW_KEY_ESCAPE -> { cancelRename(); return true }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { toggleRename(); return true }
            }
            renameBox.keyPressed(keyEvent) // edit keys (backspace, arrows, etc.)
            return true
        }
        return super.keyPressed(keyEvent)
    }

    // mojank doesn't check mouse release for their widgets for some reason
    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        isDragging = false
        if (getChildAt(mouseButtonEvent.x(), mouseButtonEvent.y())
                .filter { it.mouseReleased(mouseButtonEvent) }.isPresent
        ) {
            return true
        }

        return super.mouseReleased(mouseButtonEvent)
    }

    companion object { // TEXTURE DATA
        internal val TEXTURE = Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, "textures/gui/ship_helm.png")

        // Optimistic rename names keyed by ship id. vs-core sends a ship's slug to the client only ONCE (at
        // load), so after a rename the client's Ship.slug stays stale until the world reloads. A per-screen
        // field would reset every time the helm is reopened and fall back to that stale slug ("keeps the old
        // name"), so the names live here, surviving reopens for the session, and are dropped once the real
        // client slug catches up (reconciled in updateButtons).
        private val pendingNames = HashMap<Long, String>()

        // Right-hand checkbox column (the open area beside the texture's left info boxes). Seven rows fit between
        // the title separator (y15) and the first button (y86): row 6 (Vanilla Controls) box at 18+8*6=66..75
        // clears the button at 86 with 11px to spare. The sub-toggles are indented under the master.
        private const val CHECKBOX_X = 74
        private const val CHECKBOX_SUB_X = 88 // indented further in so the HUD sub-toggles clearly nest under it
        private const val CHECKBOX_Y = 18 // first row, just below the title separator
        private const val CHECKBOX_DY = 8 // row spacing tightened from 9 to fit a 7th row without a texture edit
        private fun checkboxRowY(row: Int) = CHECKBOX_Y + CHECKBOX_DY * row
        private fun checkboxWidth(text: Component) =
            ShipHelmCheckbox.BOX + ShipHelmCheckbox.GAP + Minecraft.getInstance().font.width(text)

        // The three grey readout boxes baked into the texture (x ~5..68, three rows down the left side).
        private const val INFO_X = 7
        private const val INFO_W = 58
        private const val INFO_BOX_H = 14
        private const val INFO_MAX_SCALE = 0.8f
        private const val INFO_TEXT = 0xFF383838.toInt()
        private const val BOX1_Y = 21
        private const val BOX2_Y = 37
        private const val BOX3_Y = 53

        // Rename control in the title strip (above the separator line at y15). Small + small label.
        private const val RENAME_BTN_W = 32
        private const val RENAME_BTN_H = 9
        private const val RENAME_BTN_X = 176 - RENAME_BTN_W - 6 // right-aligned in the panel
        private const val RENAME_BTN_Y = 4 // nudged down a touch to sit level with the name
        private const val RENAME_BOX_X = 6
        private const val RENAME_BOX_Y = 2
        private const val RENAME_BOX_W = 118
        private const val RENAME_BOX_H = 12

        // The panel art (ship_helm.png) is rendered PANEL_HEIGHT tall -- it was extended 10px (166->176): the
        // baked button frames AND the bottom border were shifted down 10px in the texture, and the hover/pressed
        // button sprites relocated to match (ShipHelmButton.BUTTON_H_Y/P_Y). The three buttons move down the same
        // 10px here (76/106/136 -> 86/116/146) so they align with their shifted baked frames; this gives the
        // relabeled "Vanilla Controls" checkbox row (box y66..75) clear air above the first button. Bottom button
        // ends at 146+23=169, preserving the original 7px margin inside the now-176px panel.
        private const val PANEL_HEIGHT = 176
        private const val BUTTON_1_X = 10
        private const val BUTTON_1_Y = 86
        private const val BUTTON_2_X = 10
        private const val BUTTON_2_Y = 116
        private const val BUTTON_3_X = 10
        private const val BUTTON_3_Y = 146

        private val KEEP_ACTIVE_TEXT = Component.translatable("gui.vs_eureka.keep_active")
        private val WATER_LOCK_TEXT = Component.translatable("gui.vs_eureka.water_lock")
        private val VANILLA_TEXT = Component.translatable("gui.vs_eureka.vanilla_controls")
        private val DISPLAY_HUD_TEXT = Component.translatable("gui.vs_eureka.display_hud")
        private val SPEED_TEXT = Component.translatable("gui.vs_eureka.display_speed")
        private val ALTITUDE_TEXT = Component.translatable("gui.vs_eureka.display_altitude")
        private val HEADING_TEXT = Component.translatable("gui.vs_eureka.display_heading")

        private val RENAME_TEXT = Component.translatable("gui.vs_eureka.rename")
        private val SAVE_TEXT = Component.translatable("gui.vs_eureka.rename_save")

        private val ASSEMBLE_TEXT = Component.translatable("gui.vs_eureka.assemble")
        private val DISSEMBLE_TEXT = Component.translatable("gui.vs_eureka.disassemble")
        private val ALIGN_TEXT = Component.translatable("gui.vs_eureka.align")
        private val ALIGNING_TEXT = Component.translatable("gui.vs_eureka.aligning")
    }
}
