.PHONY: all clean compile install test test-full run benchmark fmt check pre-commit arch realworld


export MAVEN_OPTS := --sun-misc-unsafe-memory-access=allow

all: compile

clean:
	mvn clean

compile:
	mvn clean compile

install:
	mvn clean install -DskipTests

test:
	mvn clean test

test-full:
	mvn clean test

run:
	mvn compile exec:java -pl summer-example -am

realworld:
	mvn compile exec:java -f summer-realworld/pom.xml

benchmark:
	python summer-benchmark/run-benchmarks.py

fmt:
	mvn spotless:apply

check:
	mvn spotless:check

pre-commit: fmt check test

arch:
	mvn test -pl summer-archunit
