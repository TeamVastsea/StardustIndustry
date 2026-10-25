#!/usr/bin/env node

import fs from "node:fs";
import { createHash } from "node:crypto";
import path from "node:path";

const worldArgument = process.argv[2];
if (!worldArgument || worldArgument === "--help" || worldArgument === "-h") {
  console.error("Usage: node tools/generate-showcase-datapack.mjs <world-directory>");
  process.exit(worldArgument ? 0 : 2);
}

const worldDirectory = path.resolve(worldArgument);
if (!fs.existsSync(path.join(worldDirectory, "level.dat"))) {
  console.error(`Not a Minecraft world (level.dat is missing): ${worldDirectory}`);
  process.exit(2);
}

const packDirectory = path.join(worldDirectory, "datapacks", "stardust-showcase");
const functionDirectory = path.join(packDirectory, "data", "stardust_showcase", "function");
const functionTagDirectory = path.join(packDirectory, "data", "minecraft", "tags", "function");
const functionPath = path.join(functionDirectory, "build.mcfunction");

const X_MASK = (1n << 26n) - 1n;
const Y_MASK = (1n << 12n) - 1n;
const UINT64 = 1n << 64n;
const INT64_SIGN = 1n << 63n;

function signed(value, bits) {
  const sign = 1n << BigInt(bits - 1);
  const modulus = 1n << BigInt(bits);
  return Number(value >= sign ? value - modulus : value);
}

// Matches net.minecraft.core.BlockPos#asLong for Minecraft 1.21.1.
function blockPosLong(x, y, z) {
  let packed = ((BigInt(x) & X_MASK) << 38n)
    | ((BigInt(z) & X_MASK) << 12n)
    | (BigInt(y) & Y_MASK);
  if (packed >= INT64_SIGN) packed -= UINT64;
  return packed;
}

function unpackBlockPos(value) {
  const unsigned = value < 0n ? value + UINT64 : value;
  return {
    x: signed((unsigned >> 38n) & X_MASK, 26),
    y: signed(unsigned & Y_MASK, 12),
    z: signed((unsigned >> 12n) & X_MASK, 26),
  };
}

function assertBlockPosRoundTrip(pos) {
  const packed = blockPosLong(pos.x, pos.y, pos.z);
  const unpacked = unpackBlockPos(packed);
  if (unpacked.x !== pos.x || unpacked.y !== pos.y || unpacked.z !== pos.z) {
    throw new Error(`BlockPos long round-trip failed: ${JSON.stringify(pos)} -> ${packed} -> ${JSON.stringify(unpacked)}`);
  }
  return packed;
}

const commands = [];
const racks = new Map();
const cableRuns = new Set();
const tierCells = { lv: 16, mv: 4, hv: 1, ehv: 1 };
const validFacings = new Set(["north", "south", "east", "west", "up", "down"]);
const materials = {
  "50": "copper",
  "150": "copper",
  "500": "copper",
  "800": "acsr",
  "1500": "copper",
  "1600": "acsr",
  "5000": "copper",
};
const validInsulation = new Set(["bare", "class_1", "class_2"]);
const RED = 0xB02E26;
const YELLOW = 0xFED83D;
const BLUE = 0x3C44AA;
const BLACK = 0x1D1D21;
const UNDYED = -1;
const tierDisplay = {
  lv: { pedestal: "minecraft:cyan_concrete", text: "aqua" },
  mv: { pedestal: "minecraft:yellow_concrete", text: "yellow" },
  hv: { pedestal: "minecraft:red_concrete", text: "red" },
  ehv: { pedestal: "minecraft:purple_concrete", text: "light_purple" },
};

function command(value = "") {
  commands.push(value);
}

function rackKey(x, y, z) {
  return `${x},${y},${z}`;
}

function addRack(x, y, z, tier, facing, cells) {
  if (!(tier in tierCells)) throw new Error(`Unknown terminal tier: ${tier}`);
  if (!validFacings.has(facing)) throw new Error(`Unknown terminal facing: ${facing}`);
  const uniqueCells = [...new Set(cells)].sort((a, b) => a - b);
  for (const cell of uniqueCells) {
    if (!Number.isInteger(cell) || cell < 0 || cell >= tierCells[tier]) {
      throw new Error(`Cell ${cell} is outside the ${tier} terminal grid`);
    }
  }
  const key = rackKey(x, y, z);
  if (racks.has(key)) throw new Error(`Duplicate terminal rack at ${key}`);
  const rack = { x, y, z, tier, facing, cells: uniqueCells, cables: [] };
  assertBlockPosRoundTrip(rack);
  racks.set(key, rack);
  return rack;
}

