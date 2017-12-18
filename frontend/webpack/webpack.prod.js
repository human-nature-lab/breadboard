const designConfig = require('./webpack.design.js');
const clientConfig = require('./webpack.client.js');

// TODO: add minification and prod stuff for client here

// clientConfig.entry = ['webpack/hot/dev-server', 'webpack-dev-server/client?http://localhost:8080'].concat(clientConfig.entry);

module.exports = [clientConfig, designConfig];
console.log(module.exports[0].module.rules, module.exports[1].module.rules);