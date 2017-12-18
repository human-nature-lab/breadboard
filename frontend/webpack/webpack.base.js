/* global __dirname */
"use strict";
const webpack = require('webpack');
const path = require('path');
console.log(__dirname);
const buildPath = path.resolve(__dirname, '../../../public/bundles/');
const nodeModulesPath = path.resolve(__dirname, 'node_modules');

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
                use: ['style-loader','css-loader'],
                exclude: /node_modules/,
            },
            {
                test: /\.[sass|scss]$/,
                use: ['style-loader', 'css-loader', 'sass-loader'],
                exclude: /node_modules/,
            },
            {
                test: /\.html$/,
                use: ['ngtemplate-loader', 'html-loader'],
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
            }
        ]
    },
    resolve: {
        extensions: ['.js','.json','.css','.html']
    },
    plugins: [
        new webpack.ContextReplacementPlugin(
            /angular(\\|\/)core(\\|\/)@angular/,
            path.resolve(__dirname, './app')
        ),
        new webpack.ProvidePlugin({
            'fetch': 'imports?this=>global!exports?global.fetch!whatwg-fetch'
        })
    ]
};