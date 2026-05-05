// Iteration 33
'use strict';

// ── Configuration ─────────────────────────────────────────────────────────────

// Native dimensions of all fragment images (from grab.py capture settings)
const IMG_W = 1280;
const IMG_H = 800;

const FG_W = 1080; // fixed fg design width; CSS scale brings it to viewport on narrow screens

const CONFIG = {
  maxImages:         40,
  overlapTarget:     0.50,
  maxScrapAreaFrac:  0.097,  // regular scrap area as fraction of W×H
  minScrapAreaFrac:  0.018,  // regular scrap area as fraction of W×H
  minAspectRatio:    0.15,   // shorter ÷ longer
  rippedDist: [0, 0, 0, 0, 0, 0, 1, 1, 1, 2],
  placementAttempts: 200,
  edgeOverhang:      0.04,
  boringThreshold:   0.60,  // reject crop if >60% pixels are near-white or near-black
  cropRetries:       10,
};

// ── Utilities ─────────────────────────────────────────────────────────────────

function rnd(min, max) { return min + Math.random() * (max - min); }
function rndInt(min, max) { return Math.floor(min + Math.random() * (max - min + 1)); }

function hexToRgba(hex, alpha) {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

function shuffle(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = rndInt(0, i);
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// ── Image discovery ───────────────────────────────────────────────────────────

// Returns [{src, imgEl}] for all fragments listed in manifest.json.
async function discoverImages() {
  function loadOne(src) {
    return new Promise(resolve => {
      const imgEl = new Image();
      imgEl.onload  = () => resolve({ src, imgEl });
      imgEl.onerror = () => resolve(null);
      imgEl.src = src;
    });
  }
  const manifest = await fetch('../images/manifest.json').then(r => r.json());
  const results = await Promise.all(
    manifest.fragments.map(f => loadOne(`../images/${f}`))
  );
  return results.filter(Boolean);
}

// ── Boring-crop detection ─────────────────────────────────────────────────────

const _sampleCanvas = document.createElement('canvas');
_sampleCanvas.width = _sampleCanvas.height = 32;
const _sampleCtx = _sampleCanvas.getContext('2d', { willReadFrequently: true });

function cropBoringScore(imgEl, cropX, cropY, cropW, cropH) {
  try {
    _sampleCtx.clearRect(0, 0, 32, 32);
    _sampleCtx.drawImage(imgEl, cropX, cropY, cropW, cropH, 0, 0, 32, 32);
    const data  = _sampleCtx.getImageData(0, 0, 32, 32).data;
    const total = 32 * 32;
    let white = 0, black = 0;
    for (let i = 0; i < data.length; i += 4) {
      const lum = (data[i] + data[i + 1] + data[i + 2]) / 3;
      if (lum > 230) white++;
      else if (lum < 25) black++;
    }
    return Math.max(white, black) / total;
  } catch (_) {
    return 0;
  }
}

// ── Clip-path generation ──────────────────────────────────────────────────────

function chooseRippedEdges() {
  const n = CONFIG.rippedDist[rndInt(0, CONFIG.rippedDist.length - 1)];
  return new Set(shuffle([0, 1, 2, 3]).slice(0, n));
}

// Clockwise edges: 0=top(L→R), 1=right(T→B), 2=bottom(R→L), 3=left(B→T)
// innerAngle rotates the four corners around (50,50) before edge generation,
// producing a rotated-rectangle cutout shape rather than an axis-aligned one.
// Returns { clipPath, fringePolygons } — fringePolygons is one cream strip per ripped edge
function generateClipPath(rippedEdges, innerAngle = 0) {
  const cos = Math.cos(innerAngle * Math.PI / 180);
  const sin = Math.sin(innerAngle * Math.PI / 180);
  // Scale so all 4 rotated corners stay within [0,100]%
  const scale = 1 / (Math.abs(cos) + Math.abs(sin) || 1);
  function rp(x, y) {
    const sx = (x - 50) * scale, sy = (y - 50) * scale;
    return [50 + sx * cos - sy * sin, 50 + sx * sin + sy * cos];
  }
  function rd(dx, dy) { return [dx * cos - dy * sin, dx * sin + dy * cos]; }

  const rawEdges = [
    [  0,   0, 100,   0,  0,  1],
    [100,   0, 100, 100, -1,  0],
    [100, 100,   0, 100,  0, -1],
    [  0, 100,   0,   0,  1,  0],
  ];
  const edgeDefs = rawEdges.map(([x0, y0, x1, y1, dx, dy]) => {
    const [rx0, ry0] = rp(x0, y0);
    const [rx1, ry1] = rp(x1, y1);
    const [rdx, rdy] = rd(dx, dy);
    return [rx0, ry0, rx1, ry1, rdx, rdy];
  });

  const allPts       = [];
  const rippedEdgeData = [];

  for (let e = 0; e < 4; e++) {
    const [x0, y0, x1, y1, dx, dy] = edgeDefs[e];
    const isRipped = rippedEdges.has(e);
    const ePts = [[x0, y0]];

    allPts.push([x0, y0]);

    if (isRipped) {
      const off1 = rnd(-3, 13);
      const off2 = rnd(-3, 13);
      const n    = 22 + rndInt(0, 8);
      for (let j = 1; j < n; j++) {
        const t      = j / n;
        const u      = 1 - t;
        const bx     = x0 + (x1 - x0) * t;
        const by     = y0 + (y1 - y0) * t;
        const smooth = 3 * u * u * t * off1 + 3 * u * t * t * off2;
        const micro  = rnd(-0.4, 0.4);
        const pt     = [bx + dx * (smooth + micro), by + dy * (smooth + micro)];
        allPts.push(pt);
        ePts.push(pt);
      }
      rippedEdgeData.push({ ePts, dx, dy });
    } else {
      const off1 = rnd(-3.5, 3.5);
      const off2 = rnd(-3.5, 3.5);
      const n    = 12;
      for (let j = 1; j < n; j++) {
        const t   = j / n;
        const u   = 1 - t;
        const bx  = x0 + (x1 - x0) * t;
        const by  = y0 + (y1 - y0) * t;
        const off = 3 * u * u * t * off1 + 3 * u * t * t * off2;
        allPts.push([bx + dx * off, by + dy * off]);
      }
    }
  }

  const clipPath = `polygon(${allPts.map(([x, y]) => `${x.toFixed(1)}% ${y.toFixed(1)}%`).join(', ')})`;

  // Fringe strip centered on the torn edge: inner half hidden under scrap, outer half peeks out
  const fringeW = rnd(0.7, 1.4);
  const half    = fringeW / 2;
  const fringePolygons = rippedEdgeData.map(({ ePts, dx, dy }) => {
    const innerPts = ePts.map(([x, y]) => [
      x + dx * (half + rnd(-0.3, 0.3)),
      y + dy * (half + rnd(-0.3, 0.3)),
    ]);
    const outerPts = ePts.map(([x, y]) => [
      x - dx * (half + rnd(-0.3, 0.3)),
      y - dy * (half + rnd(-0.3, 0.3)),
    ]);
    const poly = [...innerPts, ...[...outerPts].reverse()];
    return `polygon(${poly.map(([x, y]) => `${x.toFixed(1)}% ${y.toFixed(1)}%`).join(', ')})`;
  });

  return { clipPath, fringePolygons };
}

// ── Soft-overlap stochastic placement ────────────────────────────────────────

function getAABB(cx, cy, w, h, deg) {
  const rad = deg * Math.PI / 180;
  const cos = Math.abs(Math.cos(rad));
  const sin = Math.abs(Math.sin(rad));
  return {
    l: cx - (w * cos + h * sin) / 2,
    r: cx + (w * cos + h * sin) / 2,
    t: cy - (w * sin + h * cos) / 2,
    b: cy + (w * sin + h * cos) / 2,
  };
}

function overlapArea(a, b) {
  const ox = Math.max(0, Math.min(a.r, b.r) - Math.max(a.l, b.l));
  const oy = Math.max(0, Math.min(a.b, b.b) - Math.max(a.t, b.t));
  return ox * oy;
}

function scorePlacement(cx, cy, w, h, rot, placed, overlapTarget) {
  const box    = getAABB(cx, cy, w, h, rot);
  let total    = 0;
  for (const p of placed) {
    total += overlapArea(box, getAABB(p.cx, p.cy, p.w, p.h, p.rot));
  }
  const frac   = total / (w * h);
  const target = overlapTarget ?? CONFIG.overlapTarget;
  return frac <= target ? frac : target + (frac - target) * 4;
}

function bestPosition(w, h, rot, W, H, placed, forbidden = []) {
  const oh     = CONFIG.edgeOverhang;
  let best     = { cx: W / 2, cy: H / 2 };
  let topScore = Infinity;
  for (let i = 0; i < CONFIG.placementAttempts; i++) {
    const cx = rnd(-W * oh, W * (1 + oh));
    const cy = rnd(-H * oh, H * (1 + oh));
    if (forbidden.length > 0) {
      const box = getAABB(cx, cy, w, h, rot);
      if (forbidden.some(p => overlapArea(box, getAABB(p.cx, p.cy, p.w, p.h, p.rot)) > 0)) continue;
    }
    const s  = scorePlacement(cx, cy, w, h, rot, placed);
    if (s < topScore) { topScore = s; best = { cx, cy }; }
  }
  return best;
}

// ── DOM rendering ─────────────────────────────────────────────────────────────

function renderScrap(container, src, cx, cy, w, h, rot, clipPath, fringePolygons, zIndex, shadowX, shadowY, shadowBlur, cropX, cropY, shadowAlpha) {
  // Fringe divs rendered first so the scrap sits on top (same z-index, later in DOM)
  const basePos = [
    `position:absolute`,
    `left:${(cx - w / 2).toFixed(1)}px`,
    `top:${(cy - h / 2).toFixed(1)}px`,
    `width:${w.toFixed(1)}px`,
    `height:${h.toFixed(1)}px`,
    `transform:rotate(${rot.toFixed(1)}deg)`,
    `transform-origin:center center`,
    `z-index:${zIndex}`,
    `pointer-events:none`,
  ];
  for (const fp of fringePolygons) {
    const fd = document.createElement('div');
    fd.style.cssText = [...basePos, `clip-path:${fp}`, `background:#f0ebe0`].join(';');
    container.appendChild(fd);
  }

  const div = document.createElement('div');
  div.className = 'scrap';

  const alpha = shadowAlpha !== undefined ? shadowAlpha : rnd(0.32, 0.52).toFixed(2);
  div.style.cssText = [
    `left:${(cx - w / 2).toFixed(1)}px`,
    `top:${(cy - h / 2).toFixed(1)}px`,
    `width:${w.toFixed(1)}px`,
    `height:${h.toFixed(1)}px`,
    `transform:rotate(${rot.toFixed(1)}deg)`,
    `clip-path:${clipPath}`,
    `filter:drop-shadow(${shadowX}px ${shadowY}px ${shadowBlur}px rgba(0,0,0,${alpha}))`,
    `z-index:${zIndex}`,
  ].join(';');

  const img = document.createElement('img');
  img.src = src;
  img.alt = '';
  img.draggable = false;
  img.style.objectFit = 'none';
  img.style.objectPosition = `-${cropX.toFixed(0)}px -${cropY.toFixed(0)}px`;
  div.appendChild(img);
  container.appendChild(div);
}

// ── Site image layer ──────────────────────────────────────────────────────────

const SITE_CONFIG = {
  overlapTarget: 0.10,
  minFraction:   0.15,
  maxFraction:   0.27,
  maxRotation:   15,
  baseZ:         1000,
};

const SITE_IMAGES = [
  { src: '../images/sites/element.png',    href: 'https://element.techietable.com',    label: 'ELEMENT'    },
  { src: '../images/sites/fivethings.png', href: 'https://fivethings.techietable.com', label: 'FIVE THINGS' },
  { src: '../images/sites/infontity.png',  href: 'https://infontityscroll.com',         label: 'INFONTITY'  },
  { src: '../images/sites/skyisopen.png',  href: 'https://skyisopen.techietable.com',  label: 'SKYISOPEN'  },
];

const LABEL_FONTS  = ['Impact', 'Arial Black', 'Georgia', '"Comic Sans MS"', '"Courier New"'];
const LABEL_COLORS = [
  '#e63946', // red
  '#2ec4b6', // teal
  '#ff9f1c', // orange
  '#6a4c93', // purple
  '#1982c4', // blue
  '#f72585', // magenta
  '#06d6a0', // lime
  '#ff006e', // hot pink
  '#fb5607', // coral
  '#ffbe0b', // gold
  '#00b4d8', // cyan
  '#ffd60a', // yellow
];

function renderLabel(container, label, cx, cy, w, h, rot, zIndex, fixedColor) {
  // Independent position: random offset from scrap center, up to ±25% of each dimension
  const offX = rnd(-0.05, 0.05) * w;
  const offY = rnd(-0.05, 0.05) * h;
  const lw = w * rnd(0.55, 0.80);
  const lh = h * rnd(0.40, 0.65);
  const lcx = cx + offX;
  const lcy = cy + offY;
  const jc = ['flex-start', 'center', 'flex-end'][rndInt(0, 2)];
  const ai = 'center';

  const div = document.createElement('div');
  div.style.cssText = [
    `position:absolute`,
    `left:${(lcx - lw / 2).toFixed(1)}px`,
    `top:${(lcy - lh / 2).toFixed(1)}px`,
    `width:${lw.toFixed(1)}px`,
    `height:${lh.toFixed(1)}px`,
    `transform:rotate(${rnd(-25, 25).toFixed(1)}deg)`,
    `transform-origin:center center`,
    `z-index:${zIndex}`,
    `display:flex`,
    `overflow:visible`,
    `align-items:${ai}`,
    `justify-content:${jc}`,
    `gap:2px`,
    `pointer-events:auto`,
    `cursor:pointer`,
  ].join(';');

  const minFontPx = parseFloat(getComputedStyle(document.documentElement).fontSize) * 2.6;
  const fontSize = Math.max(minFontPx, Math.round(Math.min(lw / label.length * 1.1, lh * 0.85)));
  const font  = LABEL_FONTS[Math.floor(Math.random() * LABEL_FONTS.length)];
  const color = fixedColor || LABEL_COLORS[Math.floor(Math.random() * LABEL_COLORS.length)];
  for (const ch of label) {
    const span  = document.createElement('span');
    span.textContent = ch;
    const isSpace = ch === ' ';
    span.style.cssText = [
      `display:inline-block`,
      `transform:rotate(${isSpace ? '0' : rnd(-16, 16).toFixed(1)}deg)`,
      `font-family:${font}`,
      `font-size:${fontSize}px`,
      `font-weight:bold`,
      `color:${color}`,
      `text-shadow:-1px -1px 0 #000,1px -1px 0 #000,-1px 1px 0 #000,1px 1px 0 #000`,
      `line-height:1`,
      isSpace ? `min-width:${(fontSize * 0.55).toFixed(0)}px` : '',
    ].filter(Boolean).join(';');
    div.appendChild(span);
  }
  container.appendChild(div);
  return div;
}

function renderSiteScrap(container, src, cx, cy, w, h, rot, clipPath, fringePolygons, zIndex, shadowX, shadowY, shadowBlur, labelColor) {
  const allEls = [];
  const basePos = [
    `position:absolute`,
    `left:${(cx - w / 2).toFixed(1)}px`,
    `top:${(cy - h / 2).toFixed(1)}px`,
    `width:${w.toFixed(1)}px`,
    `height:${h.toFixed(1)}px`,
    `transform:rotate(${rot.toFixed(1)}deg)`,
    `transform-origin:center center`,
    `z-index:${zIndex}`,
  ];

  for (const fp of fringePolygons) {
    const fd = document.createElement('div');
    fd.style.cssText = [...basePos, `clip-path:${fp}`, `background:#f0ebe0`, `pointer-events:none`].join(';');
    container.appendChild(fd);
    allEls.push(fd);
  }

  // Stronger double drop-shadow for site scraps
  const alpha = rnd(0.55, 0.75).toFixed(2);
  const boostDist = shadowBlur * 0.7;
  const blackShadow = `drop-shadow(${shadowX}px ${shadowY}px ${shadowBlur}px rgba(0,0,0,${alpha}))` +
                      ` drop-shadow(${(shadowX * 0.5).toFixed(1)}px ${(shadowY * 0.5).toFixed(1)}px ${boostDist.toFixed(1)}px rgba(0,0,0,0.30))`;
  // Airbrush glow: separate fixed-z element so it stays in place on click-to-front
  if (labelColor) {
    const glowFilter = [
      `drop-shadow(0 0 4px ${hexToRgba(labelColor, 1.00)})`,
      `drop-shadow(0 0 10px ${hexToRgba(labelColor, 0.90)})`,
      `drop-shadow(0 0 22px ${hexToRgba(labelColor, 0.60)})`,
      `drop-shadow(0 0 40px ${hexToRgba(labelColor, 0.22)})`,
      `drop-shadow(0 0 65px ${hexToRgba(labelColor, 0.08)})`,
    ].join(' ');
    const glowWrapper = document.createElement('div');
    glowWrapper.style.cssText = [
      `position:absolute`,
      `left:${(cx - w / 2).toFixed(1)}px`,
      `top:${(cy - h / 2).toFixed(1)}px`,
      `width:${w.toFixed(1)}px`,
      `height:${h.toFixed(1)}px`,
      `transform:rotate(${rot.toFixed(1)}deg)`,
      `transform-origin:center center`,
      `z-index:${SITE_CONFIG.baseZ - 2}`,
      `filter:${glowFilter}`,
      `pointer-events:none`,
    ].join(';');
    const glowInner = document.createElement('div');
    glowInner.style.cssText = `width:100%;height:100%;clip-path:${clipPath};background:${labelColor}`;
    glowWrapper.appendChild(glowInner);
    container.appendChild(glowWrapper);
  }

  // Wrapper carries only the black shadow; inner div carries clip-path
  const wrapper = document.createElement('div');
  wrapper.className = 'site-scrap-wrapper';
  wrapper.style.cssText = [
    `position:absolute`,
    `left:${(cx - w / 2).toFixed(1)}px`,
    `top:${(cy - h / 2).toFixed(1)}px`,
    `width:${w.toFixed(1)}px`,
    `height:${h.toFixed(1)}px`,
    `transform:rotate(${rot.toFixed(1)}deg)`,
    `transform-origin:center center`,
    `z-index:${zIndex}`,
    `cursor:pointer`,
    `will-change:transform`,
    `filter:${blackShadow}`,
  ].join(';');

  const div = document.createElement('div');
  div.className = 'site-scrap';
  div.style.cssText = [
    `width:100%`,
    `height:100%`,
    `clip-path:${clipPath}`,
  ].join(';');

  // 30% chance of x-axis or y-axis pre-crop via object-position
  let objPos = 'center center';
  if (Math.random() < 0.30) {
    if (Math.random() < 0.5) objPos = `${rndInt(10, 90)}% center`;
    else                      objPos = `center ${rndInt(10, 90)}%`;
  }

  const img = document.createElement('img');
  img.src = src;
  img.alt = '';
  img.draggable = false;
  img.style.objectPosition = objPos;
  div.appendChild(img);
  wrapper.appendChild(div);
  container.appendChild(wrapper);
  allEls.push(wrapper);

  // Reverse-shadow: light-catch overlay on the upper-left edge
  const overlay = document.createElement('div');
  overlay.style.cssText = [
    ...basePos,
    `clip-path:${clipPath}`,
    `background:linear-gradient(135deg, rgba(255,255,255,0.22) 0%, transparent 55%)`,
    `pointer-events:none`,
  ].join(';');
  container.appendChild(overlay);
  allEls.push(overlay);

  return allEls;
}

async function buildSiteLayer(container, W, H) {
  const lightAngle = rnd(25, 65) * Math.PI / 180;
  const shadowDist = rnd(5, 12);
  const shadowX    = +(Math.cos(lightAngle) * shadowDist).toFixed(1);
  const shadowY    = +(Math.sin(lightAngle) * shadowDist).toFixed(1);
  const shadowBlur = +rnd(8, 16).toFixed(1);

  // container is already the CSS-centered site wrapper; W is the content area width
  const contentRight = W;

  // Semi-transparent frosted panel behind site images
  const contentPanel = document.createElement('div');
  contentPanel.style.cssText = [
    `position:absolute`,
    `left:0`,
    `top:0`,
    `width:100%`,
    `height:100%`,
    `background:radial-gradient(ellipse 80% 75% at center, rgba(255,255,255,0.22) 0%, rgba(255,255,255,0) 100%)`,
    `z-index:${SITE_CONFIG.baseZ - 1}`,
    `pointer-events:none`,
  ].join(';');
  container.appendChild(contentPanel);

  const N = SITE_IMAGES.length;
  // Each image gets an equal share of the content area; target 60–72% of that share
  const slotArea = (W * H) / N;

  // Grid: soft gravitational pull toward cell centers
  const cols = Math.ceil(Math.sqrt(N));
  const rows = Math.ceil(N / cols);
  const cellW = W / cols;
  const cellH = H / rows;

  // Non-contiguous rotation buckets: divide ±28° into 2*N buckets, pick N
  // non-adjacent ones — guarantees ≥1 bucket gap (~7°) between any two images
  const ROT_RANGE = 28;
  const numBuckets = N * 2;
  const bSize = (ROT_RANGE * 2) / numBuckets;
  const startOffset = Math.random() < 0.5 ? 0 : 1; // randomly use even or odd set
  const selectedBuckets = [];
  for (let k = startOffset; k < numBuckets; k += 2) selectedBuckets.push(k);
  for (let k = selectedBuckets.length - 1; k > 0; k--) {
    const j = Math.floor(Math.random() * (k + 1));
    [selectedBuckets[k], selectedBuckets[j]] = [selectedBuckets[j], selectedBuckets[k]];
  }

  const placed = [];
  let topZ = SITE_CONFIG.baseZ + N * 2 + 2;
  const siteGroups = [];
  const labelColorPool = shuffle(LABEL_COLORS);

  for (let i = 0; i < N; i++) {
    const { src, href, label } = SITE_IMAGES[i];

    // Derive dimensions from content-area slot
    const aspect      = rnd(0.55, 1.0);
    const targetArea  = slotArea * rnd(0.60, 0.75);
    const longer      = Math.sqrt(targetArea / aspect);
    const shorter     = longer * aspect;
    const landscape   = Math.random() < 0.5;
    const w = landscape ? longer : shorter;
    const h = landscape ? shorter : longer;

    // Non-contiguous bucket rotation — guaranteed ≥7° separation
    const rot = -ROT_RANGE + selectedBuckets[i] * bSize + rnd(0, bSize);

    const zIndex = SITE_CONFIG.baseZ + i * 2;

    // Soft grid: each image gravitates toward its cell center (±30% of cell)
    const col = i % cols;
    const row = Math.floor(i / cols);
    const cellCx = (col + 0.5) * cellW;
    const cellCy = (row + 0.5) * cellH;
    const driftX = cellW * 0.30;
    const driftY = cellH * 0.30;

    let best = { cx: cellCx, cy: cellCy };
    let topScore = Infinity;
    for (let a = 0; a < 500; a++) {
      const cx = Math.max(w / 2, Math.min(contentRight - w / 2, cellCx + rnd(-driftX, driftX)));
      const cy = Math.max(h / 2, Math.min(H - h / 2, cellCy + rnd(-driftY, driftY)));
      const s  = scorePlacement(cx, cy, w, h, rot, placed, 0);
      if (s < topScore) { topScore = s; best = { cx, cy }; }
    }
    placed.push({ cx: best.cx, cy: best.cy, w, h, rot });

    const labelColor = labelColorPool[i];
    const innerAngle = rnd(-10, 10);
    const { clipPath, fringePolygons } = generateClipPath(chooseRippedEdges(), innerAngle);
    const allEls  = renderSiteScrap(container, src, best.cx, best.cy, w, h, rot, clipPath, fringePolygons, zIndex, shadowX, shadowY, shadowBlur, labelColor);
    const labelEl = renderLabel(container, label, best.cx, best.cy, w, h, rot, zIndex + 1, labelColor);

    siteGroups.push({ allEls, labelEl, href });
  }

  // Desktop: hover → front, click → navigate
  // Mobile:  first tap → front, second tap on same image → navigate
  let frontGroup = null;

  for (const group of siteGroups) {
    const scrapDiv = group.allEls.find(el => el.classList && el.classList.contains('site-scrap-wrapper'));

    const bringToFront = () => {
      topZ += 2;
      for (const el of group.allEls) el.style.zIndex = String(topZ);
      group.labelEl.style.zIndex = String(topZ + 1);
      frontGroup = group;
    };
    const navigate = () => { if (group.href) window.location.href = group.href; };

    if (scrapDiv) {
      scrapDiv.addEventListener('mouseenter', bringToFront);
      scrapDiv.addEventListener('click', navigate);
      scrapDiv.addEventListener('touchend', (e) => {
        e.preventDefault();
        frontGroup === group ? navigate() : bringToFront();
      });
    }

    group.labelEl.addEventListener('click', navigate);
    group.labelEl.addEventListener('touchend', (e) => {
      e.preventDefault();
      frontGroup === group ? navigate() : bringToFront();
    });
  }
}

// ── Spinner ───────────────────────────────────────────────────────────────────

function showSpinner(src = null) {
  if (document.getElementById('loading-spinner')) return;
  const el = document.createElement('div');
  el.id = 'loading-spinner';
  const glow = document.createElement('div');
  glow.className = 'spinner-glow';
  const scrap = document.createElement('div');
  scrap.className = 'spinner-scrap';
  if (src) {
    const img = document.createElement('img');
    img.src = src;
    img.style.cssText = 'width:100%;height:100%;object-fit:cover;display:block;';
    scrap.style.background = 'none';
    scrap.appendChild(img);
  }
  glow.appendChild(scrap);
  el.appendChild(glow);
  document.body.appendChild(el);
}


function hideSpinner() {
  // wait two frames so the collage paints before the spinner disappears
  requestAnimationFrame(() => requestAnimationFrame(() => {
    const el = document.getElementById('loading-spinner');
    if (el) el.remove();
  }));
}

// ── Build ─────────────────────────────────────────────────────────────────────

function makeCrop(src, imgEl, W, H, minAreaFrac, maxAreaFrac) {
  const aspect  = rnd(CONFIG.minAspectRatio, 1.0);
  const area    = W * H * rnd(minAreaFrac, maxAreaFrac);
  const longer  = Math.sqrt(area / aspect);
  const shorter = longer * aspect;
  const landscape = Math.random() < 0.5;
  const w = Math.min(landscape ? longer : shorter, IMG_W);
  const h = Math.min(landscape ? shorter : longer, IMG_H);
  const rot = rnd(-28, 28);
  let cropX = 0, cropY = 0, bestScore = Infinity;
  for (let tries = 0; tries < CONFIG.cropRetries; tries++) {
    const cx = rnd(0, Math.max(0, IMG_W - w));
    const cy = rnd(0, Math.max(0, IMG_H - h));
    const score = cropBoringScore(imgEl, cx, cy, Math.ceil(w), Math.ceil(h));
    if (score < bestScore) { bestScore = score; cropX = cx; cropY = cy; }
    if (score <= CONFIG.boringThreshold) break;
  }
  if (bestScore > CONFIG.boringThreshold) return null;
  return { src, w, h, rot, cropX, cropY, area: w * h };
}

let cachedImages = null;

async function buildCollage() {
  const container  = document.getElementById('collage');
  const W = window.innerWidth;
  const H = window.innerHeight;

  // Show spinner immediately — opaque overlay hides everything below
  const spinnerSrc = SITE_IMAGES[Math.floor(Math.random() * SITE_IMAGES.length)].src;
  showSpinner(spinnerSrc);
  await new Promise(r => requestAnimationFrame(() => setTimeout(r, 0)));

  if (!cachedImages) {
    // Preload site images in parallel with fragment discovery
    const sitePreload = Promise.all(SITE_IMAGES.map(({ src }) => new Promise(r => {
      const img = new Image(); img.onload = img.onerror = r; img.src = src;
    })));
    cachedImages = await discoverImages();
    await sitePreload;
  }
  if (cachedImages.length === 0) {
    container.textContent = 'No fragment images found in ../images/';
    hideSpinner();
    return;
  }

  const shuffled    = shuffle(cachedImages).slice(0, CONFIG.maxImages);
  const numBacking  = rndInt(4, 6);
  const backingPool = shuffled.slice(0, numBacking);
  const regularPool = shuffled.slice(numBacking);

  // Backing scraps: one large crop per image, placed first (land at bottom of z-stack)
  // Regular scraps: two crops per image for dense coverage
  const scraps = [
    ...backingPool.map(({ src, imgEl }) => makeCrop(src, imgEl, W, H, 0.12, 0.30)),
    ...regularPool.flatMap(({ src, imgEl }) => [
      makeCrop(src, imgEl, W, H, CONFIG.minScrapAreaFrac, CONFIG.maxScrapAreaFrac),
      makeCrop(src, imgEl, W, H, CONFIG.minScrapAreaFrac, CONFIG.maxScrapAreaFrac),
    ]),
  ].filter(Boolean);

  // Sort largest → smallest so big scraps land at the bottom (lower z-index)
  scraps.sort((a, b) => b.area - a.area);

  const lightAngle  = rnd(25, 65) * Math.PI / 180;
  const baseDist    = rnd(4, 8);
  const baseBlur    = rnd(5, 10);

  // Build wrappers detached from the live DOM — yields below won't cause partial paints
  const bgWrapper = document.createElement('div');
  bgWrapper.style.cssText = `position:absolute;left:50%;transform:translateX(-50%);width:${W}px;height:100%;overflow:visible`;

  const bgContainer = document.createElement('div');
  bgContainer.style.cssText = 'position:absolute;inset:0;filter:saturate(0.45) brightness(0.70)';
  bgWrapper.appendChild(bgContainer);

  const placed = [];
  for (let i = 0; i < scraps.length; i++) {
    const { src, w, h, rot, cropX, cropY } = scraps[i];
    const rippedEdges = chooseRippedEdges();
    const { clipPath, fringePolygons } = generateClipPath(rippedEdges);

    // Same-source scraps must not overlap each other at all
    const forbidden = placed.filter(p => p.src === src);
    const pos = bestPosition(w, h, rot, W, H, placed, forbidden);

    // Shadow scales with z-rank: bottom scraps nearly flat, top scraps clearly lifted
    const rank    = scraps.length > 1 ? i / (scraps.length - 1) : 0.5;
    const dist    = baseDist * (0.3 + rank * 1.0);
    const blur    = +(baseBlur * (0.4 + rank * 0.9)).toFixed(1);
    const alpha   = (0.10 + rank * 0.45).toFixed(2);
    const shadowX = +(Math.cos(lightAngle) * dist).toFixed(1);
    const shadowY = +(Math.sin(lightAngle) * dist).toFixed(1);

    placed.push({ cx: pos.cx, cy: pos.cy, w, h, rot, src });
    renderScrap(bgContainer, src, pos.cx, pos.cy, w, h, rot, clipPath, fringePolygons, i + 1, shadowX, shadowY, blur, cropX, cropY, alpha);

    // Yield every 8 scraps — safe since bgWrapper is detached, no partial paint
    if (i > 0 && i % 8 === 0) await new Promise(r => setTimeout(r, 0));
  }

  // Site wrapper: fixed FG_W wide, CSS scale shrinks it below that breakpoint
  const fgScale = Math.min(1, W / FG_W);
  const siteWrapper = document.createElement('div');
  siteWrapper.id = 'fg-wrapper';
  siteWrapper.style.cssText = [
    `position:absolute`,
    `left:50%`,
    `transform-origin:top center`,
    `transform:translateX(-50%) scale(${fgScale})`,
    `width:${FG_W}px`,
    `height:100%`,
    `overflow:visible`,
  ].join(';');

  // Build site layer into detached siteWrapper
  await buildSiteLayer(siteWrapper, FG_W, H);

  // Reveal everything at once — single DOM swap so bg and site appear together
  container.innerHTML = '';
  container.style.height = `${H}px`;
  container.appendChild(bgWrapper);
  container.appendChild(siteWrapper);

  // Wait for site images to finish loading (fragment images are already preloaded)
  const siteImgs = [...container.querySelectorAll('.site-scrap img')];
  await Promise.all(siteImgs.map(img =>
    img.complete ? Promise.resolve() : new Promise(r => { img.onload = img.onerror = r; })
  ));
  // Two rAFs: first tick lets browser layout, second confirms paint
  await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
  hideSpinner();
}

// ── Events ────────────────────────────────────────────────────────────────────

function applyFgScale() {
  const wrapper = document.getElementById('fg-wrapper');
  if (!wrapper) return;
  const s = Math.min(1, window.innerWidth / FG_W);
  wrapper.style.transform = `translateX(-50%) scale(${s})`;
}

document.addEventListener('DOMContentLoaded', () => {
  buildCollage();
});

window.addEventListener('resize', applyFgScale);

