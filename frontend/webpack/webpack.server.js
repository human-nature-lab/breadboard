'use strict'
const webpack = require('webpack')
const webpackDevServer = require('webpack-dev-server')
const webpackConfig = require('./webpack.dev.js')

// Notify about the path where the server is running
console.log('[Webpack] Server running at location: ' + __dirname)

const PORT = 8765

// We also give notice when it is done compiling, including the
// time it took. Nice to have

const server = new webpackDevServer(webpack(webpackConfig), {
    // We need to tell Webpack to serve our bundled application
    // from the build path.
    publicPath: '/bundles/',
    sockPort: PORT,

    // Configure hot replacement
    hot: true,
    liveReload: false,
    writeToDisk: false,

    // The rest is terminal configurations
    quiet: false,
    noInfo: false,
    stats: {
        colors: true
    }
})

// We fire up the development server and give notice in the terminal
// that we are starting the initial bundle
server.listen(PORT, 'localhost', function () {
    console.log('[Webpack] Bundling project, please wait...')
})
