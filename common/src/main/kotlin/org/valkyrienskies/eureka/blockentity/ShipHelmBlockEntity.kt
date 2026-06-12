package org.valkyrienskies.eureka.blockentity

import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction.Axis
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.eureka.EurekaBlockEntities
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.EurekaMod
import org.valkyrienskies.eureka.block.AnchorBlock
import org.valkyrienskies.eureka.block.BalloonBlock
import org.valkyrienskies.eureka.block.FloaterBlock
import org.valkyrienskies.eureka.block.ShipHelmBlock
import org.valkyrienskies.eureka.gui.shiphelm.ShipHelmScreenMenu
import org.valkyrienskies.eureka.ship.EurekaShipControl
import org.valkyrienskies.eureka.util.ShipAssembler
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toDoubles
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.util.logger

val ASSEMBLE_BLACKLIST: TagKey<Block> =
    TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, "assemble_blacklist"))

private const val ORPHAN_SCAN_INTERVAL_TICKS = 20

class ShipHelmBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(EurekaBlockEntities.SHIP_HELM.get(), pos, state), MenuProvider {

    private val ship: LoadedServerShip? get() = (level as ServerLevel).getLoadedShipManagingPos(this.blockPos)
    private val control: EurekaShipControl? get() = ship?.getAttachment(EurekaShipControl::class.java)
    private val seats = mutableListOf<ShipMountingEntity>()
    val assembled get() = ship != null
    val aligning get() = control?.aligning ?: false
    private var shouldDisassembleWhenPossible = false
    private var orphanScanCooldown = 0

    override fun createMenu(id: Int, playerInventory: Inventory, player: Player): AbstractContainerMenu {
        return ShipHelmScreenMenu(id, playerInventory, this)
    }

    override fun getDisplayName(): Component {
        return Component.translatable("gui.vs_eureka.ship_helm")
    }

    // Needs to get called server-side
    fun spawnSeat(blockPos: BlockPos, state: BlockState, level: ServerLevel): ShipMountingEntity {
        val newPos = blockPos.relative(state.getValue(HorizontalDirectionalBlock.FACING))
        val newState = level.getBlockState(newPos)
        val newShape = newState.getShape(level, newPos)
        val newBlock = newState.block
        var height = 0.5
        if (!newState.isAir) {
            height = if (
                newBlock is StairBlock &&
                (!newState.hasProperty(StairBlock.HALF) || newState.getValue(StairBlock.HALF) == Half.BOTTOM)
            )
                0.5 // Valid StairBlock
            else
                newShape.max(Axis.Y)
        }
        // Standing-helmsman foot leveling: the rider stands centred on the block in front of the
        // helm, so their feet rest on that block's floor (newPos.below()). If that floor sits
        // lower than the block the helm is placed on -- e.g. a bottom slab against a full-block
        // deck -- drop the seat by the difference so the feet meet the lower floor instead of
        // hovering. Capped at half a block; a flush floor, or a gap with nothing solid to stand
        // on, leaves the rider at the normal deck height.
        val deckTopY = floorTopWorldY(level, blockPos.below())
        val frontFloorTopY = floorTopWorldY(level, newPos.below())
        val standDrop = if (deckTopY != null && frontFloorTopY != null)
            (deckTopY - frontFloorTopY).coerceIn(0.0, 0.5)
        else
            0.0

        val entity = ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE.create(level, EntitySpawnReason.MOB_SUMMONED)!!.apply {
            val seatEntityPos: Vector3dc = Vector3d(newPos.x + .5, (newPos.y - .5) + height - standDrop, newPos.z + .5)
            snapTo(seatEntityPos.x(), seatEntityPos.y(), seatEntityPos.z(), yRot, xRot)

            lookAt(
                EntityAnchorArgument.Anchor.EYES,
                state.getValue(HORIZONTAL_FACING).unitVec3i.toDoubles().add(position())
            )

            isController = true
        }

        level.addFreshEntityWithPassengers(entity)
        return entity
    }

    // World-space Y of the top surface of the block at [pos], or null if there is nothing solid
    // there to stand on (air / empty shape). Used to level the standing helmsman onto the floor
    // in front of the helm.
    private fun floorTopWorldY(level: ServerLevel, pos: BlockPos): Double? {
        val state = level.getBlockState(pos)
        if (state.isAir) return null
        val shape = state.getShape(level, pos)
        if (shape.isEmpty) return null
        return pos.y + shape.max(Axis.Y)
    }

