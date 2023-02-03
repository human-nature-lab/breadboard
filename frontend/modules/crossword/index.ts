import { loadAllVueComponents } from '../../client/src/util'
import Vue from 'vue'

loadAllVueComponents(Vue, require.context('./', true, /\.vue$/))