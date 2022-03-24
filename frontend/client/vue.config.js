module.exports = {
  chainWebpack (config) {
    // config.module.rule('ts').use('ts-loader').options({ transpileOnly: true, appendTsSuffixTo: [/\.vue$/] })
  },
  configureWebpack: {
    // output: {
    //   libraryExport: 'default'
    // }
  },
  transpileDependencies: [
    'vuetify'
  ],
}
