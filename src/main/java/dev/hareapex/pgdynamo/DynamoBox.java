package dev.hareapex.pgdynamo;

import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlock;

/**
 * Where the Mode setting sits: the middle of the motor's top, the same spot PowerGrid uses for the
 * Constant Speed Motor's speed box. The wire terminals are at the back of the top, clear of it.
 */
public class DynamoBox extends CenteredSideValueBoxTransform {
    public DynamoBox() {
        super((state, dir) -> {
            Direction facing = state.getValue(ElectricMotorBlock.FACING);
            return facing.getAxis() == Direction.Axis.Y ? dir.getAxis() == Direction.Axis.Z : dir == Direction.UP;
        });
    }

    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(8.0, 8.0, 12.5);
    }
}
