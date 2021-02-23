import { loadAllVueComponents } from '../../util'

loadAllVueComponents(require.context('./', true, /\.vue$/))