function endpoint(rack, cell) {
  if (!rack.cells.includes(cell)) {
    throw new Error(`Rack ${rackKey(rack.x, rack.y, rack.z)} has no terminal in cell ${cell}`);
  }
  return { rack, cell };
}

function addCable(a, b, ampacity, insulation, color = UNDYED) {
  if (!(ampacity in materials)) throw new Error(`Unknown cable ampacity: ${ampacity}`);
  if (!validInsulation.has(insulation)) throw new Error(`Unknown cable insulation: ${insulation}`);
  if (materials[ampacity] === "acsr" && insulation !== "bare") {
    throw new Error(`ACSR ${ampacity} A is bare-only`);
  }
  if (a.rack === b.rack && a.cell === b.cell) throw new Error("A cable cannot join a terminal to itself");
  const dx = a.rack.x - b.rack.x;
  const dy = a.rack.y - b.rack.y;
  const dz = a.rack.z - b.rack.z;
  if ((dx * dx) + (dy * dy) + (dz * dz) > 64 * 64) {
    throw new Error("Cable exceeds the server's 64-block span limit");
  }
  const ends = [
    `${rackKey(a.rack.x, a.rack.y, a.rack.z)}:${a.cell}`,
    `${rackKey(b.rack.x, b.rack.y, b.rack.z)}:${b.cell}`,
  ].sort();
  const runKey = `${ampacity}/${insulation}/${ends.join("/")}`;
  if (cableRuns.has(runKey)) throw new Error(`Duplicate cable run: ${runKey}`);
  cableRuns.add(runKey);

  const aFarPos = assertBlockPosRoundTrip(b.rack);
  const bFarPos = assertBlockPosRoundTrip(a.rack);
  a.rack.cables.push({
    cell: a.cell, ampacity, insulation, color, farPos: aFarPos, farCell: b.cell,
  });
  b.rack.cables.push({
    cell: b.cell, ampacity, insulation, color, farPos: bFarPos, farCell: a.cell,
  });
}

function component(text, color = "white", bold = false) {
  return JSON.stringify({ text, color, bold, italic: false }).replaceAll("'", "\\'");
}

function label(x, y, z, text, options = {}) {
  const scale = options.scale ?? 0.65;
  const color = options.color ?? "white";
  const bold = options.bold ?? false;
  const width = options.width ?? 240;
  const transformation = `translation:[0.0f,0.0f,0.0f],left_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[${scale}f,${scale}f,${scale}f],right_rotation:[0.0f,0.0f,0.0f,1.0f]`;
  command(`summon minecraft:text_display ${x} ${y} ${z} {Tags:["stardust_showcase"],text:'${component(text, color, bold)}',billboard:"center",alignment:"center",background:0,shadow:1b,see_through:0b,line_width:${width},view_range:1.5f,brightness:{block:15,sky:15},transformation:{${transformation}}}`);
}

function device(x, z, id, text, tier, powered = false) {
  const pedestal = tier === "neutral"
    ? "minecraft:gray_concrete"
    : tierDisplay[tier].pedestal;
  command(`setblock ${x} 63 ${z} ${pedestal}`);
  const nbt = powered
    ? '{SwitchClosed:1b,StepUp:1b,FeToNative:1b,CreativeTier:"lv",CreativeWiring:"three_phase_4_wire",GeneratorWiring:"single_phase_2_wire"}'
    : "";
  command(`setblock ${x} 64 ${z} stardustindustry:${id}[facing=south,powered=${powered}]${nbt}`);
  label(x + 0.5, 66.2, z + 0.5, text, { scale: 0.48, width: 190 });
}

function energyPort(x, z, tier) {
  command(`setblock ${x} 63 ${z} ${tierDisplay[tier].pedestal}`);
  command(`setblock ${x} 64 ${z} stardustindustry:${tier}_energy_port`);
  const volts = { lv: "380 V", mv: "10 kV", hv: "110 kV", ehv: "500 kV" }[tier];
  label(x + 0.5, 66.2, z + 0.5, `${tier.toUpperCase()} 能量端口 · ${volts}`, { scale: 0.48, width: 190 });
}

