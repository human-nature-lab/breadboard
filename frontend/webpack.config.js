'use strict'
const webpack = require('webpack')
const path = require('path')
const VueLoaderPlugin = require('vue-loader/lib/plugin')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const buildPath = path.resolve(__dirname, '../public/bundles/')

const PORT = 8765
const isProd = process.env.NODE_ENV === 'production'
const publicPath = isProd
  ? '/assets/bundles/'
  : `http://localhost:${PORT}/bundles/`
// webpack-dev-server v3's client isn't injected when launched via webpack-cli's
// `webpack serve`, so the HMR websocket never connects. Inject it manually in dev.
let devClient = isProd
  ? []
  : [
      `webpack-dev-server/client?http://localhost:${PORT}`,
      'webpack/hot/dev-server',
    ]
devClient = []
const plugins = [
  new webpack.ContextReplacementPlugin(
    /angular(\\|\/)core(\\|\/)@angular/,
    path.resolve(__dirname, './design'),
  ),
  new VueLoaderPlugin(),
  new webpack.DefinePlugin({
    __VUE_PROD_DEVTOOLS__: true,
  }),
]
if (isProd) {
  plugins.push(
    new MiniCssExtractPlugin({
      filename: '[name].css',
    }),
  )
}
module.exports = {
  entry: {
    client: [...devClient, './client/src/client.ts'],
    breadboard: [...devClient, './core/src/breadboard.ts'],
    design: [...devClient, './design/design.js'],
    'client-angular': [...devClient, './design/client.js'],
    // vue: ['vue', 'vuetify'],
    // 'vue-components': {
    //   import: './client/vue-components.ts',
    //   dependOn: 'vue'
    // },
    // graph: './client/lib/graph.ts'
  },
  cache: {
    type: 'filesystem',
  },
  output: {
    path: buildPath,
    publicPath: publicPath,
    chunkFilename: '[name].js',
    clean: true,
  },
  mode: isProd ? 'production' : 'development',
  devtool: 'eval-source-map',
  devServer: {
    port: PORT,
    hot: true,
    publicPath: `/bundles`,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, PATCH, OPTIONS',
      'Access-Control-Allow-Headers':
        'X-Requested-With, content-type, Authorization',
    },
  },
  module: {
    rules: [
      {
        test: /\.vue$/,
        loader: 'vue-loader',
      },
      {
        test: /\.scss$/,
        use: [
          isProd ? MiniCssExtractPlugin.loader : 'vue-style-loader',
          'css-loader',
          {
            loader: 'sass-loader',
            options: {
              implementation: require('sass'),
            },
          },
        ],
        // exclude: /node_modules/,
      },
      {
        test: /\.sass$/,
        use: [
          isProd ? MiniCssExtractPlugin.loader : 'vue-style-loader',
          'css-loader',
          {
            loader: 'sass-loader',
            options: {
              implementation: require('sass'),
              sassOptions: {
                indentedSyntax: true,
              },
            },
          },
        ],
      },
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader'],
        // exclude: /node_modules/,
      },
      {
        test: /\.tsx?$/,
        use: [
          {
            loader: 'ts-loader',
            options: {
              transpileOnly: true,
              appendTsSuffixTo: [/\.vue$/],
            },
          },
        ],
        // exclude: /(goodish|gremlins-ts)/
      },
      {
        test: /\.js$/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: ['@babel/preset-env'],
          },
        },
        // exclude: {
        //   include: /node_modules/,
        //   exclude: /goodish/
        // }
      },
      {
        test: /\.html$/,
        use: [
          'ngtemplate-loader?relativeTo=frontend&prefix=files',
          'html-loader',
        ],
        exclude: /node_modules/,
      },
      {
        test: /\.(jpg|png|gif|webp|tiff)$/,
        use: 'url-loader',
        exclude: /node_modules/,
      },
      {
        test: /\.svg$/,
        use: 'url-loader?limit=10000&mimetype=image/svg+xml',
        exclude: /node_modules/,
      },
      {
        test: /\.(eot|svg|ttf|woff|woff2)$/,
        use: {
          loader: 'file-loader',
          options: {
            name: '[contenthash].[ext]',
          },
        },
      },
    ],
  },
  resolve: {
    extensions: [
      '.js',
      '.json',
      '.css',
      '.html',
      '.jsx',
      '.ts',
      '.tsx',
      '.vue',
    ],
    // prevents multiple copies of vue being loaded
    alias: {
      vue$: 'vue/dist/vue.js', // 'vue/dist/vue.common.js' for webpack 1
    },
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
      },
    },
  },
  plugins,
}
