import type { BreadboardConfig, BreadboardClass, VueLoadOpts } from "@human-nature-lab/breadboard-core";
import DefaultView from "./mixins/DefaultView";

// Load Vue and optional dependencies
export async function loadVue(opts: VueLoadOpts) {
  opts = Object.assign({
    vueVersion: '2.6.11',
    vuetifyVersion: '2.3.7',
    mdiVersion: '5.4.55',
    useDev: false,
    withVuetify: true,
  }, opts)

  return async function (core: BreadboardClass, config: BreadboardConfig) {
    // this.addStyleFromURL('https://fonts.googleapis.com/css?family=Roboto:100,300,400,500,700,900')
    core.addStyleFromURL(`${config.assetsRoot}/bundles/client.css`)
    if (opts.withVuetify) {
      // this.addStyleFromURL(`https://cdn.jsdelivr.net/npm/@mdi/font@${opts.mdiVersion}/css/materialdesignicons.min.css`)
      // this.addStyleFromURL(`https://cdn.jsdelivr.net/npm/vuetify@${opts.vuetifyVersion}/dist/vuetify.min.css`)
    }
    window.Vue = (await import('vue')).default
    window.Vuetify = (await import('vuetify')).default
    await import(/* webpackChunkName: "vue-components" */'./vue-components')
    // await this.addScriptFromURL(`${config.assetsRoot}/bundles/vue-components.js`)
    core.addStyleFromURL(`${config.assetsRoot}/bundles/vue-components.css`)
    // await this.addScriptFromURL(`https://cdnjs.cloudflare.com/ajax/libs/vue/${opts.vueVersion}/vue.${opts.useDev ? 'common.dev.' : 'min.'}js`)
    // Register Vuetify components
    window.Vue.use(window.Vuetify)
  }
}

export function loadModules(...names: string[]) {
  return async function (core: BreadboardClass, config: BreadboardConfig) {
    const mods: PromiseLike<any>[] = []
    for (const name of names) {
      switch (name) {
        case 'llpg':
          mods.push(import(/* webpackChunkName: "llpg" */'../../modules/llpg'))
          break
        case 'chat':
          mods.push(import(/* webpackChunkName: "chat" */'../../modules/chat'))
          break
        case 'crossword':
          mods.push(import(/* webpackChunkName: "crossword" */'../../modules/crossword'))
          break
      }
      // mods.push(this.addScriptFromURL(`${config.assetsRoot}/bundles/modules/${name}.js`))
      // mods.push(this.addStyleFromURL(`${config.assetsRoot}/bundles/modules/${name}.css`).catch(err => console.log(err)))
    }
    await Promise.all(mods)
  }
}


/**
 * Create default Vue instance
 * @param template
 */
export async function createDefaultVue(template: string, mixin?: object) {
  const Vue = window.Vue
  const Vuetify = window.Vuetify
  const mixins = [DefaultView]
  if (mixin) {
    // @ts-ignore
    mixins.push(mixin)
  }
  return new Vue({
    vuetify: new Vuetify({
      icons: {
        iconfont: 'mdi'
      }
    }),
    mixins: mixins,
    template: template
  }).$mount('#app')
}


/**
 * Loads the legacy, angular.js client code. Replaces the SPA anchor with the old angular ng-app code
 */
export function loadAngularClient() {
  return async function (core: BreadboardClass, config: BreadboardConfig) {
    core.addStyleFromURL(`${config.assetsRoot}/bundles/client.css`)
    core.addStyleFromURL(`${config.assetsRoot}/bundles/client-angular.css`)
    core.addStyleFromURL('https://fonts.googleapis.com/css?family=Open+Sans:700,400')
    core.addStyleFromURL(`${config.assetsRoot}/css/bootstrap.min.css`)
    core.addStyleFromURL(`${config.assetsRoot}/css/font-awesome-4.7.0/css/font-awesome.min.css`)
    await Promise.all([
      core.addScriptFromURL('https://cdnjs.cloudflare.com/ajax/libs/jquery/1.7.2/jquery.min.js'),
      core.addScriptFromURL(`${config.assetsRoot}/bundles/client-angular.js`)
    ])
    await Promise.all([
      core.addScriptFromURL('https://cdnjs.cloudflare.com/ajax/libs/d3/2.10.0/d3.v2.js'),
      core.addScriptFromURL('https://cdnjs.cloudflare.com/ajax/libs/angular-ui-bootstrap/0.12.1/ui-bootstrap-tpls.js')
    ])
    // @ts-ignore
    const ang = angular; const init = window.bbClientInit
    ang.element(document).ready(function () {
      const t = document.createElement('div')
      const app = document.createElement('app')
      app.innerText = 'Loading...'
      document.body.setAttribute('ng-app', 'breadboard.client')
      const c = document.getElementById('app')
      if (!c) throw new Error('Unable to initialize app')
      document.body.replaceChild(app, c)
      init()
      ang.bootstrap(document, ['breadboard.client'])
    })
  }
}