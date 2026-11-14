package dev.hareapex.pgdynamo;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import org.patryk3211.powergrid.collections.ModIcons;

import java.util.Locale;

/**
 * The Electric Motor's mode. MOTOR is PowerGrid's normal behaviour. The others turn it into a
 * permanent-magnet generator whose winding is rated for that many volts at 256 RPM.
 * Worlds save the position in this list as a number: never reorder it, only add at the end.
 */
public enum DynamoMode implements INamedIconOptions {
    MOTOR(0),
    GEN_12(12),
    GEN_24(24),
    GEN_30(30),
    GEN_60(60),
    GEN_120(120),
    /** The motor's own winding: the voltage that would drive it to 256 RPM (258 V with default configs). */
    GEN_STOCK(-1);

    private final float ratedVolts;

    DynamoMode(float ratedVolts) {
        this.ratedVolts = ratedVolts;
    }

    public boolean isGenerator() {
        return this != MOTOR;
    }

    /** Volts at 256 RPM, or a negative number for the stock winding. */
    public float ratedVolts() {
        return ratedVolts;
    }

    @Override
    public AllIcons getIcon() {
        return this == MOTOR ? ModIcons.I_MOTOR : ModIcons.I_GENERATOR;
    }

    @Override
    public String getTranslationKey() {
        return "pgdynamo.mode." + name().toLowerCase(Locale.ROOT);
    }
}
