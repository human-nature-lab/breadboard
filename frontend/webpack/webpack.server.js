"use strict";
/**
 * Webpack server for development.
 */
let webpack = require('webpack');
let webpackDevServer = require('webpack-dev-server');
let webpackConfig = require('./webpack.dev.js');

//noinspection JSUnresolvedVariable
let webpackPath = __dirname;

// Notify about the path where the server is running
console.log('[Webpack] Server running at location: ' + webpackPath);

// First we fire up Webpack an pass in the configuration file
let bundleStart = null;
let compiler = webpack(webpackConfig);

// We give notice in the terminal when it starts bundling and
// set the time it started
compiler.compilers.forEach(comp => {
    comp.plugin('compile', function() {
        console.log('[Webpack] Bundling...');
        bundleStart = Date.now();
    });
    comp.plugin('done', function() {
        console.log('[Webpack] Bundled in ' + (Date.now() - bundleStart) + 'ms!');
    });
})

// We also give notice when it is done compiling, including the
// time it took. Nice to have


let server = new webpackDevServer(compiler, {

    // We need to tell Webpack to serve our bundled application
    // from the build path.
    publicPath: '/bundles/',

    // Configure hot replacement
    hot: true,

    // The rest is terminal configurations
    quiet: false,
    noInfo: true,
    stats: {
        colors: true
    }
});

// We fire up the development server and give notice in the terminal
// that we are starting the initial bundle
server.listen(8765, 'localhost', function () {
    console.log('[Webpack] Bundling project, please wait...');
});
