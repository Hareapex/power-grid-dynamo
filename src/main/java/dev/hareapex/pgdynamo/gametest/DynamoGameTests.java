package dev.hareapex.pgdynamo.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.hareapex.pgdynamo.DynamoMode;
import dev.hareapex.pgdynamo.IDynamoMotor;
import dev.hareapex.pgdynamo.PgDynamo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlock;

import java.util.Locale;

/**
 * Server-side checks with PowerGrid's real circuit solver and Create's real shaft network.
 * A Creative Motor turns the dynamo; the load is a real PowerGrid connection between its two
 * terminals (the same kind of connection a placed wire makes).
 * Run: ./gradlew runGameTestServer
 */
@GameTestHolder(PgDynamo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DynamoGameTests {
    private static final BlockPos DRIVER = new BlockPos(1, 2, 2);
    private static final BlockPos DYNAMO = new BlockPos(2, 2, 2);
    /** Ticks for the blocks to join their networks before the load is wired, and for things to settle after. */
    private static final int SETTLE = 10;
    private static final int RUN = 80;

    // Expected values with PowerGrid's default configs: R = 25.6 ohm, 64 su per RPM, torqueForStress 15.
    private static final double STOCK_VOLTS = Math.sqrt(256.0 * 25.6 * 64 * 15 / 94.24778); // 258.4 V at 256 RPM
    private static final double STOCK_OHMS = 25.6;
    private static final double WATTS_PER_SU = 15.0 * Math.pow(Math.PI / 30.0, 2);          // 0.1645

    private DynamoGameTests() {
    }

    private static IDynamoMotor setUp(GameTestHelper h, int rpm, DynamoMode mode) {
        h.setBlock(DRIVER, AllBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.setBlock(DYNAMO, ModdedBlocks.ELECTRIC_MOTOR.get().defaultBlockState().setValue(ElectricMotorBlock.FACING, Direction.WEST));
        CreativeMotorBlockEntity driver = (CreativeMotorBlockEntity) h.getBlockEntity(DRIVER);
        driver.generatedSpeed.setValue(rpm);
        BlockEntity be = h.getBlockEntity(DYNAMO);
        IDynamoMotor dynamo = (IDynamoMotor) be;
        dynamo.pgdynamo$setMode(mode);
        return dynamo;
    }

    /** A load of this many ohms wired from the dynamo's + terminal to its - terminal. */
    private static void wireLoad(GameTestHelper h, double ohms) {
        BlockPos pos = h.absolutePos(DYNAMO);
        GlobalElectricNetworks.makeSimpleConnection(h.getLevel(), new BlockWireEndpoint(pos, 0), new BlockWireEndpoint(pos, 1), (float) ohms);
    }

    private static float shaftSpeed(GameTestHelper h) {
        BlockEntity be = h.getBlockEntity(DYNAMO);
        return ((KineticBlockEntity) be).getSpeed();
    }

    /**
     * Current out of the generator, as a magnitude. Its sign follows the voltage's (which depends on the
     * rotation direction), so this also fails the test if the dynamo is absorbing power instead of making it.
     */
    private static double generatedAmps(GameTestHelper h, IDynamoMotor d) {
        double watts = d.pgdynamo$getEmf() * d.pgdynamo$getOutputCurrent();
        if (watts <= 0)
            h.fail(String.format(Locale.ROOT, "not generating: %.3f V x %.4f A", d.pgdynamo$getEmf(), d.pgdynamo$getOutputCurrent()));
        return Math.abs(d.pgdynamo$getOutputCurrent());
    }

    private static void near(GameTestHelper h, String what, double actual, double expected, double tolerance) {
        if (Math.abs(actual - expected) > tolerance)
            h.fail(String.format(Locale.ROOT, "%s: got %.4f, expected %.4f (+/- %.4f)", what, actual, expected, tolerance));
    }

    /** No load: volts = flux x speed, no stress, and the dynamo is not a rotation source. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void openCircuitVoltage(GameTestHelper h) {
        IDynamoMotor d = setUp(h, 64, DynamoMode.GEN_STOCK);
        h.runAfterDelay(RUN, () -> {
            double expected = STOCK_VOLTS * 64 / 256; // 64.6 V
            near(h, "open-circuit volts", Math.abs(d.pgdynamo$getEmf()), expected, expected * 0.01);
            near(h, "stress with no load (su/RPM)", d.pgdynamo$getImpact(), 0, 0.05);
            near(h, "shaft speed", Math.abs(shaftSpeed(h)), 64, 0.01);
            near(h, "motor's own speed value", d.pgdynamo$getMotorGeneratedSpeed(), 0, 0);
            h.succeed();
        });
    }

    /** Load equal to the winding: half the power reaches the load, and the shaft pays for all of it plus 5%. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void matchedLoad(GameTestHelper h) {
        IDynamoMotor d = setUp(h, 64, DynamoMode.GEN_STOCK);
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> wireLoad(h, STOCK_OHMS))
                .thenIdle(RUN)
                .thenExecute(() -> {
                    double emf = STOCK_VOLTS * 64 / 256;                    // 64.6 V
                    double amps = emf / (2 * STOCK_OHMS);                    // 1.26 A
                    near(h, "current", generatedAmps(h, d), amps, amps * 0.03);
                    double impact = 1.05 * emf * amps / (WATTS_PER_SU * 64); // about 8.1 su per RPM
                    near(h, "stress (su/RPM)", d.pgdynamo$getImpact(), impact, impact * 0.05);
                })
                .thenSucceed();
    }

    /** A 12 V winding at 256 RPM: 12 V, and the same 652 W into a matched load as the stock winding. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void lowVoltageWindingSamePower(GameTestHelper h) {
        double windingOhms = STOCK_OHMS * Math.pow(12 / STOCK_VOLTS, 2); // 0.055 ohm
        IDynamoMotor d = setUp(h, 256, DynamoMode.GEN_12);
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> wireLoad(h, windingOhms))
                .thenIdle(RUN)
                .thenExecute(() -> {
                    near(h, "rated volts", Math.abs(d.pgdynamo$getEmf()), 12, 0.12);
                    double amps = 12 / (2 * windingOhms);                     // about 109 A
                    double measured = generatedAmps(h, d);
                    near(h, "current", measured, amps, amps * 0.03);
                    double loadWatts = measured * measured * windingOhms;
                    double expected = STOCK_VOLTS * STOCK_VOLTS / (4 * STOCK_OHMS); // 652 W
                    near(h, "watts into the load", loadWatts, expected, expected * 0.05);
                })
                .thenSucceed();
    }

    /** Reversing the shaft reverses the polarity. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void polarityFollowsDirection(GameTestHelper h) {
        IDynamoMotor d = setUp(h, 64, DynamoMode.GEN_STOCK);
        double[] forward = new double[1];
        h.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    forward[0] = d.pgdynamo$getEmf();
                    CreativeMotorBlockEntity driver = (CreativeMotorBlockEntity) h.getBlockEntity(DRIVER);
                    driver.generatedSpeed.setValue(-64);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    double reverse = d.pgdynamo$getEmf();
                    if (Math.abs(forward[0]) < 1 || Math.signum(reverse) == Math.signum(forward[0]))
                        h.fail(String.format(Locale.ROOT, "polarity did not flip: %.2f V, then %.2f V", forward[0], reverse));
                    near(h, "reversed volts", Math.abs(reverse), Math.abs(forward[0]), 0.5);
                })
                .thenSucceed();
    }

    /** With only 2,000 su free, it takes no more than that and the voltage sags. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void governorRespectsFreeStress(GameTestHelper h) {
        IDynamoMotor d = setUp(h, 256, DynamoMode.GEN_STOCK);
        d.pgdynamo$setTestHeadroom(2000f);
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> wireLoad(h, STOCK_OHMS))
                .thenIdle(200)
                .thenExecute(() -> {
                    float usedSu = d.pgdynamo$getImpact() * 256;
                    if (usedSu > 2000f)
                        h.fail(String.format(Locale.ROOT, "took %.0f su with only 2000 su free", usedSu));
                    if (usedSu < 2000f * 0.85f)
                        h.fail(String.format(Locale.ROOT, "took only %.0f su of the 2000 su free", usedSu));
                    if (Math.abs(d.pgdynamo$getEmf()) > STOCK_VOLTS * 0.7)
                        h.fail(String.format(Locale.ROOT, "voltage did not sag: %.1f V", d.pgdynamo$getEmf()));
                })
                .thenSucceed();
    }

    /** Take the driver away while loaded: the dynamo must stop, not keep itself turning. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void stopsWhenTheShaftStops(GameTestHelper h) {
        IDynamoMotor d = setUp(h, 64, DynamoMode.GEN_STOCK);
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> wireLoad(h, STOCK_OHMS))
                .thenIdle(60)
                .thenExecute(() -> {
                    if (generatedAmps(h, d) < 0.5)
                        h.fail(String.format(Locale.ROOT, "was not generating before the stop: %.3f A", d.pgdynamo$getOutputCurrent()));
                    h.setBlock(DRIVER, Blocks.AIR.defaultBlockState());
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    near(h, "shaft speed after the stop", shaftSpeed(h), 0, 0);
                    near(h, "volts after the stop", d.pgdynamo$getEmf(), 0, 0.01);
                    near(h, "motor's own speed value", d.pgdynamo$getMotorGeneratedSpeed(), 0, 0);
                })
                .thenSucceed();
    }

    /**
     * Save a 30 V dynamo and load it into a fresh motor, the way a chunk load does (PowerGrid builds the
     * circuit before the saved mode is read). It must come back as a working 30 V generator.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void reloadKeepsGenerator(GameTestHelper h) {
        setUp(h, 64, DynamoMode.GEN_30);
        double windingOhms = STOCK_OHMS * Math.pow(30 / STOCK_VOLTS, 2); // 0.345 ohm
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    BlockEntity old = h.getBlockEntity(DYNAMO);
                    CompoundTag saved = old.saveWithoutMetadata();
                    h.setBlock(DYNAMO, Blocks.AIR.defaultBlockState());
                    h.setBlock(DYNAMO, ModdedBlocks.ELECTRIC_MOTOR.get().defaultBlockState().setValue(ElectricMotorBlock.FACING, Direction.WEST));
                    BlockEntity fresh = h.getBlockEntity(DYNAMO);
                    fresh.load(saved);
                })
                .thenIdle(SETTLE)
                .thenExecute(() -> wireLoad(h, windingOhms))
                .thenIdle(RUN)
                .thenExecute(() -> {
                    BlockEntity be = h.getBlockEntity(DYNAMO);
                    IDynamoMotor d = (IDynamoMotor) be;
                    if (d.pgdynamo$getMode() != DynamoMode.GEN_30)
                        h.fail("mode after reload: " + d.pgdynamo$getMode());
                    double emf = 30.0 * 64 / 256; // 7.5 V
                    near(h, "volts after reload", Math.abs(d.pgdynamo$getEmf()), emf, emf * 0.01);
                    double amps = emf / (2 * windingOhms);
                    near(h, "current after reload", generatedAmps(h, d), amps, amps * 0.03);
                })
                .thenSucceed();
    }

    /** Motor mode is PowerGrid's own behaviour: 129.2 V must drive the motor to 64 RPM (RPM = V^2 / (R x T) x 30 pi). */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void motorModeStillDrives(GameTestHelper h) {
        BlockPos sourcePos = new BlockPos(2, 2, 4);
        h.setBlock(DYNAMO, ModdedBlocks.ELECTRIC_MOTOR.get().defaultBlockState().setValue(ElectricMotorBlock.FACING, Direction.WEST));
        h.setBlock(sourcePos, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get().defaultBlockState());
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    BlockPos motor = h.absolutePos(DYNAMO);
                    BlockPos source = h.absolutePos(sourcePos);
                    GlobalElectricNetworks.makeSimpleConnection(h.getLevel(), new BlockWireEndpoint(source, 0), new BlockWireEndpoint(motor, 0), 0.001f);
                    GlobalElectricNetworks.makeSimpleConnection(h.getLevel(), new BlockWireEndpoint(source, 1), new BlockWireEndpoint(motor, 1), 0.001f);
                    CreativeSourceBlockEntity src = (CreativeSourceBlockEntity) h.getBlockEntity(sourcePos);
                    src.setValue((float) (STOCK_VOLTS / 2));
                })
                .thenIdle(RUN)
                .thenExecute(() -> near(h, "motor speed on 129.2 V", Math.abs(shaftSpeed(h)), 64, 2))
                .thenSucceed();
    }

    /** Back to Motor mode with the load still wired: no generator, no stress, and it just turns along. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void switchBackToMotor(GameTestHelper h) {
        IDynamoMotor d = setUp(h, 64, DynamoMode.GEN_STOCK);
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> wireLoad(h, STOCK_OHMS))
                .thenIdle(40)
                .thenExecute(() -> d.pgdynamo$setMode(DynamoMode.MOTOR))
                .thenIdle(40)
                .thenExecute(() -> {
                    near(h, "volts in Motor mode", d.pgdynamo$getEmf(), 0, 0);
                    near(h, "stress in Motor mode", d.pgdynamo$getImpact(), 0, 0);
                    near(h, "shaft speed", Math.abs(shaftSpeed(h)), 64, 0.01);
                })
                .thenSucceed();
    }
}
