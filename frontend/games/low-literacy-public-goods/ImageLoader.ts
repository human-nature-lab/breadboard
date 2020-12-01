export class ImageLoader {
  private srcMap = new Map<string, HTMLImageElement>()
  private pendingMap = new Map<string, Promise<HTMLImageElement>>()

  constructor (sources: string[] = []) {
    this.preload(sources)
  }
  
  preload (sources: string[]) {
    for (const src of sources) {
      this.load(src)
    }
  }
  
  load (src: string): Promise<HTMLImageElement> {
    const p = new Promise<HTMLImageElement>(async (resolve, reject) => {
      if (this.srcMap.has(src)) {
        console.log('loaded', src)
        return resolve(this.srcMap.get(src))
      }
      if (this.pendingMap.has(src)) {
        const existing = this.pendingMap.get(src)
        console.log('pending', src, existing)
        await existing
        if (this.srcMap.has(src)) {
          return resolve(this.srcMap.get(src))
        } else {
          return reject('An error occurred when loading this previously ' + src)
        }
      }
      const image = new Image()
      console.log('loading', src)
      image.addEventListener('load', () => {
        this.pendingMap.delete(src)
        this.srcMap.set(src, image)
        console.log('done', src)
        resolve(image)
      })
      image.addEventListener('error', err => {
        console.log('error', src)
        this.pendingMap.delete(src)
        reject(err)
      })
      image.src = src
    })
    this.pendingMap.set(src, p)
    return p
  }
}