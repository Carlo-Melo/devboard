module.exports = function (config) {
  config.set({
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [require('karma-jasmine'), require('karma-chrome-launcher'), require('karma-coverage'), require('@angular-devkit/build-angular/plugins/karma')],
    hostname: '127.0.0.1',
    listenAddress: '127.0.0.1',
    browsers: ['ChromeHeadless'],
    singleRun: true,
    reporters: ['progress'],
    coverageReporter: { dir: require('path').join(__dirname, 'coverage'), reporters: [{ type: 'text-summary' }, { type: 'html' }] }
  });
};
