import type  Vuetify from 'vuetify'
import type Vue from 'vue'
import { createDefaultVue, loadAngularClient, loadModules, loadVue, loadVueDependencies } from './client'

declare global {
  interface Window {
    Vuetify: typeof Vuetify
    Vue: typeof Vue
    BreadboardVueComponents: { name: string, component: Vue }[]
    loadVue: typeof loadVue
    loadVueDependencies: typeof loadVueDependencies
    createDefaultVue: typeof createDefaultVue
    loadModules: typeof loadModules
    loadAngularClient: typeof loadAngularClient
    bbClientInit: () => void
  }
}

export interface Exports<T> extends SimpleMap<T> {
  default: T
}

export type SimpleMap<T> = {
  [key: string]: T
}

