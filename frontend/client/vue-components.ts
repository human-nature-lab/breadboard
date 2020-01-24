import { requireAll } from '../util'
import { Exports, SimpleMap } from './types'
import { VueConstructor } from 'vue/types/vue'

const components: SimpleMap<Exports<VueConstructor>> = requireAll(require.context('./components', true, /\.vue$/))

window.BreadboardVueComponents = Object.keys(components).map(filename => {
  const parts = filename.split('/')
  return {
    name: parts[parts.length - 1].replace('.vue', ''),
    component: components[filename].default
  }
})