function station({ tier, size, x, z, cells, capacity, rating }) {
  const y = 64;
  const x2 = x + size - 1;
  const y2 = y + size - 1;
  const z2 = z + size - 1;
  const middleX = Math.floor((x + x2) / 2);
  const middleZ = Math.floor((z + z2) / 2);

  command(`fill ${x} ${y} ${z} ${x2} ${y2} ${z2} stardustindustry:storage_station_frame hollow`);
  command(`fill ${x + 1} ${y} ${z + 1} ${x2 - 1} ${y} ${z2 - 1} stardustindustry:storage_station_casing`);
  command(`fill ${x + 1} ${y2} ${z + 1} ${x2 - 1} ${y2} ${z2 - 1} stardustindustry:storage_station_casing`);
  command(`fill ${x} ${y + 1} ${z + 1} ${x} ${y2 - 1} ${z2 - 1} stardustindustry:storage_station_casing`);
  command(`fill ${x2} ${y + 1} ${z + 1} ${x2} ${y2 - 1} ${z2 - 1} stardustindustry:storage_station_casing`);
  command(`fill ${x + 1} ${y + 1} ${z} ${x2 - 1} ${y2 - 1} ${z} stardustindustry:storage_station_casing`);
  command(`fill ${x + 1} ${y + 1} ${z2} ${x2 - 1} ${y2 - 1} ${z2} stardustindustry:industrial_glass`);
  command(`fill ${x + 1} ${y + 1} ${z + 1} ${x2 - 1} ${y2 - 1} ${z2 - 1} stardustindustry:${tier}_storage_cell`);
  command(`setblock ${x2} ${y + 1} ${middleZ} stardustindustry:${tier}_energy_port`);
  command(`setblock ${middleX} ${y + 1} ${z2} stardustindustry:${tier}_storage_station_controller[facing=south]`);

  const portCells = tier === "lv" ? [0, 1] : tier === "mv" ? [0, 1, 2] : [0];
  addRack(x2 + 1, y + 1, middleZ, tier, "west", portCells);
  label(middleX + 0.5, y2 + 1.4, z2 + 0.8,
    `${tier.toUpperCase()} ${size}×${size}×${size} · ${cells} 单元 · ${capacity}\\n${rating}`,
    { scale: 0.58, color: "gold", bold: true, width: 260 });
}

// Build state: bounded cleanup, stable lighting and a path that remains after reruns.
command("# Stardust Industry visual showcase; generated file, do not edit by hand.");
command("gamemode creative @s");
command("tp @s 26.5 76 30.5 180 35");
command("kill @e[type=minecraft:text_display,tag=stardust_showcase]");
command("fill -10 64 -6 28 80 30 minecraft:air");
command("fill -10 64 31 28 80 68 minecraft:air");
command("fill 29 64 -6 66 80 20 minecraft:air");
command("fill -10 63 -6 68 63 68 minecraft:smooth_stone");
command("fill 25 63 -4 27 63 66 minecraft:light_gray_concrete");
command("fill -8 63 60 27 63 62 minecraft:light_gray_concrete");
command("fill -8 63 42 27 63 44 minecraft:light_gray_concrete");
command("fill -8 63 22 27 63 24 minecraft:light_gray_concrete");
command("fill 27 63 10 66 63 12 minecraft:light_gray_concrete");
command("setblock 26 63 61 minecraft:gold_block");
command("setblock 26 63 43 minecraft:gold_block");
command("setblock 26 63 23 minecraft:gold_block");
command("setblock 30 63 11 minecraft:gold_block");
command();

// Complete electrical-device catalogue (open state).
device(-6, 58, "fuel_generator", "LV 燃料发电机 · 20 kW", "lv");
device(-2, 58, "creative_power_source", "创意电源 · LV/MV/HV/EHV", "neutral");
device(2, 58, "transformer_lv_mv", "LV / MV 变压器 · 24 kW", "mv");
device(6, 58, "transformer_mv_hv", "MV / HV 变压器 · 1.7 MW", "hv");
device(10, 58, "transformer_hv_ehv", "HV / EHV 变压器 · 38 MW", "ehv");

for (const [index, tier] of ["lv", "mv", "hv", "ehv"].entries()) {
  const x = -6 + (index * 4);
  const upper = tier.toUpperCase();
  const current = { lv: "150 A", mv: "500 A", hv: "1500 A", ehv: "5000 A" }[tier];
  device(x, 55, `${tier}_breaker`, `${upper} 断路器 · ${current}`, tier);
  device(x, 52, `${tier}_fuse_box`, `${upper} 保险丝盒 · ${current}`, tier);
  const converter = { lv: "38 kW", mv: "2.5 MW", hv: "55 MW", ehv: "500 MW" }[tier];
  device(x, 49, `${tier}_fe_converter`, `${upper} FE 转换器 · ${converter}`, tier);
  energyPort(x, 46, tier);
}

