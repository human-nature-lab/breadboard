import RequireContext = __WebpackModuleApi.RequireContext
import { Exports, SimpleMap } from './client/types'

export function requireAll<T> (r: RequireContext) {
  const o: {[key: string]: Exports<T>} = {}
  for (let fileName of r.keys()) {
    o[fileName] = r(fileName)
  }
  return o
}

export function requireAllModules<T> (r: RequireContext): T[] {
  const modules: SimpleMap<Exports<T>> = requireAll(r)
  return Object.values(modules).map(m => m.default)
}
