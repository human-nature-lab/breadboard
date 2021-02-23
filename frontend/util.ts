import RequireContext = __WebpackModuleApi.RequireContext
import { Exports, SimpleMap } from './client/types'
import { VueConstructor } from 'vue'

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

export function loadAllVueComponents (r: RequireContext) {
  const components: SimpleMap<Exports<VueConstructor>> = requireAll(r)
  for (const filename in components) {
    const parts = filename.split('/')
    const name = parts[parts.length - 1].replace('.vue', '')
    window.Vue.component(name, components[filename].default)
  }
}
