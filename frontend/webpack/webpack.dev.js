const webpack = require('webpack')

const config = require('./webpack.base.js')

config.mode = 'development'
config.devtool = 'source-map'
config.plugins = [
    // new webpack.HotModuleReplacementPlugin()
]

const clientConfig = require('./webpack.client')
const designConfig = require('./webpack.design')
const coreConfig = require('./webpack.core')
module.exports = [clientConfig, designConfig, coreConfig]
