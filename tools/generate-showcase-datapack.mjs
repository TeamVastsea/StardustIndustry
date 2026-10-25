#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createHash } from "node:crypto";

const PACK_NAME = "stardust-showcase";
const NAMESPACE = "stardust_showcase";
const SCOREBOARD = "sd_showcase";
const DEMO_ORIGIN = "0 64 0";
const FORBIDDEN_PERSISTENCE = ["Cables", "data merge block"];

function usage() {
  console.error([
    "Usage:",
    "  node tools/generate-showcase-datapack.mjs <world-directory>",
    "  node tools/generate-showcase-datapack.mjs --check",
  ].join("\n"));
}

function writeText(file, contents) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, contents.endsWith("\n") ? contents : `${contents}\n`, "utf8");
}

function commandFunctions(version) {
  return {
    "load.mcfunction": [
      `scoreboard objectives add ${SCOREBOARD} dummy`,
      `schedule function ${NAMESPACE}:bootstrap 1s replace`,
    ],
    "bootstrap.mcfunction": [
      `execute unless entity @a run schedule function ${NAMESPACE}:bootstrap 1s replace`,
      `execute if entity @a unless score #built ${SCOREBOARD} matches ${version} run function ${NAMESPACE}:build`,
    ],
    "build.mcfunction": [
      "# The mod owns all placement, terminal and cable persistence.",
      `execute store success score #last_command ${SCOREBOARD} run stardustindustry demo build`,
      `execute if score #last_command ${SCOREBOARD} matches 1 run scoreboard players set #built ${SCOREBOARD} ${version}`,
      `execute unless score #last_command ${SCOREBOARD} matches 1 run tellraw @a {\"text\":\"Stardust Industry demo build failed; check latest.log.\",\"color\":\"red\"}`,
    ],
    "reset.mcfunction": [
      `execute store success score #last_command ${SCOREBOARD} run stardustindustry demo reset`,
      `execute if score #last_command ${SCOREBOARD} matches 1 run scoreboard players reset #built ${SCOREBOARD}`,
    ],
    "fault_overload.mcfunction": ["stardustindustry demo fault overload"],
    "fault_phase_loss.mcfunction": ["stardustindustry demo fault phase_loss"],
    "fault_overvoltage.mcfunction": ["stardustindustry demo fault overvoltage"],
    "fault_reset.mcfunction": ["stardustindustry demo fault reset"],
  };
}

function calculateVersion(functions) {
  const contract = Object.entries(functions)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, lines]) => `${name}\n${lines.join("\n")}`)
    .join("\n");
  return Number.parseInt(
    createHash("sha256").update(contract).digest("hex").slice(0, 7),
    16,
  );
}

function generate(worldDirectory) {
  const world = path.resolve(worldDirectory);
  if (!fs.existsSync(path.join(world, "level.dat"))) {
    throw new Error(`Not a Minecraft world (level.dat is missing): ${world}`);
  }

  const packDirectory = path.join(world, "datapacks", PACK_NAME);
  const functionDirectory = path.join(packDirectory, "data", NAMESPACE, "function");
  const functionTagDirectory = path.join(packDirectory, "data", "minecraft", "tags", "function");

  // Derive a stable content version without making the version itself part of its hash.
  const draft = commandFunctions(0);
  const version = calculateVersion(draft);
  const functions = commandFunctions(version);

  fs.rmSync(packDirectory, { recursive: true, force: true });
  writeText(path.join(packDirectory, "pack.mcmeta"), JSON.stringify({
    pack: {
      pack_format: 48,
      description: "Stardust Industry executable power demo launcher",
    },
  }, null, 2));
  writeText(path.join(functionTagDirectory, "load.json"), JSON.stringify({
    values: [`${NAMESPACE}:load`],
  }, null, 2));
  for (const [name, lines] of Object.entries(functions)) {
    writeText(path.join(functionDirectory, name), lines.join("\n"));
  }
  writeText(path.join(packDirectory, "README.txt"), [
    "Stardust Industry executable power demo",
    "",
    `Fixed origin: ${DEMO_ORIGIN}`,
    "This datapack is only a launcher. The mod command owns world edits and cable persistence.",
    "",
    "Functions:",
    `  /function ${NAMESPACE}:build`,
    `  /function ${NAMESPACE}:reset`,
    `  /function ${NAMESPACE}:fault_overload`,
    `  /function ${NAMESPACE}:fault_phase_loss`,
    `  /function ${NAMESPACE}:fault_overvoltage`,
    `  /function ${NAMESPACE}:fault_reset`,
  ].join("\n"));

  validate(packDirectory, version);
  return { packDirectory, version };
}

function validate(packDirectory, version) {
  const functionDirectory = path.join(packDirectory, "data", NAMESPACE, "function");
  const files = fs.readdirSync(functionDirectory)
    .filter((name) => name.endsWith(".mcfunction"))
    .sort();
  const expected = Object.keys(commandFunctions(version)).sort();
  if (JSON.stringify(files) !== JSON.stringify(expected)) {
    throw new Error(`Generated function set differs from the command contract: ${files.join(", ")}`);
  }

  const allFunctions = files
    .map((name) => fs.readFileSync(path.join(functionDirectory, name), "utf8"))
    .join("\n");
  for (const forbidden of FORBIDDEN_PERSISTENCE) {
    if (allFunctions.includes(forbidden)) {
      throw new Error(`Generated datapack writes internal persistence directly: ${forbidden}`);
    }
  }
  for (const required of [
    "stardustindustry demo build",
    "stardustindustry demo reset",
    "stardustindustry demo fault overload",
    "stardustindustry demo fault phase_loss",
    "stardustindustry demo fault overvoltage",
    "stardustindustry demo fault reset",
  ]) {
    if (!allFunctions.includes(required)) {
      throw new Error(`Generated datapack is missing command entry: ${required}`);
    }
  }
}

function selfCheck() {
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), "stardust-showcase-check-"));
  try {
    fs.writeFileSync(path.join(temporaryRoot, "level.dat"), "self-check", "utf8");
    const first = generate(temporaryRoot);
    const snapshot = fs.readFileSync(
      path.join(first.packDirectory, "data", NAMESPACE, "function", "build.mcfunction"),
      "utf8",
    );
    const second = generate(temporaryRoot);
    const regenerated = fs.readFileSync(
      path.join(second.packDirectory, "data", NAMESPACE, "function", "build.mcfunction"),
      "utf8",
    );
    if (first.version !== second.version || snapshot !== regenerated) {
      throw new Error("Showcase datapack generation is not deterministic");
    }
    console.log(`Self-check passed (version ${first.version}); command-only datapack is deterministic.`);
  } finally {
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
  }
}

const argument = process.argv[2];
try {
  if (argument === "--check") {
    selfCheck();
  } else if (!argument || argument === "--help" || argument === "-h") {
    usage();
    process.exit(argument ? 0 : 2);
  } else {
    const result = generate(argument);
    console.log(`Generated ${result.packDirectory}`);
    console.log(`Validated command-only demo launcher version ${result.version}.`);
    console.log(`The mod command builds the idempotent demo at ${DEMO_ORIGIN}.`);
  }
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
