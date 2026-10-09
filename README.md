Stardust Industry
=======

Stardust Industry is a scientific industrial expansion for Minecraft 1.21.1 on
NeoForge. The repository is a Gradle multi-project build: the gameplay core and
each third-party integration are shipped as separate mods. It builds on the ore
and metal foundation provided by the
**Stardust Industry: Metal Addon** (`stardustindustrymetaladdon`) and adds the
machinery, processing chains and power systems on top of it.

This suite and the Metal Addon are developed as independent projects that can be
loaded side by side in the same development environment. Stardust Industry Core
does **not** declare a hard dependency on the addon, so either project can be
used on its own.

## Requirements

- JDK 21 (the Gradle toolchain will fetch one automatically if needed)
- Minecraft 1.21.1 / NeoForge 21.1.x

## Development

```bash
./gradlew build       # compile all modules and collect all four jars in build/libs
./gradlew runClient   # build the extensions and launch one client with all modules
./gradlew runServer   # launch the Core development server
./gradlew runData     # run the Core data generators
```

Development environment state is kept out of the NAS reparse-point tree:
each module's `buildDir` points at the local temporary directory while all
source files stay in the project folder. The root build copies release jars to
`build/libs/`.

## Modules

| Project | Mod id | Dependencies | Contents |
|---|---|---|---|
| `StardustIndustry-Core` | `stardustindustry` | Minecraft, NeoForge | Original gameplay systems, machines, recipes, resources and rendering |
| `StardustIndustry-UtilsEx` | `stardustindustry_utilsex` | Core required; JEI, Jade, WTHIT and TOP optional | Recipe-viewer and block-information integrations |
| `StardustIndustry-MekanismEx` | `stardustindustry_mekanismex` | Core and Mekanism required | Mekanism chemical registry and gas-port bridge |
| `StardustIndustry-AE2Ex` | `stardustindustry_ae2ex` | Core and AE2 required | Independent AE2 extension entry point; integration features are not implemented yet |

Install Core in every pack. Install an extension only when its required external
mod is present. UtilsEx is safe without any of its optional integrations; it
activates each plugin only when the corresponding mod is installed.

The root build produces these independent artifacts:

```text
build/libs/stardustindustry-core-0.1.0.jar
build/libs/stardustindustry-utilsex-0.1.0.jar
build/libs/stardustindustry-mekanismex-0.1.0.jar
build/libs/stardustindustry-ae2ex-0.1.0.jar
```

## Documentation

Start with the [documentation index](docs/README.md). The
[module architecture](docs/module-architecture.md) defines dependency and code
ownership rules; the [development guide](docs/dev-environment.md) contains the
reproducible build, run and release workflow.

## Architecture

The mod is built bottom-up in layers. The foundation (L0) is complete and every
later feature is expressed through it.

| Layer | Contents |
|---|---|
| L0 framework | multiblock API, machine base, modules, resource abstraction, recipe engine |
| L1 networks | FE power grid, fluid/gas pipes, heat |
| L2 beneficiation | crushing, grinding, screening, flotation, magnetic separation |
| L3 metallurgy | roasting, smelting, converting, hydrometallurgy, electrolysis |
| L4 chemical | reactors, distillation, gas handling |
| L5 power generation | thermal, combined cycle, HRSG, steam turbines |
| L6 nuclear | fuel cycle, reactor, coolant loops, reprocessing, safety |
| L7 compatibility | Optional integrations supplied by separate extension mods |

### L0 framework

- **`multiblock`** - declarative structure definitions authored in a Java DSL
  (`StructureDefinition.builder(...)`), validated by `StructureMatcher`, and
  turned into ghost-block overlays by `StructureProjector`. Definitions may be
  overridden by datapack JSON of the same id.
- **`machine`** - machines are composed from `MachineModule`s rather than
  subclassed per machine. `MachineBlockEntity` owns a `ModuleHost` and a
  structure; `MachineTier` bundles voltage, speed and parallel count.
- **`capability`** - `ResourceType` and `ResourceStack` give the recipe engine a
  type-erased view over items, fluids, gases, energy and heat. Ports still speak
  native NeoForge capabilities so the mod interoperates with the FE ecosystem.
- **`energy`** - `EnergyTier` defines the LV/MV/HV/EHV voltage ladder used by
  machines, cables and transformers.
- **`recipe`** - `ProcessingRecipe` is the shared single-input processing
  recipe, with probabilistic by-products and time/energy costs. The codec
  accepts both a singular `result` and a plural `results` so the addon's
  generated compat recipes parse unchanged.

### Materials

Materials are referenced by **common `c:` tags** (`c:raw_materials/iron`,
`c:ingots/steel`, ...), not by direct item ids. The metal addon happens to
provide a rich set of those tags, but this mod runs without it.

### Loading the addon alongside this mod

The addon is a separate Gradle build. To test both together, build the addon and
drop its jar into this project's development `run` directory:

```bash
cd ../StardustIndustryMetalAddon && ./gradlew build
cp build/libs/stardustindustrymetaladdon-*.jar \
   ../StardustIndustry/run/client/mods/
```

## License

All Rights Reserved unless stated otherwise.
