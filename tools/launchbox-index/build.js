#!/usr/bin/env node
// Builds art/launchbox/<system>.json: for each disc system, "Game Name (Region)" -> LaunchBox image file name of a
// scan of the disc (served from https://images.launchbox-app.com/<file>). Alternate names are included too.
// The app downloads one small list per system instead of LaunchBox's whole database.
//
//   curl -o Metadata.zip https://gamesdb.launchbox-app.com/Metadata.zip && unzip Metadata.zip Metadata.xml
//   node tools/launchbox-index/build.js Metadata.xml art/launchbox
const fs = require('fs');
const path = require('path');
const readline = require('readline');

const PLATFORMS = {
  'Sony Playstation': 'psx', 'Sony Playstation 2': 'ps2', 'Sony PSP': 'psp', 'Sega Dreamcast': 'dc',
  'Nintendo GameCube': 'gc', 'Microsoft Xbox': 'xbox', 'Microsoft Xbox 360': 'x360', 'Sony Playstation 3': 'ps3',
};
// LaunchBox regions -> the No-Intro / Redump style tags the app's name matcher understands
const REGION = {
  'North America': 'USA', 'United States': 'USA', 'Canada': 'USA', 'World': 'World', 'Japan': 'Japan',
  'Europe': 'Europe', 'United Kingdom': 'Europe', 'Germany': 'Europe', 'France': 'Europe', 'Italy': 'Europe',
  'Spain': 'Europe', 'Australia': 'Europe', 'The Netherlands': 'Europe', 'Sweden': 'Europe', 'Korea': 'Korea',
  'Asia': 'Asia', 'Brazil': 'Brazil', 'China': 'China',
};
const unxml = s => s.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'");

const [src, outDir] = process.argv.slice(2);
if (!src || !outDir) { console.error('usage: build.js Metadata.xml art/launchbox'); process.exit(1); }

const games = new Map();      // DatabaseID -> { name, system }
const alts = new Map();       // DatabaseID -> [names]
const discs = new Map();      // DatabaseID -> [{ file, region }]
let cur = null, kind = null;
const rl = readline.createInterface({ input: fs.createReadStream(src), crlfDelay: Infinity });
rl.on('line', raw => {
  const l = raw.trim();
  if (l === '<Game>' || l === '<GameImage>' || l === '<GameAlternateName>') { cur = {}; kind = l; return; }
  if (cur && l === '</Game>') {
    const sys = PLATFORMS[cur.Platform];
    if (sys && cur.DatabaseID && cur.Name) games.set(cur.DatabaseID, { name: unxml(cur.Name), system: sys });
    cur = null; return;
  }
  if (cur && l === '</GameImage>') {
    if (cur.Type === 'Disc' && cur.FileName) {
      const a = discs.get(cur.DatabaseID) || [];
      a.push({ file: cur.FileName, region: REGION[cur.Region] || '' });
      discs.set(cur.DatabaseID, a);
    }
    cur = null; return;
  }
  if (cur && l === '</GameAlternateName>') {
    if (cur.AlternateName) { const a = alts.get(cur.DatabaseID) || []; a.push(unxml(cur.AlternateName)); alts.set(cur.DatabaseID, a); }
    cur = null; return;
  }
  if (!cur) return;
  const m = l.match(/^<(\w+)>(.*)<\/\1>$/);
  if (m) cur[m[1]] = m[2];
});
rl.on('close', () => {
  const out = {};
  for (const [id, list] of discs) {
    const g = games.get(id);
    if (!g) continue;
    const o = out[g.system] || (out[g.system] = {});
    const names = [g.name, ...(alts.get(id) || [])];
    // one scan per region (the first listed); a scan without a region is used for the bare name
    const seen = new Set();
    for (const { file, region } of list) {
      if (seen.has(region)) continue;
      seen.add(region);
      for (const n of names) {
        const key = region ? `${n} (${region})` : n;
        if (!(key in o)) o[key] = file;
      }
    }
    for (const n of names) if (!(n in o)) o[n] = list[0].file;
  }
  fs.mkdirSync(outDir, { recursive: true });
  for (const [sys, map] of Object.entries(out)) {
    const sorted = Object.fromEntries(Object.entries(map).sort(([a], [b]) => a.localeCompare(b)));
    fs.writeFileSync(path.join(outDir, `${sys}.json`), JSON.stringify(sorted));
    console.log(sys.padEnd(5), String(Object.keys(sorted).length).padStart(6), 'names');
  }
});
