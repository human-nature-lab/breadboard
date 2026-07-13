// We want to track screen size changes, aspect ratios, zooms, orientation changes, etc and send them to the backend
// additionally, we want to track visibility changes and send them as well

function extractScreenInfo (selectors: Record<string, string> = {}) {
  const data: Record<string, any> = {
    change: 'initial',
    width: window.innerWidth,
    height: window.innerHeight,
    devicePixelRatio: window.devicePixelRatio,
    windowScrollPosition: window.scrollY,
    documentHidden: document.hidden,
    documentScrollTop: document.documentElement.scrollTop,
    documentScrollLeft: document.documentElement.scrollLeft,
    // orientation: screen.orientation.type, // The orientation of the device
    // documentHidden: document.hidden, // Whether the browser detected a hidden tab/window
  }
  if (screen && screen.orientation) {
    data.screenOrientation = screen.orientation.type
  }
  if (window.visualViewport) {
    data.visualViewportWidth = window.visualViewport.width
    data.visualViewportHeight = window.visualViewport.height
    data.visualViewportScale = window.visualViewport.scale
  }
  if (selectors) {
    for (const [key, value] of Object.entries(selectors)) {
      const element = document.querySelector(value)
      if (element) {
        const rect = element.getBoundingClientRect()
        data[key] = {
          width: rect.width,
          height: rect.height,
          top: rect.top,
          left: rect.left,
          right: rect.right,
          bottom: rect.bottom,
        }
      }
    }
  }
  return data
}

// Extract relevant screen information and send it to the backend
export function trackScreen (notify: (data: Record<string, any>) => void, selectors: Record<string, string> = {}) {
  const initialData = extractScreenInfo(selectors)
  initialData.change = 'initial'
  notify(initialData)
  window.addEventListener('resize', () => {
    const data = extractScreenInfo(selectors)
    data.change = 'window-resize'
    notify(data)
  })
  screen.orientation.addEventListener('change', () => {
    const data = extractScreenInfo(selectors)
    data.change = 'orientation'
    notify(data)
  })
  document.addEventListener('visibilitychange', () => {
    const data = extractScreenInfo(selectors)
    data.change = 'visibility'
    notify(data)
  }, false)
  window.addEventListener('scroll', () => {
    const data = extractScreenInfo(selectors)
    data.change = 'scroll'
    notify(data)
  }, false)
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', () => {
      const data = extractScreenInfo(selectors)
      data.change = 'visual-viewport-resize'
      notify(data)
    }, false)
    window.visualViewport.addEventListener('scroll', () => {
      const data = extractScreenInfo(selectors)
      data.change = 'visual-viewport-scroll'
      notify(data)
    }, false)
    window.visualViewport.addEventListener('scrollend', () => {
      const data = extractScreenInfo(selectors)
      data.change = 'visual-viewport-scrollend'
      notify(data)
    }, false)
  }
  return () => extractScreenInfo(selectors)
}
