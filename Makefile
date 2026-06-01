.PHONY: all clean compile install test run benchmark fmt arch realworld

MVN ?= mvnd
export MAVEN_OPTS := --sun-misc-unsafe-memory-access=allow

all: compile

clean:
	$(MVN) clean

compile:
	$(MVN) clean compile

install:
	$(MVN) clean install -DskipTests

test:
	$(MVN) clean test

run:
	$(MVN) compile exec:java -pl summer-example -am

realworld:
	$(MVN) compile exec:java -f summer-realworld/pom.xml

benchmark:
	python summer-benchmark/run-benchmarks.py

fmt:
	$(MVN) spotless:apply

arch:
	$(MVN) test -pl summer-archunit
