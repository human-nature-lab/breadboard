'use strict'
const webpack = require('webpack')
const path = require('path')
const VueLoaderPlugin = require('vue-loader/lib/plugin')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const buildPath = path.resolve(__dirname, '../../public/bundles/')

const isProd = process.env.NODE_ENV === 'production'
const publicPath = isProd ? '/assets/bundles/' : `http://localhost:${PORT}/bundles/`
const plugins =  [
  new webpack.ContextReplacementPlugin(
    /angular(\\|\/)core(\\|\/)@angular/,
    path.resolve(__dirname, './design')
  ),
  new VueLoaderPlugin()
]
if (isProd) {
  plugins.push(new MiniCssExtractPlugin({
    filename: '[name].css',
  }))
}
const PORT = 8765
module.exports = {
  entry: {
    client: './client/client.ts',
    // breadboard: './core/breadboard.ts',
    design: './design/design.js',
    // 'client-angular': './design/client.js',
    // vue: ['vue', 'vuetify'],
    // 'vue-components': {
    //   import: './client/vue-components.ts',
    //   dependOn: 'vue'
    // },
    // graph: './client/lib/graph.ts'
  },
  output: {
    path: buildPath,
    publicPath: publicPath,
    chunkFilename: '[name].js',
    clean: true
  },
  mode: isProd ? 'production' : 'development',
  devServer: {
    port: PORT,
    hot: true,
    publicPath: `/bundles`,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, PATCH, OPTIONS',
      'Access-Control-Allow-Headers': 'X-Requested-With, content-type, Authorization'
    }
  },
  module: {
    rules: [{
      test: /\.vue$/,
      loader: 'vue-loader'
    }, {
      test: /\.scss$/,
      use: [
        isProd ? MiniCssExtractPlugin.loader : 'vue-style-loader',
        'css-loader',
        {
          loader: 'sass-loader',
          options: {
            implementation: require('sass')
          }
        }
      ],
      // exclude: /node_modules/,
    }, {
      test: /\.sass$/,
      use: [
        isProd ? MiniCssExtractPlugin.loader : 'vue-style-loader',
        'css-loader',
        {
          loader: 'sass-loader',
          options: {
            implementation: require('sass'),
            sassOptions: {
              indentedSyntax: true
            }
          }
        }
      ],
    }, {
      test: /\.css$/,
      use: ['style-loader', 'css-loader'],
      // exclude: /node_modules/,
    }, {
      test: /\.tsx?$/,
      use: [{
        loader: 'ts-loader',
        options: {
          appendTsSuffixTo: [/\.vue$/]
        },
      }],
      // exclude: /(goodish|gremlins-ts)/
    },  {
      test: /\.js$/,
      use: {
        loader: 'babel-loader',
        options: {
          presets: ['@babel/preset-env']
        }
      },
      // exclude: {
      //   include: /node_modules/,
      //   exclude: /goodish/
      // }
    }, {
      test: /\.html$/,
      use: ['ngtemplate-loader?relativeTo=frontend&prefix=files', 'html-loader'],
      exclude: /node_modules/,
    }, {
      test: /\.(jpg|png|gif|webp|tiff)$/,
      use: 'url-loader',
      exclude: /node_modules/,
    }, {
      test: /\.svg$/,
      use: 'url-loader?limit=10000&mimetype=image/svg+xml',
      exclude: /node_modules/,
    }, {
      test: /\.(eot|svg|ttf|woff|woff2)$/,
      use: {
        loader: 'file-loader',
        options: {
          name: '[contenthash].[ext]'
        }
      }
    }]
  },
  resolve: {
    extensions: ['.js','.json','.css','.html', '.jsx', '.ts', '.tsx', '.vue'],
    alias: {
      'vue$': 'vue/dist/vue.esm.js' // 'vue/dist/vue.common.js' for webpack 1
    }
  },
  // externals: {
  //   vue: 'Vue'
  // }
  optimization: {
    splitChunks: {
      chunks: 'async',
      cacheGroups: {
        
        vendor: {
          test: /[\\/]node_modules[\\/]/,
          name: 'vendors',
          chunks: 'async',
        },
      }
    }
  },
  plugins
}
