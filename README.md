# Power Grid: Dynamo

An unofficial add-on for [Create: Power Grid](https://www.curseforge.com/minecraft/mc-mods/power-grid). It lets Power Grid's Electric Motor run as a generator, like a real permanent-magnet machine.

Minecraft 1.21.1 · NeoForge · Create 6.0.x · Create: Power Grid 0.6.2 · mod id `pgdynamo`

## Using it

- Every Electric Motor gets a **Mode** setting in the value box on top of it:
  - **Motor**: unchanged Power Grid behaviour. This is the default, so existing motors don't change.
  - **Generator** with a **12 / 24 / 30 / 60 / 120 V** or **stock (258 V)** winding. The number is what it makes at 256 RPM; slower shafts give proportionally less.
- Turn its shaft and wire loads to its terminals. Red is + when the shaft turns the way + on red would drive the motor. Reverse the shaft and the polarity flips.
- **Every winding has the same power:** up to 652 W into a matched load at 256 RPM. Low windings mean big currents (12 V gives about 109 A into a matched load), so use thick wire.
- **Shaft load:** drawing current takes stress from the shaft (the electrical power plus 5% friction), using the same physics as Power Grid's induction generator. It never takes more stress than the shaft network has free; the voltage sags instead.
- In Generator mode it never drives the shaft.
- **On a battery bus, put a diode between the dynamo's + and the batteries.** A Power Grid circuit-board diode carries up to about 12 A. A stopped dynamo is just its winding, so without a diode it drains the batteries as heat. Real DC dynamos needed a cut-out for the same reason.
- **Goggles** show the winding and the output (volts, amps, watts).

## Requirements

- Minecraft 1.21.1 and NeoForge 21.1.
- Create 6.0.x.
- Create: Power Grid **0.6.2 exactly**. The add-on changes Power Grid's motor from the inside, so each Power Grid update needs a matching Dynamo release.
- **Multiplayer:** install it on the server and on every client, because the Mode box and the goggles readout are client-side. Players without it can still join, but they can't set a dynamo.

## Building

You need JDK 21. The jars it compiles against aren't in this repository; copy them into `libs/`:

- `powergrid-mc1.21.1-0.6.2.jar`
- `create-1.21.1-6.0.10.jar`
- From inside the Create jar's `META-INF/jarjar/` folder: `flywheel-neoforge-1.21.1-1.0.6.jar`, `ponder-neoforge-1.0.82+mc1.21.1.jar` and `Registrate-MC1.21-1.3.0+67.jar`

Then run `./gradlew build`. The jar lands in `build/libs/`.

**Tests:** put Create, Power Grid and Architectury into `run/gametest/mods/`, then run `./gradlew runGameTestServer`. Nine tests run Power Grid's real circuit solver on Create's real shaft network:
- open-circuit voltage
- matched-load current and stress
- a 12 V winding giving the same 652 W
- polarity flip
- the stress governor
- stopping when the shaft stops
- save and reload
- Motor mode unchanged
- switching back to Motor

If Gradle fails on Windows with "Unable to establish loopback connection", create a `build/uds` folder in the project, then set `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<project folder>/build/uds`.

## Credits

- [Create: Power Grid](https://www.curseforge.com/minecraft/mc-mods/power-grid) by patryk3211 (Apache-2.0). This add-on changes its Electric Motor through a mixin; no Power Grid code is included.
- [Create](https://www.curseforge.com/minecraft/mc-mods/create) by the Create team.

## License

Apache-2.0. See [LICENSE](LICENSE). Copyright 2026 Hareapex.
