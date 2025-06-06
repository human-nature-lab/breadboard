export type Listener<Args extends any[], R = any> = (...args: Args) => R

export class Hook<Args extends any[] = any[], R = void> {
  private listeners: Listener<Args, R>[] = []

  /**
   * Add a listerner to this hook
   * @param cb a function that is called when this hook fires
   * @returns an unsubscribe function
   */
  add (cb: Listener<Args, R>) {
    this.listeners.push(cb)
    return () => {
      this.remove(cb)
    }
  }

  /**
   * Emit this hook
   * @param args
   */
  emit (...args: Args) {
    for (const cb of this.listeners) {
      cb(...args)
    }
  }

  /**
   *  remove a listener from this hook
   * @param cb a reference to the function that was added by "add"
   */
  remove (cb: Listener<Args, R>) {
    const index = this.listeners.indexOf(cb)
    if (index > -1) {
      this.listeners.splice(index, 1)
    }
  }
}

export function LogHooks (hooks: Record<string | number | symbol, Hook>) {
  for (const name in hooks) {
    const hook = hooks[name]
    hook.add(function (...args: any[]) {
      console.log(name, args)
    })
  }
}