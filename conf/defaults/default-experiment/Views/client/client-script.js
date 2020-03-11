async function init () {
  // Breadboard.connect() // Not required, but this slightly speeds up client's appearance in the graph. Using this will make styles not load correctly.
  const config = await Breadboard.loadConfig()
  await Breadboard.loadVueDependencies({
    useDev: true
  })
  await Breadboard.createDefaultVue(config.clientHtml)
}

init()