/**
 * Colour of route `i`. Hues are spaced by the golden angle (≈137.5°), so any two consecutive
 * routes are far apart on the colour wheel and no fixed palette runs out on large instances.
 * Lightness comes from a CSS custom property so light and dark themes can each pick a value
 * with enough contrast against their background.
 */
export const GOLDEN_ANGLE = 137.50776405003785;

export function routeHue(i: number): number {
  return (25 + i * GOLDEN_ANGLE) % 360;
}

export function routeColor(i: number): string {
  return `oklch(var(--route-l) var(--route-c) ${routeHue(i).toFixed(1)})`;
}

/** Smallest circular distance between two hues, in degrees. */
export function hueDistance(a: number, b: number): number {
  const d = Math.abs(a - b) % 360;
  return Math.min(d, 360 - d);
}
