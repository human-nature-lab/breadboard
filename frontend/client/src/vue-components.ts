import { loadAllVueComponents } from './util'
import 'vuetify/dist/vuetify.min.css'
import '@mdi/font/css/materialdesignicons.css'
import 'typeface-roboto/index.css'

import Vue from 'vue'
import Vuetify from 'vuetify'

// @ts-ignore
window.Vue = Vue
// @ts-ignore
window.Vuetify = Vuetify

loadAllVueComponents(require.context('./components', true, /\.vue$/))
