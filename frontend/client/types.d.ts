import { BreadboardClass } from '../core/breadboard'
import { Vuetify } from 'vuetify'
import { VueConstructor } from 'vue/types/vue'

declare global {
  interface Window {
    Breadboard: BreadboardClass
    Vuetify: Vuetify
    Vue: VueConstructor
    BreadboardVueComponents: { name: string, component: VueConstructor }[]
  }
}

export interface Exports<T> extends SimpleMap<T> {
  default: T
}

export type SimpleMap<T> = {
  [key: string]: T
}

