package org.valkyrienskies.eureka.gui.shiphelm

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.DataSlot
import net.minecraft.world.item.ItemStack
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.EurekaScreens
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity

class ShipHelmScreenMenu(syncId: Int, playerInv: Inventory, private val blockEntity: ShipHelmBlockEntity?) :
    AbstractContainerMenu(EurekaScreens.SHIP_HELM.get(), syncId) {

    constructor(syncId: Int, playerInv: Inventory) : this(syncId, playerInv, null)

    // TODO this isn't synced...
    val aligning = blockEntity?.aligning ?: false
    val assembled = blockEntity?.assembled ?: false

    // Server->client sync of the ship's keep-active flag so the "Keep Active?" checkbox shows the real state.
    // On the server get() reads the live ship setting; on the client (blockEntity == null) it returns the
    // value pushed by set() via the vanilla data-slot sync. broadcastChanges() (per-tick) keeps it current.
    // Client-side mirrors of the server-authoritative stats, populated by the DataSlot set() calls below.
    private var syncedKeepActive = false
    private var syncedBlockLow = 0   // low 16 bits of the assembled block count
    private var syncedBlockHigh = 0  // remaining high bits (a DataSlot transmits only a 16-bit short)
    private var syncedTopSpeed = 0
    private var syncedWaterHold = false
    private var syncedVanilla = false
    init {
        addDataSlot(object : DataSlot() {
            override fun get(): Int = if (blockEntity?.keepActive ?: syncedKeepActive) 1 else 0
            override fun set(value: Int) { syncedKeepActive = value == 1 }
        })
        // Assembled block count, split across two slots (a single DataSlot is a 16-bit short, but counts
        // can reach maxShipBlocks = 50000). The split also stays correct if the sync width is a full int.
        addDataSlot(object : DataSlot() {
            override fun get(): Int = (blockEntity?.assembledBlockCount ?: 0) and 0xFFFF
            override fun set(value: Int) { syncedBlockLow = value }
        })
        addDataSlot(object : DataSlot() {
            override fun get(): Int = (blockEntity?.assembledBlockCount ?: 0) ushr 16
            override fun set(value: Int) { syncedBlockHigh = value }
        })
        // Estimated top speed in m/s (already small -- fits one slot).
        addDataSlot(object : DataSlot() {
            override fun get(): Int = blockEntity?.estimatedTopSpeed ?: 0
            override fun set(value: Int) { syncedTopSpeed = value }
        })
        // Water altitude-hold (global server flag) so the checkbox reflects the real value.
        addDataSlot(object : DataSlot() {
            override fun get(): Int = if (blockEntity?.waterAltitudeHold == true) 1 else 0
            override fun set(value: Int) { syncedWaterHold = value == 1 }
        })
        // Vanilla controls (per-ship) so the checkbox reflects the controlled ship's mode.
        addDataSlot(object : DataSlot() {
            override fun get(): Int = if (blockEntity?.vanillaControls == true) 1 else 0
            override fun set(value: Int) { syncedVanilla = value == 1 }
        })
    }
    val keepActive: Boolean get() = blockEntity?.keepActive ?: syncedKeepActive
    val blockCount: Int get() = blockEntity?.assembledBlockCount
        ?: ((syncedBlockHigh shl 16) or (syncedBlockLow and 0xFFFF))
    val topSpeed: Int get() = blockEntity?.estimatedTopSpeed ?: syncedTopSpeed
    val waterAltitudeHold: Boolean get() = blockEntity?.waterAltitudeHold ?: syncedWaterHold
    val vanillaControls: Boolean get() = blockEntity?.vanillaControls ?: syncedVanilla

    override fun stillValid(player: Player): Boolean = true

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (blockEntity == null) return false

        if (id == 0 && !assembled && !player.level().isClientSide) {
            blockEntity.assemble(player)
            return true
        }

        if (id == 1 && assembled && !player.level().isClientSide) {
            blockEntity.align()
            return true
        }

        if (id == 3 && assembled && !player.level().isClientSide && EurekaConfig.SERVER.allowDisassembly) {
            blockEntity.disassemble()
            return true
        }

        // "Keep Active?" checkbox -> toggle the ship's keep-active flag (same as /vs set-keep-active).
        if (id == 4 && !player.level().isClientSide) {
            blockEntity.setKeepActive(!blockEntity.keepActive)
            return true
        }

        // "Water Altitude Lock" checkbox -> flip the GLOBAL enableWaterAltitudeHold server config.
        if (id == 5 && !player.level().isClientSide) {
            blockEntity.toggleWaterAltitudeHold()
            return true
        }

        // "Vanilla" checkbox -> flip THIS ship's per-ship control mode (and cancel its cruise).
        if (id == 6 && !player.level().isClientSide) {
            blockEntity.toggleVanillaControls()
            return true
        }

        return super.clickMenuButton(player, id)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        // Do nothing
        return ItemStack.EMPTY
    }

    companion object {
        val factory: (syncId: Int, playerInv: Inventory) -> ShipHelmScreenMenu = ::ShipHelmScreenMenu
    }
}
