const cluster = require('cluster');

if (cluster.isPrimary) {
  // Spawn 2 workers to match the 2 CPU limit
  for (let i = 0; i < 2; i++) {
    cluster.fork();
  }
  cluster.on('exit', (worker, code, signal) => {
    cluster.fork();
  });
} else {
  const fastify = require('fastify')({ logger: false });

  // In-memory data store
  const userMap = new Map();
  for (let i = 1; i <= 10; i++) {
    userMap.set(String(i), {
      id: String(i),
      name: "User" + i,
      email: "user" + i + "@example.com"
    });
  }

  fastify.get('/health/live', async (request, reply) => {
    return 'OK';
  });

  fastify.get('/users/:id', async (request, reply) => {
    const user = userMap.get(request.params.id);
    if (!user) {
      reply.code(404).send();
      return;
    }
    return user;
  });

  fastify.post('/users', async (request, reply) => {
    const user = request.body;
    userMap.set(user.id, user);
    return user;
  });

  fastify.put('/users/:id', async (request, reply) => {
    const id = request.params.id;
    if (!userMap.has(id)) {
      reply.code(404).send();
      return;
    }
    const user = request.body;
    userMap.set(id, user);
    return user;
  });

  fastify.delete('/users/:id', async (request, reply) => {
    userMap.delete(request.params.id);
    return { success: true };
  });

  fastify.listen({ port: 3000, host: '0.0.0.0' }, (err, address) => {
    if (err) throw err;
  });
}
