var config = require('./webpack.base.js');
config = Object.assign({}, config);
module.exports = Object.assign({}, config, {
  entry: ['babel-polyfill', './app/design.js'],
  output: {
  	path: config.output.path,
  	publicPath: config.output.publicPath,
    filename: 'design.js',
    sourceMapFilename: 'design.map'
  }
});