// One closed/powered example of every distinct shared device model family.
device(12, 52, "creative_power_source", "创意电源 · 合闸态", "neutral", true);
device(15, 52, "fuel_generator", "发电机 · 合闸态", "lv", true);
device(18, 52, "transformer_lv_mv", "变压器 · 合闸态", "mv", true);
device(21, 52, "lv_breaker", "断路器 · 合闸态", "lv", true);
device(24, 52, "lv_fuse_box", "保险丝盒 · 合闸态", "lv", true);
device(27, 52, "lv_fe_converter", "FE 转换器 · 合闸态", "lv", true);
command();

// Four terminal sizes on a vertical inspection wall.
command("fill -8 64 40 8 69 40 minecraft:polished_deepslate");
addRack(-6, 65, 41, "lv", "north", Array.from({ length: 16 }, (_, i) => i));
addRack(-2, 65, 41, "mv", "north", [0, 1, 2, 3]);
addRack(2, 65, 41, "hv", "north", [0]);
addRack(6, 65, 41, "ehv", "north", [0]);
label(-5.5, 69.2, 41.5, "LV 380 V · 4×4 · 16 端子", { scale: 0.52, color: tierDisplay.lv.text, bold: true });
label(-1.5, 69.2, 41.5, "MV 10 kV · 2×2 · 4 端子", { scale: 0.52, color: "yellow", bold: true });
label(2.5, 69.2, 41.5, "HV 110 kV · 1×1", { scale: 0.52, color: tierDisplay.hv.text, bold: true });
label(6.5, 69.2, 41.5, "EHV 500 kV · 1×1", { scale: 0.52, color: "light_purple", bold: true });
command();

// Real, two-sided cable records for all supported wiring arrangements and voltage tiers.
const lv2a = addRack(-6, 64, 24, "lv", "down", [0, 1]);
const lv2b = addRack(-6, 64, 34, "lv", "down", [0, 1]);
addCable(endpoint(lv2a, 0), endpoint(lv2b, 0), "150", "class_1", RED);
addCable(endpoint(lv2a, 1), endpoint(lv2b, 1), "150", "class_1", BLACK);

const mv3a = addRack(-2, 64, 24, "mv", "down", [0, 1, 2]);
const mv3b = addRack(-2, 64, 34, "mv", "down", [0, 1, 2]);
for (const [cell, color] of [RED, YELLOW, BLUE].entries()) {
  addCable(endpoint(mv3a, cell), endpoint(mv3b, cell), "500", "class_2", color);
}

const lv4a = addRack(2, 64, 24, "lv", "down", [0, 1, 2, 3]);
const lv4b = addRack(2, 64, 34, "lv", "down", [0, 1, 2, 3]);
for (const [cell, color] of [RED, YELLOW, BLUE, BLACK].entries()) {
  addCable(endpoint(lv4a, cell), endpoint(lv4b, cell), "500", "class_2", color);
}

const hvColors = [RED, YELLOW, BLUE];
for (const [index, x] of [6, 8, 10].entries()) {
  const from = addRack(x, 64, 24, "hv", "down", [0]);
  const to = addRack(x, 64, 34, "hv", "down", [0]);
  addCable(endpoint(from, 0), endpoint(to, 0), "1500", "class_2", hvColors[index]);
}
for (const [index, x] of [14, 16, 18].entries()) {
  const from = addRack(x, 64, 24, "ehv", "down", [0]);
  const to = addRack(x, 64, 34, "ehv", "down", [0]);
  addCable(endpoint(from, 0), endpoint(to, 0), "5000", "class_2", hvColors[index]);
}

label(-5.5, 67.2, 29.5, "LV 单相二线\\n2 导体", { scale: 0.50, color: tierDisplay.lv.text, bold: true, width: 150 });
label(-1.5, 68.0, 29.5, "MV 10 kV\\n三相三线", { scale: 0.50, color: "yellow", bold: true, width: 150 });
label(2.5, 67.2, 29.5, "LV 380 V\\n三相四线", { scale: 0.50, color: tierDisplay.lv.text, bold: true, width: 150 });
label(8.5, 68.0, 29.5, "HV 110 kV\\n三相三线", { scale: 0.50, color: tierDisplay.hv.text, bold: true, width: 150 });
label(16.5, 68.0, 29.5, "EHV 500 kV\\n三相三线", { scale: 0.50, color: "light_purple", bold: true, width: 150 });
command();

