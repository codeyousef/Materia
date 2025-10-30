const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

module.exports = {
  entry: './src/index.js',
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: 'bundle.[contenthash].js',
    clean: true,
    publicPath: '/'
  },

  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: ['@babel/preset-env']
          }
        }
      },
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader']
      },
      {
        test: /\.(wasm|wgsl)$/,
        type: 'asset/resource'
      }
    ]
  },

  plugins: [
    new HtmlWebpackPlugin({
      template: './src/index.html',
      title: 'Materia Development Tools',
      meta: {
        viewport: 'width=device-width, initial-scale=1',
        description: 'Web-based development tools for Materia 3D library'
      }
    }),

    new CopyWebpackPlugin({
      patterns: [
        { from: '../editor/web/dist', to: 'tools/editor' },
        { from: '../profiler/web/dist', to: 'tools/profiler' },
        { from: '../docs/web/dist', to: 'docs' },
        { from: 'static', to: 'static' }
      ]
    })
  ],

  devServer: {
    contentBase: './dist',
    port: 3000,
    historyApiFallback: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },

  optimization: {
    splitChunks: {
      chunks: 'all',
      cacheGroups: {
        vendor: {
          test: /[\\/]node_modules[\\/]/,
          name: 'vendors',
          chunks: 'all'
        }
      }
    }
  }
};