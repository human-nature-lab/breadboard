'use strict'
const webpack = require('webpack')
const path = require('path')
const VueLoaderPlugin = require('vue-loader/lib/plugin')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
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
      test: /\.vue$/,
      loader: 'vue-loader'
    }, {
      test: /\.(sass|scss)$/,
      use: [MiniCssExtractPlugin.loader,
        'css-loader?url=false',
        {
          loader: 'sass-loader',
          options: {
            implementation: require('sass'),
            fiber: require('fibers'),
            indentedSyntax: true
          }
        }],
      exclude: /node_modules/,
    }, {
      test: /\.css$/,
      use: ['style-loader','css-loader?url=false'],
      exclude: /node_modules/,
    }, {
      test: /\.tsx?$/,
      use: [{
        loader: 'ts-loader',
        options: {
          appendTsSuffixTo: [/\.vue$/]
        },
      }],
      exclude: {
        include: /node_modules/,
        exclude: /goodish/
      }
    },  {
      test: /\.js$/,
      use: {
        loader: 'babel-loader',
        options: {
          presets: ['@babel/preset-env']
        }
      },
      exclude: {
        include: /node_modules/,
        exclude: /goodish/
      }
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
  externals: {
    vue: 'Vue'
  },
  plugins: [
    new webpack.ContextReplacementPlugin(
      /angular(\\|\/)core(\\|\/)@angular/,
      path.resolve(__dirname, './design')
    ),
    new VueLoaderPlugin(),
    new MiniCssExtractPlugin({
      filename: '[name].css',
    }),
  ]
}
