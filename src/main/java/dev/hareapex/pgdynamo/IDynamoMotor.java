package dev.hareapex.pgdynamo;

/** Added to PowerGrid's Electric Motor by the mixin. Only the game tests use it. */
public interface IDynamoMotor {
    DynamoMode pgdynamo$getMode();

    void pgdynamo$setMode(DynamoMode mode);

    /** Generator voltage right now (signed by rotation direction); 0 in Motor mode. */
    double pgdynamo$getEmf();

    /** Current out of the + terminal; positive while generating. */
    double pgdynamo$getOutputCurrent();

    /** Stress the dynamo puts on its shaft network, in su per RPM. */
    float pgdynamo$getImpact();

    /** PowerGrid's own speed value inside the motor. It must stay 0 in Generator mode. */
    float pgdynamo$getMotorGeneratedSpeed();

    /** Test only: pretend the shaft network has this much free stress (su; negative = off). */
    void pgdynamo$setTestHeadroom(float su);
}
