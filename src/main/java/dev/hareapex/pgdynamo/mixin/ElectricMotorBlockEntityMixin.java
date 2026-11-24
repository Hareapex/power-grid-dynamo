package dev.hareapex.pgdynamo.mixin;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.hareapex.pgdynamo.DynamoBox;
import dev.hareapex.pgdynamo.DynamoMode;
import dev.hareapex.pgdynamo.DynamoPhysics;
import dev.hareapex.pgdynamo.IDynamoMotor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlock;
import org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Locale;

/**
 * Turns PowerGrid's Electric Motor into a motor OR a generator.
 * <p>
 * Motor mode leaves PowerGrid's code untouched. Generator mode:
 * <ul>
 * <li>Circuit: terminal 0 (+) - winding resistance - ideal voltage source - terminal 1 (-).
 *     The winding is the motor's own {@code coil} wire, so PowerGrid's heating still works.</li>
 * <li>Voltage = flux x shaft speed (rad/s), signed by rotation direction, like a real DC machine.</li>
 * <li>The shaft pays for the power drawn (plus 5% friction) as stress, averaged over 10 ticks.</li>
 * <li>A governor keeps that stress within what the shaft network has free; the voltage sags instead.</li>
 * <li>The motor never drives the shaft in this mode.</li>
 * </ul>
 */
@Mixin(value = ElectricMotorBlockEntity.class, remap = false)
public abstract class ElectricMotorBlockEntityMixin extends GeneratingKineticBlockEntity implements IDynamoMotor {
    @Unique
    private static final int PGDYNAMO_WINDOW_TICKS = 10;

    @Shadow
    private ElectricWire coil;
    @Shadow
    protected ElectricBehaviour electricBehaviour;
    @Shadow
    private float generatedSpeed;

    @Unique
    private ScrollOptionBehaviour<DynamoMode> pgdynamo$mode;
    @Unique
    private VoltageSourceCoupling pgdynamo$source;
    @Unique
    private boolean pgdynamo$builtGenerator;
    @Unique
    private float pgdynamo$testHeadroom = -1f;
    @Unique
    private float pgdynamo$governor = 1f;
    /** Generated power summed over the current window (watt-ticks). */
    @Unique
    private double pgdynamo$energy;
    @Unique
    private int pgdynamo$window;
    /** Stress taken from the shaft, su per RPM. */
    @Unique
    private float pgdynamo$impact;
    @Unique
    private float pgdynamo$netCapacity = -1f;
    @Unique
    private float pgdynamo$netStress;
    /** Terminal voltage and current, synced to clients for the goggles. */
    @Unique
    private float pgdynamo$shownVolts;
    @Unique
    private float pgdynamo$shownAmps;

    private ElectricMotorBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- Mode setting ----------------------------------------------------------------------

    @Inject(method = "addBehaviours", at = @At("TAIL"))
    private void pgdynamo$addModeSetting(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        pgdynamo$mode = new ScrollOptionBehaviour<>(DynamoMode.class, Component.translatable("pgdynamo.gui.mode"), this, new DynamoBox());
        pgdynamo$mode.withCallback(i -> pgdynamo$modeChanged());
        behaviours.add(pgdynamo$mode);
    }

    @Unique
    private DynamoMode pgdynamo$currentMode() {
        return pgdynamo$mode == null ? DynamoMode.MOTOR : pgdynamo$mode.get();
    }

    @Unique
    private boolean pgdynamo$generating() {
        return pgdynamo$currentMode().isGenerator();
    }

    @Unique
    private ElectricMotorBlockEntity pgdynamo$self() {
        return (ElectricMotorBlockEntity) (Object) this;
    }

    @Unique
    private void pgdynamo$modeChanged() {
        pgdynamo$governor = 1f;
        pgdynamo$energy = 0;
        pgdynamo$window = 0;
        pgdynamo$impact = 0f;
        pgdynamo$shownVolts = 0f;
        pgdynamo$shownAmps = 0f;
        if (electricBehaviour != null)
            electricBehaviour.rebuildCircuit(false); // keeps the wires attached
        if (level != null && !level.isClientSide) {
            if (hasNetwork())
                getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
            updateGeneratedRotation(); // drops (or resumes) the motor's role as a rotation source
            sendData();
        }
    }

    // ---- Circuit ---------------------------------------------------------------------------

