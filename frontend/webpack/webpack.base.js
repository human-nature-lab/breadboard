'use strict'
const webpack = require('webpack')
const path = require('path')
console.log(__dirname)
const buildPath = path.resolve(__dirname, '../../public/bundles/')

/**
 * Base configuration object for Webpack
 */
module.exports = {
  output: {
    path: buildPath,
    publicPath: '/bundles/'
  },
  module: {
    rules: [{
      test: /\.css$/,
      use: ['style-loader','css-loader?url=false'],
      // exclude: /node_modules/,
    }, {
      test: /\.js$/,
      use: {
        loader: 'babel-loader',
        options: {
          presets: ['@babel/preset-env']
        }
      },
      exclude: /node_modules/,
    }, {
      test: /\.tsx?$/,
      loader: 'ts-loader',
      exclude: /node_modules/,
      options: {
        appendTsSuffixTo: [/\.vue$/]
      }
    }, {
      test: /\.vue$/,
      use: 'vue-loader'
    }, {
      test: /\.(sass|scss)$/,
      use: ['vue-style-loader', 'css-loader?url=false', {
        loader: 'sass-loader',
        options: {
          implementation: require('sass'),
          fiber: require('fibers'),
          indentedSyntax: true
        }
      }],
      exclude: /node_modules/,
    }, {
      test: /\.html$/,
      use: ['ngtemplate-loader?relativeTo=frontend&prefix=files', 'html-loader'],
      exclude: /node_modules/,
    }, {
      test: /\.(jpg|png)$/,
      use: 'url-loader?limit=100000',
      exclude: /node_modules/,
    }, {
      test: /\.svg$/,
      use: 'url-loader?limit=10000&mimetype=image/svg+xml',
      exclude: /node_modules/,
    }, {
      test: /\.(eot|svg|ttf|woff|woff2)$/,
      use: 'file-loader?name=public/fonts/[name].[ext]'
    }]
  },
  resolve: {
    extensions: ['.js','.json','.css','.html', '.jsx', '.ts', '.tsx', '.vue']
  },
  plugins: [
    new webpack.ContextReplacementPlugin(
      /angular(\\|\/)core(\\|\/)@angular/,
      path.resolve(__dirname, './design')
    )
  ]
}