// Seventeen actual products: 15 copper combinations plus two bare ACSR classes.
const catalogue = [
  { z: 0, ampacity: "50", label: "50 A 铜 · 裸 / I类 / II类" },
  { z: 3, ampacity: "150", label: "150 A 铜 · 裸 / I类 / II类" },
  { z: 6, ampacity: "500", label: "500 A 铜 · 裸 / I类 / II类" },
  { z: 9, ampacity: "800", label: "800 A ACSR · 裸线" },
  { z: 12, ampacity: "1500", label: "1500 A 铜 · 裸 / I类 / II类" },
  { z: 15, ampacity: "1600", label: "1600 A ACSR · 裸线" },
  { z: 18, ampacity: "5000", label: "5000 A 铜 · 裸 / I类 / II类" },
];
for (const row of catalogue) {
  const acsr = materials[row.ampacity] === "acsr";
  const cells = acsr ? [4] : [0, 4, 8];
  command(`setblock 0 64 ${row.z} minecraft:polished_deepslate`);
  command(`setblock 20 64 ${row.z} minecraft:polished_deepslate`);
  const from = addRack(0, 65, row.z, "lv", "down", cells);
  const to = addRack(20, 65, row.z, "lv", "down", cells);
  if (acsr) {
    addCable(endpoint(from, 4), endpoint(to, 4), row.ampacity, "bare", UNDYED);
  } else {
    addCable(endpoint(from, 0), endpoint(to, 0), row.ampacity, "bare", UNDYED);
    addCable(endpoint(from, 4), endpoint(to, 4), row.ampacity, "class_1", RED);
    addCable(endpoint(from, 8), endpoint(to, 8), row.ampacity, "class_2", BLUE);
  }
  label(10.5, 67.3, row.z + 0.5, row.label, {
    scale: 0.54,
    color: acsr ? "aqua" : "white",
    bold: true,
    width: 260,
  });
}
command();

// Variable-size storage stations: minimum through maximum legal dimensions.
station({ tier: "lv", size: 3, x: 30, z: 0, cells: 1, capacity: "100 kWh", rating: "380 V / 100 A / 38 kW" });
station({ tier: "mv", size: 5, x: 36, z: 0, cells: 27, capacity: "10.8 MWh", rating: "10 kV / 250 A / 2.5 MW" });
station({ tier: "hv", size: 7, x: 44, z: 0, cells: 125, capacity: "200 MWh", rating: "110 kV / 500 A / 55 MW" });
station({ tier: "ehv", size: 9, x: 54, z: 0, cells: 343, capacity: "2.058 GWh", rating: "500 kV / 1000 A / 500 MW" });
command();

// Emit racks first, then merge cable halves. This prevents load-time orphan pruning.
const sortedRacks = [...racks.values()].sort((a, b) =>
  (a.z - b.z) || (a.x - b.x) || (a.y - b.y));
for (const rack of sortedRacks) {
  const terminals = rack.cells
    .map((cell) => `{Cell:${cell},Style:"${rack.tier}_terminal"}`)
    .join(",");
  command(`setblock ${rack.x} ${rack.y} ${rack.z} stardustindustry:${rack.tier}_terminal_mount[facing=${rack.facing}]{Terminals:[${terminals}]}`);
}
for (const rack of sortedRacks) {
  if (rack.cables.length === 0) continue;
  rack.cables.sort((a, b) => (a.cell - b.cell)
    || Number(BigInt(a.farPos) - BigInt(b.farPos))
    || (a.farCell - b.farCell));
  const cables = rack.cables.map((cable) =>
    `{Cell:${cable.cell},Ampacity:"${cable.ampacity}",Insulation:"${cable.insulation}",Color:${cable.color},FarPos:${cable.farPos}L,FarCell:${cable.farCell}}`)
    .join(",");
  command(`data merge block ${rack.x} ${rack.y} ${rack.z} {Cables:[${cables}]}`);
}
command();

