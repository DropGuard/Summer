.PHONY: up down seed seed-clean run

up:
	docker-compose -f docker/docker-compose.yml up -d

down:
	docker-compose -f docker/docker-compose.yml down

seed:
	docker exec -i summer-pg psql -U summer summer < docker/seed.sql

seed-clean:
	docker exec -i summer-pg psql -U summer summer -c \
	  "TRUNCATE tweets, likes, follows, direct_messages, conversations, users CASCADE"

run:
	cd .. && mvn compile exec:java -pl summer-twitter -am
