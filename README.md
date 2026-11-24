# Power Grid: Dynamo

An unofficial add-on for [Create: Power Grid](https://www.curseforge.com/minecraft/mc-mods/power-grid). It lets Power Grid's Electric Motor run as a generator, like a real permanent-magnet machine.

Minecraft 1.20.1 · Forge · Create 6.0.x · Create: Power Grid 0.6.2 · mod id `pgdynamo`

This is the **Forge 1.20.1** port (branch `forge-1.20.1`). The NeoForge 1.21.1 version is on `main`. Both versions have the same modes, windings and numbers.

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

- Minecraft 1.20.1 and Forge 47.
- Create 6.0.7 or newer 6.0.x (Power Grid's own minimum).
- Create: Power Grid **0.6.2 exactly**, the Forge 1.20.1 build (`powergrid-mc1.20.1-0.6.2.jar`), and Architectury API, which Power Grid needs. The add-on changes Power Grid's motor from the inside, so each Power Grid update needs a matching Dynamo release.
- **Multiplayer:** install it on the server and on every client, because the Mode box and the goggles readout are client-side. Players without it can still join, but they can't set a dynamo.

## Building

You need JDK 21; the build compiles with `--release 17`, so the classes run on Java 17. Gradle downloads Create, Ponder, Flywheel, Registrate, Architectury API and Power Grid from their Maven repositories and remaps them to the dev names (ModDevGradle Legacy).

Run `./gradlew build`. The jar to install lands in `build/libs/`; it's reobfuscated to SRG names, as Forge 1.20.1 needs.

**Tests:** run `./gradlew runGameTestServer`. The game runs on Java 17 there, so Gradle needs a Java 17 runtime: list its folder in `org.gradle.java.installations.paths` in `gradle.properties`. Nine tests run Power Grid's real circuit solver on Create's real shaft network:
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
