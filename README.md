Stardust Industry
=======

Stardust Industry is a scientific industrial expansion for Minecraft 1.21.1 on
NeoForge. It builds on the ore and metal foundation provided by the
**Stardust Industry: Metal Addon** (`stardustindustrymetaladdon`) and adds the
machinery, processing chains and power systems on top of it.

The two mods are developed as independent projects that are loaded side by side
in the same development environment. Stardust Industry does **not** declare a
hard dependency on the addon, so either mod can be used on its own.

## Requirements

- JDK 21 (the Gradle toolchain will fetch one automatically if needed)
- Minecraft 1.21.1 / NeoForge 21.1.x

## Development

```bash
./gradlew build       # compile and assemble the mod
./gradlew runClient   # launch a development client
./gradlew runServer   # launch a development server
./gradlew runData     # run the data generators
```

Development environment state is kept out of the NAS reparse-point tree:
Gradle's `buildDir` points at the local temporary directory while all source
files stay in the project folder.

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
| L7 compatibility | Create and AE2 integration |

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
