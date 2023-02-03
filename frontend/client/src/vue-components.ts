import { loadAllVueComponents } from './util'
import 'vuetify/dist/vuetify.min.css'
import '@mdi/font/css/materialdesignicons.css'
import 'typeface-roboto/index.css'
import Vue from 'vue'

loadAllVueComponents(Vue, require.context('./components', true, /\.vue$/))