    @Inject(method = "buildCircuit", at = @At("HEAD"), cancellable = true)
    private void pgdynamo$buildGeneratorCircuit(IElectricEntity.CircuitBuilder builder, CallbackInfo ci) {
        DynamoMode mode = pgdynamo$currentMode();
        pgdynamo$builtGenerator = mode.isGenerator();
        if (!mode.isGenerator()) {
            pgdynamo$source = null;
            return; // PowerGrid builds its normal motor coil
        }
        builder.setTerminalCount(2);
        FloatingNode mid = builder.addInternalNode();
        coil = builder.connect((float) DynamoPhysics.windingResistance(mode, pgdynamo$self()), builder.terminalNode(0), mid);
        VoltageSourceCoupling source = new VoltageSourceCoupling(mid, builder.terminalNode(1), 0.0F);
        builder.add(source);
        pgdynamo$source = source;
        ci.cancel();
    }

    /** PowerGrid builds the circuit before the saved mode is read, so rebuild it once the mode is known. */
    @Inject(method = "read", at = @At("TAIL"))
    private void pgdynamo$read(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        pgdynamo$impact = tag.getFloat("DynamoImpact");
        if (clientPacket) {
            pgdynamo$shownVolts = tag.getFloat("DynamoVolts");
            pgdynamo$shownAmps = tag.getFloat("DynamoAmps");
        }
        if (electricBehaviour != null && pgdynamo$builtGenerator != pgdynamo$generating())
            electricBehaviour.rebuildCircuit(false);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void pgdynamo$write(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        if (!pgdynamo$generating())
            return;
        tag.putFloat("DynamoImpact", pgdynamo$impact);
        if (clientPacket) {
            tag.putFloat("DynamoVolts", pgdynamo$shownVolts);
            tag.putFloat("DynamoAmps", pgdynamo$shownAmps);
        }
    }

    // ---- Keep the motor logic out of the way in Generator mode ----------------------------------

    /** In Generator mode the coil carries the dynamo's own output: the motor must not read it as drive power. */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/kinetics/motor/ElectricMotorBlockEntity;calculateSpeed(DD)D"))
    private double pgdynamo$noSelfDrive(double power, double torque) {
        return pgdynamo$generating() ? 0.0 : ElectricMotorBlockEntity.calculateSpeed(power, torque);
    }

    /** A generator is never a rotation source. */
    @Inject(method = "getGeneratedSpeed", at = @At("HEAD"), cancellable = true)
    private void pgdynamo$notASource(CallbackInfoReturnable<Float> cir) {
        if (pgdynamo$generating())
            cir.setReturnValue(0f);
    }

    /** PowerGrid lowers the motor coil's resistance with load; the generator winding stays fixed. */
    @Redirect(method = "updateFromNetwork", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/electricity/sim/ElectricWire;setResistance(D)V"))
    private void pgdynamo$keepWinding(ElectricWire wire, double resistance) {
        if (!pgdynamo$generating())
            wire.setResistance(resistance);
    }

    @Inject(method = "updateFromNetwork", at = @At("TAIL"))
    private void pgdynamo$rememberNetwork(float maxStress, float currentStress, int networkSize, CallbackInfo ci) {
        pgdynamo$netCapacity = maxStress;
        pgdynamo$netStress = currentStress;
    }

    // ---- Generating --------------------------------------------------------------------------

    @Inject(method = "tick", at = @At("TAIL"))
    private void pgdynamo$generate(CallbackInfo ci) {
        if (level == null || level.isClientSide || !pgdynamo$generating() || pgdynamo$source == null)
            return;
        ElectricMotorBlockEntity self = pgdynamo$self();
        DynamoMode mode = pgdynamo$currentMode();

        float speed = getSpeed(); // 0 while the network is overstressed
        float rpm = Math.abs(speed);
        // Same direction convention as the motor: turning the way + on the red terminal would drive it makes + on red.
        float forward = KineticBlockEntity.convertToDirection(speed, getBlockState().getValue(ElectricMotorBlock.FACING));
        double emf = DynamoPhysics.flux(mode, self) * DynamoPhysics.radPerSecond(forward) * pgdynamo$governor;
        pgdynamo$source.setVoltage(emf);

        // From the last solve. Negative = a stronger source pushes current back in; that costs the shaft nothing.
        double amps = -pgdynamo$source.getCurrent();
        double watts = amps * pgdynamo$source.getVoltage();
        if (watts > 0)
            pgdynamo$energy += watts;
        pgdynamo$window++;

        // Governor: stay under the free stress (3% margin, small dead band); the voltage sags instead.
        double headroomSu = pgdynamo$headroomSu(rpm);
        if (rpm > 0.01f && headroomSu >= 0) {
            double target = 0.97 * headroomSu * DynamoPhysics.wattsPerSu() / DynamoPhysics.FRICTION;
            if (watts > target && watts > 1e-9)
                pgdynamo$governor = (float) Math.max(0.0, pgdynamo$governor * Math.sqrt(target / watts));
            else if (watts < 0.95 * target)
                pgdynamo$governor = Math.min(1f, pgdynamo$governor + 0.01f);
        } else {
            pgdynamo$governor = 1f;
        }

        if (pgdynamo$window >= PGDYNAMO_WINDOW_TICKS) {
            double averageWatts = pgdynamo$energy / pgdynamo$window;
            pgdynamo$energy = 0;
            pgdynamo$window = 0;
            float impact = rpm > 0.01f ? (float) (DynamoPhysics.FRICTION * averageWatts / (DynamoPhysics.wattsPerSu() * rpm)) : 0f;
            boolean stressChanged = Math.abs(impact - pgdynamo$impact) > Math.max(0.01f, 0.02f * pgdynamo$impact);
            if (stressChanged) {
                pgdynamo$impact = impact;
                if (hasNetwork())
                    getOrCreateNetwork().updateStressFor(this, impact);
            }
            float volts = (float) Math.abs(pgdynamo$source.getVoltage() - amps * DynamoPhysics.windingResistance(mode, self));
            float shownAmps = (float) Math.abs(amps);
            if (stressChanged || Math.abs(volts - pgdynamo$shownVolts) > 0.5f || Math.abs(shownAmps - pgdynamo$shownAmps) > 0.05f) {
                pgdynamo$shownVolts = volts;
                pgdynamo$shownAmps = shownAmps;
                sendData();
            }
        }
    }

    /** Free stress (su) the dynamo may use, counting what it already takes; negative = not known yet. */
    @Unique
    private double pgdynamo$headroomSu(float rpm) {
        if (pgdynamo$testHeadroom >= 0)
            return pgdynamo$testHeadroom;
        if (pgdynamo$netCapacity <= 0)
            return -1;
        double ownSu = pgdynamo$impact * rpm;
        return Math.max(0, pgdynamo$netCapacity - (pgdynamo$netStress - ownSu));
    }

    // ---- Create overrides ----------------------------------------------------------------------

    @Override
    public float calculateStressApplied() {
        if (pgdynamo$generating()) {
            this.lastStressApplied = pgdynamo$impact;
            return pgdynamo$impact;
        }
        return super.calculateStressApplied();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (!pgdynamo$generating())
            return added;
        double rated = DynamoPhysics.ratedVolts(pgdynamo$currentMode(), pgdynamo$self());
        tooltip.add(Component.literal("    ").append(Component.translatable("pgdynamo.goggles.winding",
                String.format(Locale.ROOT, "%.0f", rated))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("    ").append(Component.translatable("pgdynamo.goggles.output",
                String.format(Locale.ROOT, "%.1f", pgdynamo$shownVolts),
                String.format(Locale.ROOT, "%.2f", pgdynamo$shownAmps),
                String.format(Locale.ROOT, "%.0f", pgdynamo$shownVolts * pgdynamo$shownAmps))).withStyle(ChatFormatting.AQUA));
        return true;
    }

    // ---- IDynamoMotor (game tests) -----------------------------------------------------------------

    @Override
    public DynamoMode pgdynamo$getMode() {
        return pgdynamo$currentMode();
    }

    @Override
    public void pgdynamo$setMode(DynamoMode mode) {
        if (pgdynamo$mode != null)
            pgdynamo$mode.setValue(mode.ordinal());
    }

    @Override
    public double pgdynamo$getEmf() {
        return pgdynamo$source == null ? 0 : pgdynamo$source.getVoltage();
    }

    @Override
    public double pgdynamo$getOutputCurrent() {
        return pgdynamo$source == null ? 0 : -pgdynamo$source.getCurrent();
    }

    @Override
    public float pgdynamo$getImpact() {
        return pgdynamo$generating() ? pgdynamo$impact : 0f;
    }

    @Override
    public float pgdynamo$getMotorGeneratedSpeed() {
        return generatedSpeed;
    }

    @Override
    public void pgdynamo$setTestHeadroom(float su) {
        pgdynamo$testHeadroom = su;
    }
}
