package org.valkyrienskies.eureka

import com.github.imifou.jsonschema.module.addon.annotation.JsonSchema

object EurekaConfig {
    @JvmField
    val CLIENT = Client()

    @JvmField
    val SERVER = Server()

    class Client

    class Server {

        @JsonSchema(description = "Movement power per engine when heated fully")
        var enginePowerLinear: Float = 100000f

        @JsonSchema(description = "Movement power per engine with minimal heat")
        var enginePowerLinearMin: Float = 10000f

        @JsonSchema(description = "Turning power per engine when heated fully")
        var enginePowerAngular = 1.0f

        @JsonSchema(description = "Turning power per engine when minimal heat")
        var enginePowerAngularMin = 0.0f

        @JsonSchema(description = "The amount of heat a engine loses per tick")
        var engineHeatLoss = 0.01f

        @JsonSchema(description = "The amount of heat a gain per tick (when burning)")
        var engineHeatGain = 0.12f

        @JsonSchema(description = "Increases heat gained at low heat level, and increased heat decreases when at high heat and not consuming fuel")
        var engineHeatChangeExponent = 0.1f

        @JsonSchema(description = "Pause fuel consumption and power when block is powered")
        var engineRedstoneBehaviorPause = false

        @JsonSchema(description = "Number of Balloons a single engine can power. 0 disables the feature")
        var maxBalloonsPerEngine = 0

        @JsonSchema(description = "Avoids consuming fuel when heat is 100%")
        var engineFuelSaving = false

        @JsonSchema(description = "Increasing this value will result in more items being able to converted to fuel")
        var engineMinCapacity = 2000

        @JsonSchema(description = "Fuel burn time multiplier")
        var engineFuelMultiplier = 2f

        @JsonSchema(description = "Extra engine power for when having multiple engines per engine")
        var engineBoost = 4.0

        @JsonSchema(description = "At what amount of engines the boost will start taking effect")
        var engineBoostOffset = 5.0

        @JsonSchema(description = "The final linear boost will be raised to the power of 2, and the result of the delta is multiple by this value")
        var engineBoostExponentialPower = 0.000001

        @JsonSchema(description = "Max speed of a ship with engines (actual max speed varies with engines and mass.)")
        var maxSpeedFromEngines = 24.0

        @JsonSchema(description = "Max reverse speed of a ship with engines")
        var maxReverseSpeedFromEngines = 8.0

        @JsonSchema(description = "The speed at which the ship stabilizes")
        var stabilizationSpeed = 10.0

        @JsonSchema(description = "The amount extra that each floater will make the ship float, per kg mass")
        var floaterBuoyantFactorPerKg = 50_000.0

        @JsonSchema(description = "The maximum amount extra each floater will multiply the buoyant force by, irrespective of mass")
        var maxFloaterBuoyantFactor = 1.0

        @JsonSchema(description = "how much the mass decreases the speed.")
        var speedMassScale = 1.0

        // The velocity any ship at least can move at.
        @JsonSchema(description = "The speed a ship with no engines can move at")
        var baseSpeed = 3.0

        @JsonSchema(description = "Forward/backward thrust multiplier for a ship grounded on land, to overcome ground friction. 1 = no assist")
        var landThrustAssist = 5.33

        @JsonSchema(description = "Forward/backward thrust multiplier for a ship travelling on water. 1 = no assist")
        var waterThrustAssist = 8.0

        // Sensitivity of the up/down impulse buttons.
        // TODO maybe should be moved to VS2 client-side config?
        @JsonSchema(description = "Vertical sensitivity when ascending")
        var baseImpulseElevationRate = 2.0

        @JsonSchema(description = "Vertical sensitivity when descending")
        var baseImpulseDescendRate = 4.0

        @JsonSchema(description = "The max elevation speed boost gained by having extra extra balloons")
        var balloonElevationMaxSpeed = 5.5

        // Higher numbers make the ship accelerate to max speed faster
        @JsonSchema(description = "Ascend and descend acceleration")
        var elevationSnappiness = 1.0

        // Allow Eureka controlled ships to be affected by fluid drag
        @JsonSchema(description = "Allow Eureka controlled ships to be affected by fluid drag")
        var doFluidDrag = false

        // Do i need to explain? the mass 1 baloon gets to float
        @JsonSchema(description = "Amount of mass in kg a balloon can lift")
        var massPerBalloon = 5000.0

        @JsonSchema(
            description = "Multiplier on balloon FLIGHT LIFT -- the anti-gravity up-force balloons apply " +
                "in air and water alike. This is NOT water buoyancy. 1 = normal lift, 0 = balloons provide " +
                "no lift (debug lever for ships hovering above the waterline). Staying afloat on water is a " +
                "separate system: see floaterBuoyantFactorPerKg / maxFloaterBuoyantFactor."
        )
        var balloonLiftMultiplier = 1.0

