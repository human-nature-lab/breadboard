process.env.NODE_ENV = 'production'
const clientConfig = require('./webpack.client')

// TODO: add minification and prod stuff for client here

module.exports = [clientConfig]
