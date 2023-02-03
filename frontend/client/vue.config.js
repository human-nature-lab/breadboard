const isProd = process.env.NODE_ENV === 'production'
module.exports = {
  parallel: !isProd,
  chainWebpack (config) {
    // config.module.rule('ts').use('ts-loader').options({ transpileOnly: true, appendTsSuffixTo: [/\.vue$/] })
    if(isProd) {
      config.module.rule("ts").uses.delete("cache-loader");

      config.module
        .rule('ts')
        .use('ts-loader')
        .loader('ts-loader')
        .tap(opts => {
          opts.transpileOnly = false;
          opts.happyPackMode = false;
          return opts;
        });
    }
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
