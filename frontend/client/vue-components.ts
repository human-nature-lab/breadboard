import { requireAll } from '../util'
import { Exports, SimpleMap } from './types'
import { VueConstructor } from 'vue/types/vue'

const components: SimpleMap<Exports<VueConstructor>> = requireAll(require.context('./components', true, /\.vue$/))
const gameComponents: SimpleMap<Exports<VueConstructor>> = requireAll(require.context('../games/', true, /\.vue$/))

const allComponents = Object.assign(components, gameComponents)
window.BreadboardVueComponents = Object.keys(allComponents).map(filename => {
  const parts = filename.split('/')
  return {
    name: parts[parts.length - 1].replace('.vue', ''),
    component: allComponents[filename].default
  }
})
