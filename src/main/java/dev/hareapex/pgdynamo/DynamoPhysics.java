package dev.hareapex.pgdynamo;

import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlockEntity;

/**
 * Generator maths, the same as PowerGrid's induction generator ({@code GeneratorCoupling}):
 * voltage = flux x angular speed (rad/s), shaft force = flux x current, and force turns into
 * stress through torqueForStress x pi/30. That makes shaft power = su x torqueForStress x (pi/30)^2.
 * All numbers come from PowerGrid's configs at runtime.
 */
public final class DynamoPhysics {
    /** Winding ratings are given at this shaft speed. */
    public static final double REF_RPM = 256.0;
    /** Bearing and brush losses: the shaft pays 5% more than the electrical power made. */
    public static final double FRICTION = 1.05;
    private static final double RAD_PER_RPM = Math.PI / 30.0;

    private DynamoPhysics() {
    }

    /** The voltage that drives this motor to 256 RPM by its own law (RPM = V^2 / (R x T) x 30 pi). 258 V by default. */
    public static double stockVolts(ElectricMotorBlockEntity motor) {
        return Math.sqrt(REF_RPM * motor.resistance() * motor.torque() / ElectricMotorBlockEntity.CONVERSION_CONSTANT);
    }

    public static double ratedVolts(DynamoMode mode, ElectricMotorBlockEntity motor) {
        return mode.ratedVolts() > 0 ? mode.ratedVolts() : stockVolts(motor);
    }

    /** Fewer, thicker turns: resistance scales with voltage squared, so every winding has the same power rating. */
    public static double windingResistance(DynamoMode mode, ElectricMotorBlockEntity motor) {
        double ratio = ratedVolts(mode, motor) / stockVolts(motor);
        return motor.resistance() * ratio * ratio;
    }

    /** Volts per rad/s. */
    public static double flux(DynamoMode mode, ElectricMotorBlockEntity motor) {
        return ratedVolts(mode, motor) / (REF_RPM * RAD_PER_RPM);
    }

    public static double radPerSecond(float rpm) {
        return rpm * RAD_PER_RPM;
    }

    /** Watts of shaft power per su: torqueForStress x (pi/30)^2, 0.1645 W by default. */
    public static double wattsPerSu() {
        double torqueForStress = ModdedConfigs.server().kinetics.torqueForStress.getF();
        return torqueForStress * RAD_PER_RPM * RAD_PER_RPM;
    }
}
