const cluster = require('cluster');
const path = require('path');

if (cluster.isPrimary) {
  // We have 2 CPUs in the docker limit
  for (let i = 0; i < 2; i++) {
    cluster.fork();
  }
  cluster.on('exit', (worker, code, signal) => {
    console.log(`Worker ${worker.process.pid} died`);
    cluster.fork();
  });
} else {
  require(path.join(__dirname, '.next/standalone/server.js'));
}
