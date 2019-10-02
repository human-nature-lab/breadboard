const clientConfig = require('./webpack.client')

clientConfig.mode = 'development'
clientConfig.devtool = 'source-map'

// const designConfig = require('./webpack.design')
// const coreConfig = require('./webpack.core')
module.exports = [clientConfig]
