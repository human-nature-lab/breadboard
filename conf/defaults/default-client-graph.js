async function init () {
  await Breadboard.load(window.loadVue({ useDev: true }))
  Breadboard.load((core, config) => {
    window.createDefaultVue(config.clientHtml)
  })
}

init()