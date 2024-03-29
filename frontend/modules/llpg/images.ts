// @ts-ignore
const rootUrl = `/images/${window.experimentId || 233}`
export const images = {
  envelope: {
    closed: '/envelope-closed.png',
    openBack: '/envelope-open-back.png',
    openFront: '/envelope-open-front.png'
  },
  box: {
    back: '/box-back.png',
    front: '/box-front.png',
    closed: '/box-closed.png',
    lid: '/box-lid.png'
  },
  currency: {
    single: '/lempira-front.png',
    stack: '/lempira-front.png'
  },
  lock: {
    open: '/lock-open.png',
    closed: '/lock-closed.png'
  },
  basket: {
    back: '/basket-back.png',
    front: '/basket-front.png'
  },
  corn: '/corn.png',
  banana: '/banana.png',
  wallet: {
    open: '/wallet-open.png',
    closed: '/wallet-closed.png'
  },
  person: '/silhouette.png'
}

function expandImageUrls(obj: any) {
  for (const key in obj) {
    if (typeof obj[key] === 'object') {
      expandImageUrls(obj[key])
    } else {
      obj[key] = rootUrl + obj[key]
    }
  }
}

expandImageUrls(images)
console.log('images', images)
