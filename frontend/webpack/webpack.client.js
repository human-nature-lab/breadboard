let config = require('./webpack.base.js');
config = Object.assign({}, config);
module.exports = Object.assign({}, config, {
  entry: ['./app/client.js'],
  output: {
  	path: config.output.path,
  	publicPath: config.output.publicPath,
    filename: 'client.js',
    sourceMapFilename: 'client.map'
  }
});
