#!/usr/bin/env node
// Writes art/psx1g1b1c1d/disc.json: the names (Redump style, without .png) of the PS1 disc scans in
// https://github.com/UnluckyForSome/PSX1G1B1C1D (library/disc). The app uses them when LaunchBox has no disc for a
// PS1 game, fetching https://raw.githubusercontent.com/UnluckyForSome/PSX1G1B1C1D/main/library/disc/<name>.png
//   node tools/launchbox-index/psx1g1b1c1d.js art/psx1g1b1c1d
const fs = require('fs');
const path = require('path');
const https = require('https');

const get = url => new Promise((ok, fail) => https.get(url, { headers: { 'User-Agent': 'slot-android' } }, r => {
  let s = ''; r.on('data', d => s += d); r.on('end', () => r.statusCode === 200 ? ok(JSON.parse(s)) : fail(new Error(url + ' ' + r.statusCode)));
}).on('error', fail));

(async () => {
  const out = process.argv[2] || 'art/psx1g1b1c1d';
  const lib = await get('https://api.github.com/repos/UnluckyForSome/PSX1G1B1C1D/contents/library');
  const disc = lib.find(x => x.name === 'disc');
  const tree = await get(`https://api.github.com/repos/UnluckyForSome/PSX1G1B1C1D/git/trees/${disc.sha}`);
  if (tree.truncated) throw new Error('tree truncated');
  const names = tree.tree.filter(x => x.type === 'blob' && x.path.endsWith('.png')).map(x => x.path.slice(0, -4)).sort();
  fs.mkdirSync(out, { recursive: true });
  fs.writeFileSync(path.join(out, 'disc.json'), JSON.stringify(names));
  console.log(names.length, 'PS1 disc scans');
})().catch(e => { console.error(e.message); process.exit(1); });
