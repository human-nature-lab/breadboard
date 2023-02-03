import { Transform } from "./steps";

export function boxLayout (i: number, maxRow = 5, zIndex = 0): Transform & { zIndex: number } {
  const zDepth = Math.ceil(maxRow / 2)
  const xOffset = 35
  const yOffset = 120
  let x, y, z
  if (i <= 5) {
    x = i * 33 + xOffset
    y = 20 * arc(i - 1) + yOffset
    z = -zDepth * arc(i - 1)
  } else if (i <= 10) {
    i -= 5
    x = (i - 1) * 30 + xOffset + 10
    y = 20 * arc(i - 1) + yOffset - 10
    z = zDepth - zDepth * arc(i - 1)
  } else {
    i -= 10
    x = (i - 1) * 25 + xOffset + 15
    y = 20 * arc(i - 1) + yOffset - 20
    z = 2 * zDepth - zDepth * arc(i - 1)
  }
  return { x, y, zIndex: Math.round(z) + zIndex }
}

function arc (x: number) {
  return Math.sin(Math.PI * x / 5)
}