        @JsonSchema(
            description = "Water altitude-hold: a HYBRID ship (has both floaters AND balloons) pins its " +
                "current Y the moment its keel touches water, so it sails on the surface instead of balloon " +
                "lift floating it back into the air. Hands off the helm (or cruise) = hold; press descend/" +
                "ascend to choose a new depth, which re-latches when you let go; rising clear of the water " +
                "returns to normal in-air hover. Vertical-only, so sailing speed is unaffected."
        )
        var enableWaterAltitudeHold = true

        @JsonSchema(
            description = "Stiffness of the water altitude-hold spring. Higher pins the Y tighter and faster " +
                "but can feel abrupt; lower is softer and may sag slightly. Critically damped, so it never " +
                "oscillates. Default 9.0."
        )
        var waterAltitudeHoldStiffness = 9.0

        @JsonSchema(
            description = "How submerged the ship must be (fraction 0..1) for the water altitude-hold to " +
                "ENGAGE. Once engaged it stays until the hull fully clears the water. Small = engages as soon " +
                "as the keel touches. Default 0.05."
        )
        var waterAltitudeHoldMinOverlap = 0.05

        // The amount of speed that the ship can move at when the left/right impulse button is held down.
        @JsonSchema(description = "The maximum linear velocity at any point on the ship caused by helm torque")
        var turnSpeed = 3.0

        @JsonSchema(description = "The maximum linear acceleration at any point on the ship caused by helm torque")
        var turnAcceleration = 10.0

        @JsonSchema(
            description = "The maximum distance from center of mass to one end of the ship considered by " +
                "the turn speed. At it's default of 16, it ensures that really large ships will turn at the same " +
                "speed as a ship with a center of mass only 16 blocks away from the farthest point in the ship. " +
                "That way, large ships do not turn painfully slowly"
        )
        var maxSizeForTurnSpeedPenalty = 16.0

        // The strength used when trying to level the ship
        @JsonSchema(description = "How much torque a ship will apply to try and keep level")
        var stabilizationTorqueConstant = 15.0

        // Max anti-velocity used when trying to stop the ship
        @JsonSchema(description = "How fast a ship will stop. 1 = fast stop, 0 = slow stop")
        var linearStabilizeMaxAntiVelocity = 1.0

        // Instability scaled with mass and squared speed
        @JsonSchema(description = "Stronger stabilization with higher mass, less at higher speeds.")
        var scaledInstability = 70.0

        // Unscaled linear instability cased by speed
        @JsonSchema(description = "Less stabilization at higher speed.")
        var unscaledInstability = 0.1

        @JsonSchema(description = "How fast a ship will stop and accelerate.")
        var linearMassScaling = 0.0002

        // Must be positive. higher value will case slower acceleration and deceleration.
        @JsonSchema(description = "Base mass for linear acceleration in Kg.")
        var linearBaseMass = 50.0

        //when value is same as linearMaxMass, actual value will be 1/3. actual value will be close to linearMaxMass when 5 times over
        @JsonSchema(description = "Max smoothing value, will smooth out before reaching max value.")
        var linearMaxMass = 10000.0

        @JsonSchema(description = "Max unscaled speed in m/s without engines.")
        var linearCasualSpeed = 3.0

        // Anti-velocity mass relevance when stopping the ship
        // Max 10.0 (means no mass irrelevance)
        @JsonSchema(description = "How much inertia affects Eureka ships. Max 10 = full inertia")
        var antiVelocityMassRelevance = 0.8

        // Chance that if side will pop, its this chance per side
        @JsonSchema(description = "Chance for popped balloons to pop adjacent balloons, per side")
        var popSideBalloonChance = 0.3

        @JsonSchema(description = "Whether the ship helm assembles diagonally connected blocks or not")
        var diagonals = true

        @JsonSchema(description = "Weight of ballast when lowest redstone power")
        var ballastWeight: Double = 10000.0

        @JsonSchema(description = "Weight of ballast when highest redstone power")
        var ballastNoWeight: Double = 1000.0

        @JsonSchema(description = "Whether or not disassembly is permitted")
        var allowDisassembly = true

        @JsonSchema(description = "Maximum number of blocks allowed in a ship. Set to 0 for no limit")
        var maxShipBlocks = 50000

        // TODO: Remove blockBlacklist
        // Blacklist of blocks that don't get added for ship building
        @JsonSchema(description = "Blacklist of blocks that don't get assembled (Use Block Tag instead)")
        var blockBlacklist : Set<String> = emptySet()
    }
}
