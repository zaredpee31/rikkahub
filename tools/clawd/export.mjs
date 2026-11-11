import fs from 'fs';
import path from 'path';

const SRC = 'tools/clawd/clawd-rig.js';
const OUT = 'app/src/main/assets/clawd/clawd.json';
const INK = '#6b5d52';

let src = fs.readFileSync(SRC, 'utf8');
const ANCHOR = 'return { css: genRestCSS(),';
if (!src.includes(ANCHOR)) { console.error('clawd-rig.js 里找不到 ' + ANCHOR); process.exit(1); }
src = src.replace(ANCHOR, 'return { __x: { CLIPS, SPR, PAL, ORIGIN, REST_F, REST_Z, PARTS, SHEET, UPPER_KIDS, TICK, sampleState, flexAt }, css: genRestCSS(),');
fs.writeFileSync('/tmp/clawd-rig.mjs', src);
const { buildClawdRig } = await import('/tmp/clawd-rig.mjs');
const X = buildClawdRig().__x;

const hex2 = n => Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, '0');
function argb(v) {
  v = v.trim(); let m;
  if ((m = v.match(/^color-mix\(in srgb,\s*(.+?)\s+(\d+)%,\s*transparent\)$/))) { const c = argb(m[1]); return '#' + hex2(+m[2] / 100 * 255) + c.slice(3); }
  if ((m = v.match(/^var\([^,]+,\s*(.+)\)$/))) return argb(m[1]);
  if (v.startsWith('var(')) return argb(INK);
  if ((m = v.match(/^rgba\(\s*(\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\s*\)$/))) return '#' + hex2(+m[4] * 255) + hex2(+m[1]) + hex2(+m[2]) + hex2(+m[3]);
  if (/^#[0-9a-f]{6}$/i.test(v)) return '#ff' + v.slice(1).toLowerCase();
  if (/^#[0-9a-f]{3}$/i.test(v)) return '#ff' + v.slice(1).split('').map(c => c + c).join('').toLowerCase();
  throw new Error('不认识的颜色 ' + v);
}

const palKeys = Object.keys(X.PAL);
const palette = palKeys.map(k => argb(X.PAL[k]));
const palIndex = Object.fromEntries(palKeys.map((k, i) => [k, i]));

const names = {}, sprites = {};
for (const [sheet, frames] of Object.entries(X.SPR)) {
  names[sheet] = Object.keys(frames);
  sprites[sheet] = names[sheet].map(n => {
    const fr = frames[n], out = [];
    fr.rows.forEach((row, y) => row.split('').forEach((ch, x) => {
      if (ch === '.' || ch === ' ') return;
      if (!(ch in palIndex)) throw new Error('没有这个颜色字符 ' + ch + '（' + sheet + '.' + n + '）');
      out.push(fr.ox + x, fr.oy + y, palIndex[ch]);
    }));
    return out;
  });
}

const r2 = v => Math.round(v * 100) / 100, r3 = v => Math.round(v * 1000) / 1000;
const clips = {};
for (const [id, c] of Object.entries(X.CLIPS)) {
  const sm = new Set(c.smooth);
  const smoothOf = p => sm.has(p) || (sm.has('upper') && X.UPPER_KIDS.has(p));
  const times = [];
  for (let t = 0; t <= c.dur; t += X.TICK) times.push(t);
  if (times[times.length - 1] !== c.dur) times.push(c.dur);
  const last = {}, ticks = [];
  for (const t of times) {
    const st = X.sampleState(c, t), fl = X.flexAt(c, t);
    const row = [r2(st.root.dx), r2(st.root.dy), r3(fl.sx), r3(fl.sy), r2(fl.r), r2(fl.tx), r2(fl.ty)];
    for (const p of X.PARTS) {
      const s = st[p];
      let dx = s.dx, dy = s.dy;
      if (smoothOf(p)) { if (s.f) last[p] = [dx, dy]; else if (last[p]) [dx, dy] = last[p]; }
      const sheet = X.SHEET[p];
      const fi = s.f ? names[sheet].indexOf(s.f) : -1;
      if (s.f && fi < 0) throw new Error('没有这一帧 ' + p + '.' + s.f);
      row.push(fi, r2(dx), r2(dy), s.z, r3(s.sc), r2(s.r), r3(s.a));
    }
    ticks.push(row);
  }
  clips[id] = {
    name: c.name, dur: c.dur, loop: c.loop, intro: c.intro, track: c.track, group: c.group,
    pool: c.pool, weight: c.weight, cool: c.cool, next: c.next, hold: c.hold, after: c.after, from: c.from,
    noShadow: c.noShadow, clipGround: c.clipGround,
    smooth: X.PARTS.map(smoothOf),
    fParts: X.PARTS.map(p => !!(c.tr[p] && c.tr[p].f && c.tr[p].f.length)),
    times, ticks,
  };
}

const out = {
  v: 1, tick: X.TICK, palette, parts: X.PARTS, sheet: X.PARTS.map(p => X.SHEET[p]),
  origin: X.PARTS.map(p => X.ORIGIN[p]),
  restF: X.PARTS.map(p => (X.REST_F[p] ? names[X.SHEET[p]].indexOf(X.REST_F[p]) : -1)),
  restZ: X.PARTS.map(p => X.REST_Z[p]),
  names, sprites, clips,
};
fs.mkdirSync(path.dirname(OUT), { recursive: true });
const json = JSON.stringify(out);
fs.writeFileSync(OUT, json);
console.log('动作 ' + Object.keys(clips).length + ' 个，文件 ' + Math.round(json.length / 1024) + ' KB');
