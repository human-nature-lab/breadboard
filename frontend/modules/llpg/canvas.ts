type Point = { x: number, y: number }

const defaultArrowOpts = {
  aLength: 20,
  width: 10,
  arrowStart: false,
  arrowEnd: true
}
export function drawLineWithArrows(ctx: CanvasRenderingContext2D, a: Point, b: Point, options: Partial<typeof defaultArrowOpts> = {}){
  const opts: typeof defaultArrowOpts = Object.assign({}, defaultArrowOpts, options)
  const dx = b.x - a.x
  const dy = b.y - a.y
  const angle = Math.atan2(dy, dx)
  const length = Math.sqrt(dx*dx + dy*dy)
  ctx.translate(a.x, a.y)
  ctx.rotate(angle)
  ctx.beginPath()
  ctx.moveTo(0, 0)
  ctx.lineTo(length, 0)
  if(opts.arrowStart){
      ctx.moveTo(opts.aLength, -opts.width)
      ctx.lineTo(0,0)
      ctx.lineTo(opts.aLength, opts.width)
  }
  if(opts.arrowEnd){
      ctx.moveTo(length - opts.aLength, -opts.width)
      ctx.lineTo(length,0)
      ctx.lineTo(length - opts.aLength, opts.width)
  }
  ctx.stroke()
  ctx.setTransform(1,0,0,1,0,0)
}

export function elementCenterPoint (el: HTMLElement): Point {
  const box = el.getBoundingClientRect()
  return {
    x: (box.right + box.left) / 2,
    y: (box.top + box.bottom) / 2
  }
}
