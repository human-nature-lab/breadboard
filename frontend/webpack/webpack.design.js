const merge = require('webpack-merge')
const config = require('./webpack.base.js')
module.exports = merge(Object.create(config), {
  entry: ['./design/design.js'],
  output: {
  	path: config.output.path,
  	publicPath: config.output.publicPath,
    filename: 'design.js',
    sourceMapFilename: 'design.map'
  }
});
