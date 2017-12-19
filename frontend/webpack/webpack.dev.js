/* global __dirname */
const path = require('path');
const webpack = require('webpack');

const config = Object.create(require('./webpack.base.js'));

config.devtool = 'source-map';

config.plugins = [
    // new webpack.ContextReplacementPlugin(
    //     /angular(\\|\/)core(\\|\/)@angular/,
    //     path.resolve(__dirname, './app')
    // ),
    new webpack.HotModuleReplacementPlugin(),
    new webpack.ProvidePlugin({
        'fetch': 'imports?this=>global!exports?global.fetch!whatwg-fetch'
    })
];

const clientConfig = Object.assign({}, config, require('./webpack.client.js'));
const designConfig = Object.assign({}, config, require('./webpack.design.js'));
module.exports = [clientConfig, designConfig].map(c => {
    c.entry = ['webpack/hot/dev-server', 'webpack-dev-server/client?http://localhost:8080'].concat(c.entry);
    return c;
});