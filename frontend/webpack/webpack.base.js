"use strict";
const webpack = require('webpack');
const path = require('path');
console.log(__dirname);
const buildPath = path.resolve(__dirname, '../../../public/bundles/');
const nodeModulesPath = path.resolve(__dirname, 'node_modules');
const LiveReloadPlugin = require('webpack-livereload-plugin');

/**
 * Base configuration object for Webpack
 */
module.exports = {
    output: {
        path: buildPath,
        publicPath: '/bundles/'
    },
    module: {
        rules: [
            {
                test: /\.css$/,
                use: ['style-loader','css-loader?url=false'],
                // exclude: /node_modules/,
            },
            {
                test: /\.js$/,
                use: ['babel-loader'],
                exclude: /node_modules/,
            },
            {
                test: /\.(sass|scss)$/,
                use: ['style-loader', 'css-loader?url=false', 'sass-loader'],
                exclude: /node_modules/,
            },
            {
                test: /\.html$/,
                use: ['ngtemplate-loader?relativeTo=frontend&prefix=files', 'html-loader'],
                exclude: /node_modules/,
            },
            {
                test: /\.(jpg|png)$/,
                use: 'url-loader?limit=100000',
                exclude: /node_modules/,
            },
            {
                test: /\.svg$/,
                use: 'url-loader?limit=10000&mimetype=image/svg+xml',
                exclude: /node_modules/,
            },
            {
                test: /\.(eot|svg|ttf|woff|woff2)$/,
                use: 'file-loader?name=public/fonts/[name].[ext]'
            }
        ]
    },
    resolve: {
        extensions: ['.js','.json','.css','.html', '.jsx']
    },
    plugins: [
        new webpack.ContextReplacementPlugin(
            /angular(\\|\/)core(\\|\/)@angular/,
            path.resolve(__dirname, './app')
        ),
        new webpack.ProvidePlugin({
            'fetch': 'imports?this=>global!exports?global.fetch!whatwg-fetch'
        }),
        new LiveReloadPlugin()
    ]
};