    fun startRiding(player: Player, force: Boolean, blockPos: BlockPos, state: BlockState, level: ServerLevel): Boolean {
        for (i in seats.size - 1 downTo 0) {
            if (!seats[i].isVehicle) {
                seats[i].kill(level)
                seats.removeAt(i)
            } else if (!seats[i].isAlive) {
                seats.removeAt(i)
            }
        }

        val seat = spawnSeat(blockPos, blockState, level)
        val ride = player.startRiding(seat, force, true)

        if (ride) {
            control?.seatedPlayer = player
            seats.add(seat)
        }

        return ride
    }

    fun tick() {
        // One shipyard lookup per tick: the [ship]/[control] getters walk the loaded-ship index
        // on every call, and this tick used to do that two or three times.
        val curShip = ship
        val curControl = curShip?.getAttachment(EurekaShipControl::class.java)
        if (shouldDisassembleWhenPossible && curControl?.canDisassemble == true) {
            this.disassemble()
        }
        curControl?.ship = curShip

        // The ShipMountingEntity seat does not tick server-side: shipyard chunks are only
        // promoted to BLOCK_TICKING (see VS2 MixinChunkHolder), never ENTITY_TICKING, so the
        // vanilla Player.rideTick() sneak-to-dismount check never runs for a seated player.
        // The helm block entity itself does tick (BLOCK_TICKING), so drive the dismount here.
        val lvl = level
        if (lvl is ServerLevel) {
            // A world reload recreates this block entity with an empty [seats] list, but the
            // ShipMountingEntity seat (with its rider) was persisted. Re-adopt that orphaned
            // seat so the dismount loop below still works after a reload. Orphans only appear
            // right after a reload or a late player join, so probe at most once a second —
            // every unmanned helm hits this branch every tick otherwise.
            if (seats.isEmpty()) {
                if (--orphanScanCooldown <= 0) {
                    orphanScanCooldown = ORPHAN_SCAN_INTERVAL_TICKS
                    val seatBox = AABB(blockPos.relative(blockState.getValue(HORIZONTAL_FACING))).inflate(0.5)
                    for (player in lvl.players()) {
                        val vehicle = player.vehicle
                        if (vehicle is ShipMountingEntity && vehicle.isAlive &&
                            seatBox.contains(vehicle.x, vehicle.y, vehicle.z)
                        ) {
                            vehicle.isController = true
                            seats.add(vehicle)
                        }
                    }
                }
            }

            val iter = seats.iterator()
            while (iter.hasNext()) {
                val seat = iter.next()
                val rider = seat.passengers.firstOrNull()
                if (rider == null || !seat.isAlive) {
                    seat.kill(lvl)
                    iter.remove()
                } else if (rider is Player && rider.isShiftKeyDown) {
                    rider.stopRiding()
                    if (curShip != null && rider is ServerPlayer) {
                        val inWorld = curShip.shipToWorld.transformPosition(
                            Vector3d(seat.x, seat.y, seat.z)
                        )
                        rider.teleportTo(inWorld.x, inWorld.y, inWorld.z)
                    }
                    seat.kill(lvl)
                    iter.remove()
                } else {
                    // Drive the rider's server position from the seat each tick. The seat
                    // sits in shipyard chunks (BLOCK_TICKING only per VS2 MixinChunkHolder),
                    // so the seat never ticks server-side, so vanilla rideTick() ->
                    // positionRider() never runs. Without this, the rider's server pos
                    // stays frozen at mount-time world coords even though the seat is
                    // being carried around by ship physics. That caused two bugs:
                    //   - Other clients see the rider drift away as the ship moves
                    //     (LAN: visible body floats off behind / through the ship).
                    //   - On descent, the dismount teleportTo() jumps the rider from the
                    //     stale (high) Y to the current (low) seat Y, which produced
                    //     instant fall-death in singleplayer survival.
                    // Vanilla EntityDragger explicitly skips mounted entities, so it can't
                    // close this gap either. With the per-tick sync the dismount teleport
                    // becomes a no-op (rider is already at the seat's world pos).
                    if (curShip != null) {
                        val worldPos = curShip.shipToWorld.transformPosition(
                            Vector3d(seat.x, seat.y, seat.z)
                        )
                        rider.snapTo(worldPos.x, worldPos.y, worldPos.z, rider.yRot, rider.xRot)
                    }
                }
            }
        }
    }