// Section headers and numbered route.
label(8.5, 69.5, 61.5, "星尘工业 · 电力与储能展示场", { scale: 1.05, color: "gold", bold: true, width: 360 });
label(26.5, 66.0, 61.5, "入口", { scale: 0.72, color: "gold", bold: true });
label(26.5, 66.0, 43.5, "① 电气设备与通电贴图", { scale: 0.70, color: "gold", bold: true });
label(26.5, 66.0, 23.5, "② 端子等级与 2 / 3 / 4 线束", { scale: 0.70, color: "gold", bold: true });
label(26.5, 66.0, 16.5, "③ 17 种线缆规格墙", { scale: 0.70, color: "gold", bold: true });
label(30.5, 66.0, 11.5, "④ 储能站：3 / 5 / 7 / 9 格", { scale: 0.70, color: "gold", bold: true });
command("give @s stardustindustry:installation_tool 1");
command("give @s minecraft:red_dye 16");
command("give @s minecraft:blue_dye 16");
command("effect give @s minecraft:night_vision infinite 0 true");
command("time set noon");
command("weather clear");
command("difficulty peaceful");
command("gamerule doDaylightCycle false");
command("gamerule doWeatherCycle false");
command("gamerule doMobSpawning false");
command("gamerule doFireTick false");
command("spawnpoint @s 26 64 64");
command("tp @s 26.5 65 64.5 180 8");
command('tellraw @s {"text":"展示场已生成：沿浅灰路径依次查看设备、端子线束、17 种线缆和四级储能站。","color":"green"}');

// Every physical cable must have exactly two persisted halves with reciprocal addresses.
let persistedHalves = 0;
for (const rack of racks.values()) {
  for (const cable of rack.cables) {
    persistedHalves++;
    const far = [...racks.values()].find((candidate) => blockPosLong(candidate.x, candidate.y, candidate.z) === cable.farPos);
    if (!far) throw new Error(`Cable from ${rackKey(rack.x, rack.y, rack.z)} points to a missing rack`);
    const reciprocal = far.cables.some((candidate) =>
      candidate.cell === cable.farCell
      && candidate.farPos === blockPosLong(rack.x, rack.y, rack.z)
      && candidate.farCell === cable.cell
      && candidate.ampacity === cable.ampacity
      && candidate.insulation === cable.insulation
      && candidate.color === cable.color);
    if (!reciprocal) throw new Error(`Cable from ${rackKey(rack.x, rack.y, rack.z)} is missing its reciprocal half`);
  }
}
if (persistedHalves !== cableRuns.size * 2) {
  throw new Error(`Expected ${cableRuns.size * 2} cable halves, generated ${persistedHalves}`);
}

const generatedCommands = commands.join("\n");
const showcaseVersion = Number.parseInt(
  createHash("sha256").update(generatedCommands).digest("hex").slice(0, 7),
  16,
);
const buildFunction = `${generatedCommands}\nscoreboard players set #built sd_showcase ${showcaseVersion}\n`;

fs.rmSync(packDirectory, { recursive: true, force: true });
fs.mkdirSync(functionDirectory, { recursive: true });
fs.mkdirSync(functionTagDirectory, { recursive: true });
fs.writeFileSync(path.join(packDirectory, "pack.mcmeta"), `${JSON.stringify({
  pack: {
    pack_format: 48,
    description: "Stardust Industry electrical and storage showcase",
  },
}, null, 2)}\n`, "utf8");
fs.writeFileSync(functionPath, buildFunction, "utf8");
fs.writeFileSync(path.join(functionTagDirectory, "load.json"), `${JSON.stringify({
  values: ["stardust_showcase:load"],
}, null, 2)}\n`, "utf8");
fs.writeFileSync(path.join(functionDirectory, "load.mcfunction"), [
  "scoreboard objectives add sd_showcase dummy",
  "schedule function stardust_showcase:bootstrap 1s replace",
  "",
].join("\n"), "utf8");
fs.writeFileSync(path.join(functionDirectory, "bootstrap.mcfunction"), [
  "execute in minecraft:overworld positioned 0 0 0 unless entity @a[distance=0..] run schedule function stardust_showcase:bootstrap 1s replace",
  `execute in minecraft:overworld positioned 0 0 0 as @a[distance=0..,limit=1] at @s unless score #built sd_showcase matches ${showcaseVersion} run function stardust_showcase:build`,
  "",
].join("\n"), "utf8");

console.log(`Generated ${functionPath}`);
console.log(`Validated ${racks.size} terminal racks, ${cableRuns.size} physical cables, and ${persistedHalves} reciprocal cable halves.`);
console.log(`The showcase (version ${showcaseVersion}) will build once when a player enters the Overworld.`);
