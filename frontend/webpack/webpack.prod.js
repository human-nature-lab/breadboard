const designConfig = require('./webpack.design.js');
const clientConfig = require('./webpack.client.js');

// TODO: add minification and prod stuff for client here

module.exports = [clientConfig, designConfig];