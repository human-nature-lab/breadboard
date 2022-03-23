import type  Vuetify from 'vuetify'
import type Vue from 'vue'

declare global {
  interface Window {
    Vuetify: Vuetify
    Vue: Vue
    BreadboardVueComponents: { name: string, component: Vue }[]
  }
}

export interface Exports<T> extends SimpleMap<T> {
  default: T
}

export type SimpleMap<T> = {
  [key: string]: T
}

