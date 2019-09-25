let config = require('./webpack.base.js')
config = Object.assign({}, config)
module.exports = Object.assign({}, config, {
  entry: ['./core/breadboard.ts'],
  output: {
    path: config.output.path,
    publicPath: config.output.publicPath,
    filename: 'breadboard.js'
  }
})
