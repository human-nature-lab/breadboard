const designConfig = require('./webpack.design')
const clientConfig = require('./webpack.client')
const coreConfig = require('./webpack.core')

// TODO: add minification and prod stuff for client here

module.exports = [coreConfig, clientConfig, designConfig];
