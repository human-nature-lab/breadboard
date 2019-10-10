import { configurable } from 'gremlins-ts/src/utils/configurable'

interface TargetedClickerConfig {
  randomizer?: any
  showAction? (...a: any[]): any
  logger?: null | typeof console
}

function defaultShowAction (x: number, y: number) {
  var clickSignal = document.createElement('div')
  clickSignal.style.zIndex = '2000'
  clickSignal.style.border = "3px solid red"
  // clickSignal.style['border-radius'] = '50%' // Chrome
  clickSignal.style.borderRadius = '50%'     // Mozilla
  clickSignal.style.width = "40px"
  clickSignal.style.height = "40px"
  // clickSignal.style['box-sizing'] = 'border-box'
  clickSignal.style.position = "absolute"
  // clickSignal.style.webkitTransition = 'opacity 1s ease-out'
  // clickSignal.style.mozTransition = 'opacity 1s ease-out'
  clickSignal.style.transition = 'opacity 1s ease-out'
  clickSignal.style.left = (x - 20 ) + 'px'
  clickSignal.style.top = (y - 20 )+ 'px'
  var element = document.body.appendChild(clickSignal)
  setTimeout(function() {
    document.body.removeChild(element)
  }, 1000)
  setTimeout(function() {
    element.style.opacity = '0'
  }, 50)
}

export default function targetedClickerGremlin (tags: string[] = ['button', 'input']) {
  const config: TargetedClickerConfig = {
    randomizer: null,
    showAction: defaultShowAction,
    logger: null
  }
  function run() {

    if (!config.randomizer) {
      throw new Error('Need a randomizer')
    }

    let elements: HTMLElement[] = []
    for (const tag of tags) {
      elements = elements.concat(Array.from(document.querySelectorAll(tag)))
    }

    if (!elements.length) {
      return false
    }

    const element = config.randomizer.pick(elements)
    const box = element.getBoundingClientRect()
    const evt = document.createEvent('MouseEvents')
    const posX = (box.left + box.right) / 2
    const posY = (box.top + box.bottom) / 2
    evt.initMouseEvent('click', true, true, window, 0, 0, 0, posX, posY, false ,false, false, false, 0, null)
    element.dispatchEvent(evt)
    if (typeof config.showAction === 'function') {
      config.showAction(posX, posY, 'click')
    }
    if (config.logger && typeof config.logger.log === 'function') {
      config.logger.log('targeted clicker gremlin   ', 'click', element.tagName, posX, posY)
    }
  }

  configurable(run, config)

  return run
}



