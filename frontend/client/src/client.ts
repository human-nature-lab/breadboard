import {
  Breadboard,
  BreadboardConfig,
  BreadboardClass,
  VueLoadOpts,
} from '@human-nature-lab/breadboard-core'
import DefaultView from './mixins/DefaultView'
import './client.sass'
window.Breadboard = Breadboard

// Legacy client graphs call `Breadboard.loadVueDependencies(opts)`. Keep it working
// by wrapping the new `window.loadVue` + `Breadboard.load` flow.
export function loadVueDependencies(opts: VueLoadOpts) {
  return Breadboard.load(loadVue(opts))
}

async function client() {
  let config: BreadboardConfig
  try {
    config = await Breadboard.loadConfig()
  } catch (err) {
    console.error('Breadboard: Unable to load Breadboard')
    throw err
  }

  try {
    window.loadVue = loadVue
    window.loadVueDependencies = loadVueDependencies
    window.createDefaultVue = createDefaultVue
    window.loadAngularClient = loadAngularClient
    window.loadModules = loadModules
    // Backwards compatibility: legacy client graphs reference these as methods on
    // the global `Breadboard` object rather than on `window`.
    //@ts-ignore
    Breadboard.loadVueDependencies = loadVueDependencies
    //@ts-ignore
    Breadboard.createDefaultVue = createDefaultVue
    //@ts-ignore
    Breadboard.loadAngularClient = loadAngularClient
    //@ts-ignore
    Breadboard.loadModules = loadModules
    await Breadboard.addScriptFromString(config.clientGraph)
  } catch (err) {
    console.error('Breadboard: Unable to run client-graph.js')
    throw err
  }
}

client()

// Load Vue and optional dependencies
export function loadVue(opts: VueLoadOpts) {
  opts = Object.assign(
    {
      vueVersion: '2.6.11',
      vuetifyVersion: '2.3.7',
      mdiVersion: '5.4.55',
      useDev: false,
      withVuetify: true,
    },
    opts,
  )

  return async function (core: BreadboardClass, config: BreadboardConfig) {
    // Load Vue, Vuetify and the Vue components in parallel
    const imports: Promise<any>[] = [import('vue')]
    if (opts.withVuetify) {
      imports.push(import('vuetify'))
      // In production the sass is extracted into client.css by MiniCssExtractPlugin
      // and has to be pulled in via a <link>. In dev there is no extracted file —
      // vue-style-loader injects the styles through JS when the modules load — so
      // requesting client.css from the dev server 404s and rejects the whole load.
      if (process.env.NODE_ENV === 'production') {
        imports.push(core.addStyleFromURL(`${config.assetsRoot}/bundles/client.css`))
      }
    }
    imports.push(import(/* webpackChunkName: "vue-components" */ './vue-components') as any)
    const res = await Promise.all(imports)
    //@ts-ignore
    window.Vue = res[0].default
    if (opts.useDev) {
      window.Vue.config.devtools = true
    }
    if (opts.withVuetify) {
      window.Vuetify = res[1].default
      // Register Vuetify components
      window.Vue.use(window.Vuetify)
    }
  }
}

export function loadModules(...names: string[]) {
  return async function (core: BreadboardClass, config: BreadboardConfig) {
    // const mods: PromiseLike<any>[] = []
    // for (const name of names) {
    //   switch (name) {
    //     case 'llpg':
    //       mods.push(import(/* webpackChunkName: "llpg" */'../../modules/llpg'))
    //       break
    //     case 'chat':
    //       mods.push(import(/* webpackChunkName: "chat" */'../../modules/chat'))
    //       break
    //     case 'crossword':
    //       mods.push(import(/* webpackChunkName: "crossword" */'../../modules/crossword'))
    //       break
    //   }
    //   // mods.push(this.addScriptFromURL(`${config.assetsRoot}/bundles/modules/${name}.js`))
    //   // mods.push(this.addStyleFromURL(`${config.assetsRoot}/bundles/modules/${name}.css`).catch(err => console.log(err)))
    // }
    // await Promise.all(mods)
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
        iconfont: 'mdi',
      },
    }),
    mixins: mixins,
    template: template,
  }).$mount('#app')
}

/**
 * Loads the legacy, angular.js client code. Replaces the SPA anchor with the old angular ng-app code
 */
export function loadAngularClient() {
  return async function (core: BreadboardClass, config: BreadboardConfig) {
    core.addStyleFromURL(`${config.assetsRoot}/bundles/client.css`)
    core.addStyleFromURL(`${config.assetsRoot}/bundles/client-angular.css`)
    core.addStyleFromURL(
      'https://fonts.googleapis.com/css?family=Open+Sans:700,400',
    )
    core.addStyleFromURL(`${config.assetsRoot}/css/bootstrap.min.css`)
    core.addStyleFromURL(
      `${config.assetsRoot}/css/font-awesome-4.7.0/css/font-awesome.min.css`,
    )
    await Promise.all([
      core.addScriptFromURL(
        'https://cdnjs.cloudflare.com/ajax/libs/jquery/1.7.2/jquery.min.js',
      ),
      core.addScriptFromURL(`${config.assetsRoot}/bundles/client-angular.js`),
    ])
    await Promise.all([
      core.addScriptFromURL(
        'https://cdnjs.cloudflare.com/ajax/libs/d3/2.10.0/d3.v2.js',
      ),
      core.addScriptFromURL(
        'https://cdnjs.cloudflare.com/ajax/libs/angular-ui-bootstrap/0.12.1/ui-bootstrap-tpls.js',
      ),
    ])
    // @ts-ignore
    const ang = angular
    const init = window.bbClientInit
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
