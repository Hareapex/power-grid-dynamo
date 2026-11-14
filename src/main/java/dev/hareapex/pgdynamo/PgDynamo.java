package dev.hareapex.pgdynamo;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamo: gives PowerGrid's Electric Motor a Motor/Generator mode.
 * All the behaviour lives in {@link dev.hareapex.pgdynamo.mixin.ElectricMotorBlockEntityMixin}.
 */
@Mod(PgDynamo.MOD_ID)
public final class PgDynamo {
    public static final String MOD_ID = "pgdynamo";
    public static final Logger LOGGER = LoggerFactory.getLogger("Dynamo");

    public PgDynamo() {
        LOGGER.info("Dynamo loaded: PowerGrid's Electric Motor now has a Motor/Generator mode.");
    }
}
