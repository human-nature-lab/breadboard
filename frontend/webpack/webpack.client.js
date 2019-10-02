const merge = require('webpack-merge')
const config = require('./webpack.base.js')

function recursiveIssuer(m) {
  if (m.issuer) {
    return recursiveIssuer(m.issuer)
  } else if (m.name) {
    return m.name
  } else {
    return false
  }
}

const cacheGroups = ['client', 'vue-components', 'breadboard', 'design'].reduce((map, entry) => {
  map[entry + 'Styles'] = {
    name: entry,
    test: (m, c) => m.constructor.name === 'CssModule' && recursiveIssuer(m) === entry,
    chunks: 'all',
    enforce: false
  }
  return map
}, {})

module.exports = merge(Object.create(config), {
  entry: {
    client: './client/client.ts',
    'vue-components': './client/vue-components.ts',
    breadboard: './core/breadboard.ts',
    design: './design/design.js'
  },
  output: {
  	path: config.output.path,
  	publicPath: config.output.publicPath,
    filename: '[name].js'
  },
  optimization: {
    splitChunks: {
      cacheGroups: cacheGroups
    }
  },
})
