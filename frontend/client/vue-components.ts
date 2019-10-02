import { requireAll } from '../util'
import { Exports, SimpleMap } from './types'
import { VueConstructor } from 'vue/types/vue'

const components: SimpleMap<Exports<VueConstructor>> = requireAll(require.context('./components', false, /\.vue$/))

window.BreadboardVueComponents = Object.keys(components).map(filename => ({
  name: filename.replace('.vue', '').replace('./', ''),
  component: components[filename].default
}))
