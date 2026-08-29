import { mkdir, readFile, writeFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import path from "node:path";

const upstreamCommit = "766b1a6af0757afce1938da2b25f306ef8079838";
const outputPath = path.resolve(
  "bgi-tools/src/main/resources/artifact/recommended-builds.json",
);

const attributeKeys = {
  HP: "hp", ATK: "atk", DEF: "def", ELEMENTAL_MASTERY: "eleMas",
  ENERGY_RECHARGE: "enerRech_", HP_PERCENT: "hp_", ATK_PERCENT: "atk_",
  DEF_PERCENT: "def_", CRIT_RATE: "critRate_", CRIT_DAMAGE: "critDMG_",
  HEALING_BONUS: "heal_", ANEMO_DAMAGE_BONUS: "anemo_dmg_",
  CRYO_DAMAGE_BONUS: "cryo_dmg_", DENDRO_DAMAGE_BONUS: "dendro_dmg_",
  ELECTRO_DAMAGE_BONUS: "electro_dmg_", GEO_DAMAGE_BONUS: "geo_dmg_",
  HYDRO_DAMAGE_BONUS: "hydro_dmg_", PHYSICAL_DAMAGE_BONUS: "physical_dmg_",
  PYRO_DAMAGE_BONUS: "pyro_dmg_",
};

const buildNameTranslations = Object.freeze({
  "主C": "主力输出",
  "副C": "副输出",
  "辅助副C": "辅助副输出",
  "雷副C": "雷系副输出",
  "六命主c": "六命主力输出",
  SHIELD_SUPPORT: "护盾辅助",
  OFF_FIELD_HEALER: "后台治疗",
  GENERAL_ON_FIELD_DPS: "通用站场输出",
  VAPORIZE_DPS: "蒸发输出",
  CHARGED_ATTACK_QUICKSWAP: "重击速切输出",
  BURST_NORMAL_ATTACK_DPS: "爆发普攻输出",
  BOND_OF_LIFE_DPS: "生命之契输出",
  AGGRAVATE_DPS: "激化输出",
  BURNING_OFF_FIELD_DPS: "燃烧后台输出",
  SCROLL_SUPPORT: "绘卷辅助",
  SKILL_CANNON_DPS: "战技炮台输出",
  ELECTRO_CHARGED_DPS: "感电输出",
  OVERLOADED_TRIGGER: "超载触发",
  RES_SHRED_HEALING: "减抗治疗",
  MULTI_ELEMENT_CHARGED_DPS: "多元素重击输出",
  ON_FIELD_BURST_DPS: "站场爆发输出",
  OFF_FIELD_PYRO_DPS: "后台火伤输出",
  MELT_SHIELD_SUPPORT: "融化护盾辅助",
  SWIRL_DRIVER: "扩散站场驱动",
  ATK_SUPPORT: "攻击辅助",
  PLUNGING_ATTACK_DPS: "下落攻击输出",
  ANEMO_DPS: "风伤输出",
  CRYO_OFF_FIELD_DPS_HEALER: "后台冰伤治疗",
  QUICKSWAP_BURST_DPS: "速切爆发输出",
  ON_FIELD_NORMAL_ATTACK_DPS: "站场普攻输出",
  SHIELD_ATK_SPEED_SUPPORT: "护盾攻速辅助",
  LUNAR_CHARGED_DPS: "月感电输出",
  HYPERBLOOM_TRIGGER: "超绽放触发",
  BLOOM_SUPPORT: "绽放辅助",
  OFF_FIELD_HYDRO_SUPPORT: "后台水系辅助",
  LUNAR_BLOOM_CHARGED_DPS: "月绽放重击输出",
  WHITE_DRAGON_SUPPORT: "白龙辅助",
  DARK_DRAGON_REACTION_DPS: "黑龙反应输出",
  OFF_FIELD_LUNAR_SUPPORT: "后台月反应辅助",
  ON_FIELD_LUNAR_BLOOM_DPS: "站场月绽放输出",
  LUNAR_CRYSTALLIZE_DPS: "月结晶输出",
  LUNAR_CRYSTALLIZE_SUPPORT: "月结晶辅助",
  GEO_DPS_SUPPORT: "岩系输出辅助",
  DUAL_ELEMENT_DPS: "双元素输出",
  OFF_FIELD_LUNAR_CRYSTALLIZE_DPS: "后台月结晶输出",
  QUICKSWAP_LUNAR_CRYSTALLIZE: "速切月结晶",
  CRYO_ON_FIELD_DPS: "站场冰伤输出",
  SHIELD_ATK_SUPPORT: "护盾攻击辅助",
  ELEMENTAL_RES_SHRED_SUPPORT: "元素减抗辅助",
  ANEMO_DPS_SUPPORT: "风伤辅助",
  STELLAR_CONDUCT_CHARGED_DPS: "星辉传导重击输出",
  STELLAR_CONDUCT_SUPPORT_HEALER: "星辉传导辅助治疗",
  STELLAR_GLIMMER_SUPPORT: "星辉闪耀辅助",
});

let attributes;

async function main() {
  const [presetSource, recommendedSource, characterProto, setProto, attributeProto] = await Promise.all([
    readUpstream("src/data/presets.js"), readUpstream("src/data/recommendedPresetHashes.js"),
    readUpstream("proto/character.proto"), readUpstream("proto/set.proto"),
    readUpstream("proto/attribute.proto"),
  ]);
  const characters = enumByNumber(characterProto, "Character");
  const sets = enumByNumber(setProto, "Set");
  attributes = enumByNumber(attributeProto, "AttributeType");
  const presetHex = [presetSource, recommendedSource]
    .flatMap((source) => [...source.matchAll(/^\s*"([0-9a-f]+)",?\s*$/gm)]
      .map((match) => match[1]));

  const builds = presetHex.map((hex, index) => {
    let decoded;
    try {
      decoded = decodeBuild(Buffer.from(hex, "hex"));
    } catch (error) {
      throw new Error(`unable to decode preset ${index + 1} (${hex.slice(0, 24)}): ${error.message}`);
    }
    const characterName = characters.get(decoded.character) ?? `CHARACTER_${decoded.character}`;
    const displayName = decoded.name.startsWith("preset:")
      ? decoded.name.slice("preset:".length).toUpperCase().replaceAll("-", "_")
      : decoded.name;
    const recipes = decoded.suits.map((recipe) => recipe.map(({ set, count }) => ({
      setKey: toPascal(sets.get(set) ?? `SET_${set}`), pieces: count,
    })));
    return {
      id: `preset-${String(index + 1).padStart(3, "0")}-${toKebab(characterName)}-${toKebab(displayName)}`,
      name: localizedBuildName(displayName),
      characterKey: toPascal(characterName),
      sets: recipes[0] ?? [],
      alternativeSetRecipes: recipes.slice(1),
      mainStatsBySlot: {
        flower: decoded.flower.map(attributeKey), plume: decoded.plume.map(attributeKey),
        sands: decoded.sands.map(attributeKey), goblet: decoded.goblet.map(attributeKey),
        circlet: decoded.circlet.map(attributeKey),
      },
      substatWeights: Object.fromEntries(decoded.substats.map(({ type, value }) => [
        attributeKey(type), Math.round(value * 10) / 10,
      ])),
      analysisEnabled: true,
      nativeSyncEnabled: true,
      sourceVersion: `genshin-artifact-analyzer@${upstreamCommit}`,
    };
  });

  if (builds.length !== 158) throw new Error(`expected 158 upstream builds, received ${builds.length}`);
  await mkdir(path.dirname(outputPath), { recursive: true });
  await writeFile(outputPath, `${JSON.stringify(builds, null, 2)}\n`, "utf8");
  process.stdout.write(`${builds.length} builds -> ${outputPath}\n`);
}

async function localizeExistingCatalog() {
  const builds = JSON.parse(await readFile(outputPath, "utf8"));
  const localized = builds.map((build) => ({
    ...build,
    name: localizedBuildName(build.name),
  }));
  await writeFile(outputPath, `${JSON.stringify(localized, null, 2)}\n`, "utf8");
  process.stdout.write(`${localized.length} localized builds -> ${outputPath}\n`);
}

function localizedBuildName(name) {
  const localized = buildNameTranslations[name] ?? name;
  if (/[A-Za-z]/.test(localized)) {
    throw new Error(`missing Chinese build name translation: ${name}`);
  }
  return localized;
}

async function readUpstream(relativePath) {
  let lastError;
  for (let attempt = 1; attempt <= 4; attempt += 1) {
    try {
      return execFileSync("gh", [
        "api",
        `repos/LeiShi1313/genshin-aritifact-analyzer/contents/${relativePath}?ref=${upstreamCommit}`,
        "-H",
        "Accept: application/vnd.github.raw+json",
      ], { encoding: "utf8", maxBuffer: 4 * 1024 * 1024 });
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

function enumByNumber(source, enumName) {
  const start = source.indexOf(`enum ${enumName}`);
  if (start < 0) throw new Error(`missing enum ${enumName}`);
  const bodyStart = source.indexOf("{", start);
  const bodyEnd = findBalancedEnd(source, bodyStart, "{", "}");
  return new Map([...source.slice(bodyStart, bodyEnd).matchAll(
    /^\s*([A-Z0-9_]+)\s*=\s*(-?\d+)\s*;/gm,
  )].map((match) => [Number(match[2]), match[1]]));
}

function decodeBuild(buffer) {
  const build = { name: "", character: 0, suits: [], flower: [], plume: [], sands: [], goblet: [], circlet: [], substats: [] };
  readFields(new Reader(buffer), (field, wire, reader) => {
    if (field === 1) build.name = reader.string();
    else if (field === 2) build.character = reader.varint();
    else if (field === 4) build.suits.push(decodeSuit(reader.bytes()));
    else if (field >= 5 && field <= 9) {
      const target = [build.flower, build.plume, build.sands, build.goblet, build.circlet][field - 5];
      readRepeatedVarint(reader, wire, target);
    } else if (field === 10) build.substats.push(decodeAttribute(reader.bytes()));
    else reader.skip(wire);
  });
  return build;
}

function decodeSuit(buffer) {
  const combos = [];
  readFields(new Reader(buffer), (field, wire, reader) => {
    if (field === 1) combos.push(decodeSetCombo(reader.bytes()));
    else reader.skip(wire);
  });
  return combos;
}

function decodeSetCombo(buffer) {
  const combo = { set: 0, count: 0 };
  readFields(new Reader(buffer), (field, wire, reader) => {
    if (field === 1) combo.set = reader.varint();
    else if (field === 2) combo.count = reader.varint();
    else reader.skip(wire);
  });
  return combo;
}

function decodeAttribute(buffer) {
  const attribute = { type: 0, value: 0 };
  readFields(new Reader(buffer), (field, wire, reader) => {
    if (field === 1) attribute.type = reader.varint();
    else if (field === 2 && wire === 5) attribute.value = reader.float32();
    else reader.skip(wire);
  });
  return attribute;
}

function readRepeatedVarint(reader, wire, target) {
  if (wire === 0) target.push(reader.varint());
  else if (wire === 2) {
    const packed = new Reader(reader.bytes());
    while (!packed.done()) target.push(packed.varint());
  } else reader.skip(wire);
}

function readFields(reader, handle) {
  while (!reader.done()) {
    const tag = reader.varint();
    const field = tag >>> 3;
    const wire = tag & 7;
    try {
      handle(field, wire, reader);
    } catch (error) {
      throw new Error(`field ${field} wire ${wire} offset ${reader.offset}: ${error.message}`);
    }
  }
}

class Reader {
  constructor(buffer) { this.buffer = buffer; this.offset = 0; }
  done() { return this.offset >= this.buffer.length; }
  varint() {
    let value = 0;
    let shift = 0;
    while (this.offset < this.buffer.length) {
      const byte = this.buffer[this.offset++];
      value += (byte & 0x7f) * 2 ** shift;
      if ((byte & 0x80) === 0) return value;
      shift += 7;
    }
    throw new Error("truncated varint");
  }
  bytes() {
    const length = this.varint();
    const end = this.offset + length;
    if (end > this.buffer.length) throw new Error("truncated bytes");
    const value = this.buffer.subarray(this.offset, end);
    this.offset = end;
    return value;
  }
  string() { return this.bytes().toString("utf8"); }
  float32() {
    const value = this.buffer.readFloatLE(this.offset);
    this.offset += 4;
    return value;
  }
  skip(wire) {
    if (wire === 0) this.varint();
    else if (wire === 1) this.offset += 8;
    else if (wire === 2) {
      const length = this.varint();
      this.offset += length;
    }
    else if (wire === 5) this.offset += 4;
    else throw new Error(`unsupported protobuf wire type ${wire}`);
    if (this.offset > this.buffer.length) throw new Error("truncated field");
  }
}

function attributeKey(value) {
  const enumName = typeof value === "number" ? attributes.get(value) : value;
  const key = attributeKeys[enumName];
  if (!key) throw new Error(`unknown attribute ${value}`);
  return key;
}

function findBalancedEnd(text, start, open, close) {
  let depth = 0;
  for (let index = start; index < text.length; index += 1) {
    if (text[index] === open) depth += 1;
    if (text[index] === close && --depth === 0) return index;
  }
  throw new Error(`unbalanced ${open}${close}`);
}

function toPascal(value) { return value.toLowerCase().split("_").map(capitalize).join(""); }
function toKebab(value) {
  return value.toLowerCase().replaceAll("_", "-")
    .replaceAll(/[^\p{L}\p{N}-]+/gu, "-").replaceAll(/^-+|-+$/g, "");
}
function capitalize(value) { return value.charAt(0).toUpperCase() + value.slice(1); }

if (process.argv.includes("--localize-existing")) await localizeExistingCatalog();
else await main();
