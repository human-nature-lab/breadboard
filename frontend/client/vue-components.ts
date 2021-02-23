import { loadAllVueComponents } from '../util'

loadAllVueComponents(require.context('./components', true, /\.vue$/))
