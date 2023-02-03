import { loadAllVueComponents } from '../../client/src/util'
import 'drag-drop-touch'
import 'tailwindcss/dist/tailwind.css'
import Vue from 'vue'

loadAllVueComponents(Vue, require.context('./', true, /\.vue$/))