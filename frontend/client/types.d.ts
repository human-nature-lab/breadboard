import { BreadboardClass } from '../core/breadboard'
import { Vuetify } from 'vuetify'
import Vue from 'vue'

declare global {
  interface Window {
    Breadboard: BreadboardClass
    Vuetify: Vuetify
    Vue: typeof Vue
    BreadboardVueComponents: { name: string, component: typeof Vue }[]
  }
}

export interface Exports<T> extends SimpleMap<T> {
  default: T
}

export type SimpleMap<T> = {
  [key: string]: T
}

