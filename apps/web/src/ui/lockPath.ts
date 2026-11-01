// Geometry for the circular lock: one continuous silhouette (body circle ∪ shackle), built as a
// single filled path so there is no seam where the shackle meets the body. Shared by the timer
// (Timer.tsx) and the wordmark/symbol build (scripts/build-wordmark.mjs).

export interface LockGeometry {
  /** Body centre and radius. */
  cx: number; cy: number; r: number;
  /** Shackle: outer half-width, stroke weight, and top of the outer curve (closed position). */
  ro: number; stroke: number; top: number;
}

const f = (n: number) => Number(n.toFixed(2));

/**
 * The lock silhouette as SVG path data, filled with the even-odd rule.
 * Closed: outer outline plus the opening inside the shackle.
 * Open: the shackle lifted by `lift`; its left leg still joins the body and the right leg ends
 * in a rounded tip above it (the opening is then part of the outside).
 */
export function lockSilhouette(g: LockGeometry, open = false, lift = 0): string {
  const { cx, cy, r, ro, stroke } = g;
  const ri = ro - stroke;
  const arcY = g.top - (open ? lift : 0) + ro;            // centre of the shackle's curve
  const yi = cy - Math.sqrt(r * r - ri * ri);              // inner legs meet the body
  const L = cx - ro, Li = cx - ri, Ri = cx + ri, Ro = cx + ro;
  // Where an outer leg meets the body, a small concave fillet lets the shackle flow into the
  // circle: a circle of radius k tangent to the leg and to the body.
  const k = stroke * 0.4;
  const fy = cy - Math.sqrt((r + k) ** 2 - (ro + k) ** 2); // fillet centre height (and the leg's tangent point)
  const t = r / (r + k);
  const tx = (ro + k) * t, ty = (cy - fy) * t;              // tangent point on the body, relative to its centre
  const bodyL = `${f(cx - tx)} ${f(cy - ty)}`, bodyR = `${f(cx + tx)} ${f(cy - ty)}`;
  // Start on the body at the left fillet, round the bottom to the right fillet, up the right leg.
  const around = `M${bodyL}A${f(r)} ${f(r)} 0 1 0 ${bodyR}A${f(k)} ${f(k)} 0 0 1 ${f(Ro)} ${f(fy)}`;
  const leftUp = `A${f(k)} ${f(k)} 0 0 1 ${bodyL}Z`;      // from the left leg back onto the body
  if (!open) {
    const outer = `${around}V${f(arcY)}A${f(ro)} ${f(ro)} 0 0 0 ${f(L)} ${f(arcY)}V${f(fy)}${leftUp}`;
    const hole = `M${f(Li)} ${f(yi)}V${f(arcY)}A${f(ri)} ${f(ri)} 0 0 1 ${f(Ri)} ${f(arcY)}V${f(yi)}A${f(r)} ${f(r)} 0 0 0 ${f(Li)} ${f(yi)}Z`;
    return outer + hole;
  }
  // Open: the right leg is lifted clear and ends in a rounded tip; only the left leg joins.
  const yo = cy - Math.sqrt(r * r - ro * ro);
  const tip = Math.min(arcY + stroke * 1.1, yo - stroke * 0.9);
  const cap = stroke / 2;
  return `M${bodyL}A${f(r)} ${f(r)} 0 1 0 ${f(Li)} ${f(yi)}V${f(arcY)}A${f(ri)} ${f(ri)} 0 0 1 ${f(Ri)} ${f(arcY)}`
    + `V${f(tip)}A${f(cap)} ${f(cap)} 0 0 0 ${f(Ro)} ${f(tip)}V${f(arcY)}A${f(ro)} ${f(ro)} 0 0 0 ${f(L)} ${f(arcY)}V${f(fy)}${leftUp}`;
}

/** A full circle as path data (for knock-outs). */
export function circlePath(cx: number, cy: number, r: number): string {
  return `M${f(cx + r)} ${f(cy)}A${f(r)} ${f(r)} 0 1 0 ${f(cx - r)} ${f(cy)}A${f(r)} ${f(r)} 0 1 0 ${f(cx + r)} ${f(cy)}Z`;
}
