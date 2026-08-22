.PHONY: up down db-start db-stop backend frontend test install-deps

up: db-start backend

down: db-stop

db-start:
	docker compose -f docker/docker-compose.yml up -d

db-stop:
	docker compose -f docker/docker-compose.yml down

backend:
	mvn compile exec:java -pl .

frontend:
	cd frontend && npm install && npm run dev

test:
	mvn test

install-deps:
	cd frontend && npm install
