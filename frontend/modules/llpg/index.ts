import { loadAllVueComponents } from '../../client/src/util'
import 'drag-drop-touch'
import 'tailwindcss/dist/tailwind.css'

loadAllVueComponents(require.context('./', true, /\.vue$/))