export function heartbeat () {
  let c = randomInt(0, 1000)
  setInterval(() => {
    c++
    if (c % 10 === 0 && Breadboard && Breadboard.send) {
      Breadboard.send('heartbeat', { c })
    }
  }, 500)
}

export function trackPageVisibility () {
  Breadboard.send('page-visibility', { hidden: document.hidden, initial: true })
  document.addEventListener('visibilitychange', () => {
    Breadboard.send('page-visibility', { hidden: document.hidden })
  }, false)
}

export function randomInt (min: number, max: number) {
  return Math.floor(Math.random() * (max - min) + min)
}
