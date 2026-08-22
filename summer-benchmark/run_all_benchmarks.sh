cd summer-benchmark

for profile in spring-boot summer summer-jsonb gin fastify; do
  echo "Running benchmark for $profile..."
  docker compose --profile $profile up --build --abort-on-container-exit
  docker compose --profile $profile down
  echo "Finished $profile."
  sleep 10
done

python3 compare-benchmarks.py
