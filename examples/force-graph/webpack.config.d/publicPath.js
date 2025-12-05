// Fix for "Automatic publicPath is not supported in this browser" error
config.output = config.output || {};
config.output.publicPath = '/';
