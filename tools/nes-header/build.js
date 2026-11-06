// Builds app/src/main/assets/nesheaders.txt from NewRisingSun's NES 2.0 XML database (nes20db.xml):
// one line per game, "<CRC32 of the headerless ROM> <16-byte NES 2.0 header in hex>". The app uses it to give
// headerless .nes files (No-Intro style dumps, which FCEUmm can't open) the header they need.
//
//   node tools/nes-header/build.js path/to/nes20db.xml
const fs = require("fs");
const path = require("path");

const src = process.argv[2];
if (!src) { console.error("usage: node build.js nes20db.xml"); process.exit(1); }
const xml = fs.readFileSync(src, "utf8");
const attr = (tag, block, name) => {
  const m = block.match(new RegExp("<" + tag + "\\b[^>]*\\b" + name + '="([^"]*)"'));
  return m ? m[1] : null;
};
const num = (v) => (v == null ? 0 : parseInt(v, 10));

// ROM sizes: units in the low byte and nibble, or the exponent form for sizes that aren't whole units
function sizeField(bytes, unit) {
  const units = bytes / unit;
  if (Number.isInteger(units) && units < 0xf00) return [units & 0xff, units >> 8];
  for (let e = 0; e < 64; e++) for (let mm = 0; mm < 4; mm++) if (Math.pow(2, e) * (mm * 2 + 1) === bytes) return [(e << 2) | mm, 0xf];
  return null;
}
// RAM sizes as a shift: 64 << shift bytes, 0 for none
function shift(bytes) {
  if (!bytes) return 0;
  let s = 0;
  while ((64 << s) < bytes) s++;
  return s;
}

const out = [];
let skipped = 0;
for (const block of xml.split("<game>").slice(1)) {
  const crc = attr("rom", block, "crc32");
  const prg = sizeField(num(attr("prgrom", block, "size")), 16384);
  const chr = sizeField(num(attr("chrrom", block, "size")), 8192);
  if (!crc || !prg || !chr) { skipped++; continue; }
  const mapper = num(attr("pcb", block, "mapper"));
  const sub = num(attr("pcb", block, "submapper"));
  const mir = attr("pcb", block, "mirroring");
  const battery = num(attr("pcb", block, "battery"));
  const trainer = block.includes("<trainer") ? 1 : 0;
  const consoleType = num(attr("console", block, "type"));
  const region = num(attr("console", block, "region"));
  const misc = block.includes("<miscrom") ? num(attr("miscrom", block, "number")) : 0;
  const h = new Array(16).fill(0);
  h[0] = 0x4e; h[1] = 0x45; h[2] = 0x53; h[3] = 0x1a;
  h[4] = prg[0];
  h[5] = chr[0];
  h[6] = ((mapper & 0x0f) << 4) | (mir === "4" ? 8 : 0) | (trainer << 2) | (battery << 1) | (mir === "V" ? 1 : 0);
  h[7] = (mapper & 0xf0) | 0x08 | (consoleType > 3 ? 3 : consoleType);
  h[8] = (sub << 4) | ((mapper >> 8) & 0x0f);
  h[9] = (chr[1] << 4) | prg[1];
  h[10] = (shift(num(attr("prgnvram", block, "size"))) << 4) | shift(num(attr("prgram", block, "size")));
  h[11] = (shift(num(attr("chrnvram", block, "size"))) << 4) | shift(num(attr("chrram", block, "size")));
  h[12] = region & 3;
  if (consoleType === 1) h[13] = (num(attr("vs", block, "hardware")) << 4) | num(attr("vs", block, "ppu"));
  else if (consoleType > 3) h[13] = consoleType;
  h[14] = misc & 3;
  h[15] = num(attr("expansion", block, "type")) & 0x3f;
  out.push(crc.toUpperCase() + " " + h.map((b) => b.toString(16).padStart(2, "0")).join(""));
}
const dest = path.join(__dirname, "..", "..", "app", "src", "main", "assets", "nesheaders.txt");
fs.mkdirSync(path.dirname(dest), { recursive: true });
fs.writeFileSync(dest, out.join("\n") + "\n");
console.log("wrote", out.length, "games to", dest, "(skipped", skipped + ")");
