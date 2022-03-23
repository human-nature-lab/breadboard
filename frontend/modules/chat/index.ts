import { loadAllVueComponents } from '../../client/src/util'

loadAllVueComponents(require.context('./', true, /\.vue$/))