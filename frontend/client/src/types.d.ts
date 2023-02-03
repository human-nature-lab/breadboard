import type  Vuetify from 'vuetify'
import type Vue from 'vue'
import { createDefaultVue, loadAngularClient, loadModules, loadVue } from './client'

declare global {
  interface Window {
    Vuetify: typeof Vuetify
    Vue: typeof Vue
    BreadboardVueComponents: { name: string, component: Vue }[]
    loadVue: typeof loadVue
    createDefaultVue: typeof createDefaultVue
    loadModules: typeof loadModules
    loadAngularClient: typeof loadAngularClient
  }
}

export interface Exports<T> extends SimpleMap<T> {
  default: T
}

export type SimpleMap<T> = {
  [key: string]: T
}

