'use strict'
const webpack = require('webpack')
const webpackDevServer = require('webpack-dev-server')
const webpackConfig = require('./webpack.dev.js')

// Notify about the path where the server is running
console.log('[Webpack] Server running at location: ' + __dirname)

const PORT = 8765

// First we fire up Webpack an pass in the configuration file
const compiler = webpack(webpackConfig)

// We give notice in the terminal when it starts bundling and
// set the time it started
compiler.compilers.forEach(comp => {
    let bundleStart
    comp.plugin('compile', function() {
        console.log('[Webpack] Bundling...')
        bundleStart = Date.now()
    })
    comp.plugin('done', function() {
        console.log('[Webpack] Bundled in ' + (Date.now() - bundleStart) + 'ms!')
    })
})

// We also give notice when it is done compiling, including the
// time it took. Nice to have


const server = new webpackDevServer(compiler, {

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