    // Needs to get called server-side
    fun assemble(player: Player) {
        val level = level as ServerLevel

        // Check the block state before assembling to avoid creating an empty ship
        val blockState = level.getBlockState(blockPos)
        if (blockState.block !is ShipHelmBlock) return

        // Assembly places blocks straight into the shipyard without firing onPlace, so the
        // counters BalloonBlock/FloaterBlock/AnchorBlock/ShipHelmBlock maintain via onPlace
        // would all stay zero on a freshly assembled ship -- leaving it with no buoyancy.
        // Tally them during the collect pass and apply the totals to EurekaShipControl below.
        var helmCount = 0
        var balloonCount = 0
        var floaterCount = 0
        var anchorCount = 0
        var activeAnchorCount = 0
        val builtShip = ShipAssembler.collectBlocks(
            level,
            blockPos
        ) {
            val allowed = !it.isAir && !it.`is`(ASSEMBLE_BLACKLIST) &&
            // TODO: Remove blockBlacklist
            !(EurekaConfig.SERVER.blockBlacklist.isNotEmpty() && EurekaConfig.SERVER.blockBlacklist.contains(BuiltInRegistries.BLOCK.getKey(it.block).toString()))
            if (allowed) {
                when (it.block) {
                    is ShipHelmBlock -> helmCount++
                    is BalloonBlock -> balloonCount++
                    // Floater buoyancy scales with 15 - redstone power, matching FloaterBlock.onPlace.
                    is FloaterBlock -> floaterCount += 15 - it.getValue(BlockStateProperties.POWER)
                    is AnchorBlock -> {
                        anchorCount++
                        if (it.getValue(BlockStateProperties.POWERED)) activeAnchorCount++
                    }
                }
            }
            return@collectBlocks allowed
        }

        if (builtShip == null) {
            player.displayClientMessage(Component.translatable("gui.vs_eureka.too_big", EurekaConfig.SERVER.maxShipBlocks), true)
            logger.warn("Failed to assemble to large of a ship for ${player.name.string}")
        } else {
            // A freshly-assembled ship is created as ShipData and only becomes a
            // LoadedServerShip once vs-core builds its ShipObject (usually the next
            // ship-world tick). Attachments require a LoadedServerShip, so attach
            // EurekaShipControl now if the ship is already loaded, otherwise defer.
            val shipId = builtShip.id

            fun applyControl(loadedShip: LoadedServerShip) {
                val control = EurekaShipControl.getOrCreate(loadedShip)
                // Set helms (>= 1 for any real ship) first so the deleteIfEmpty() in the
                // remaining setters can't drop the attachment mid-update when a count is 0.
                control.helms = helmCount
                control.balloons = balloonCount
                control.floaters = floaterCount
                control.anchors = anchorCount
                control.anchorsActive = activeAnchorCount
            }

            val loaded = level.shipObjectWorld.loadedShips.getById(shipId)
            if (loaded != null) {
                applyControl(loaded)
            } else {
                level.server.executeIf({ level.shipObjectWorld.loadedShips.getById(shipId) != null }) {
                    level.shipObjectWorld.loadedShips.getById(shipId)?.let { loadedShip ->
                        applyControl(loadedShip)
                    }
                }
            }
        }
    }

    fun disassemble() {
        val ship = ship ?: return
        val level = level ?: return
        val control = control ?: return

        if (!control.canDisassemble) {
            shouldDisassembleWhenPossible = true
            control.disassembling = true
            control.aligning = true
            return
        }

        val inWorld = ship.shipToWorld.transformPosition(this.blockPos.toJOMLD())

        ShipAssembler.unfillShip(
            level as ServerLevel,
            ship,
            this.blockPos,
            BlockPos.containing(inWorld.x, inWorld.y, inWorld.z)
        )
        // ship.die() TODO i think we do need this no? or autodetecting on all air

        shouldDisassembleWhenPossible = false
    }

    fun align() {
        val control = control ?: return
        control.aligning = !control.aligning
    }

    override fun setRemoved() {
        if (level?.isClientSide == false) {
            for (i in seats.indices) {
                seats[i].kill(level as ServerLevel)
            }
            seats.clear()
        }

        super.setRemoved()
    }

    fun sit(player: Player, force: Boolean = false): Boolean {
        // If player is already controlling the ship, open the helm menu
        if (!force && player.vehicle?.type == ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE && seats.contains(player.vehicle as ShipMountingEntity)) {
            player.openMenu(this)
            return true
        }

        return startRiding(player, force, blockPos, blockState, level as ServerLevel)
    }
    private val logger by logger()
}
