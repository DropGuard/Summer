.PHONY: all clean compile build test run run-aot run-runtime benchmark fmt

MVN ?= mvnd
export MAVEN_OPTS := --sun-misc-unsafe-memory-access=allow

# Default goal
all: build

clean:
	$(MVN) clean

compile:
	$(MVN) clean compile

build:
	$(MVN) clean install

test:
	$(MVN) clean test

# Run the example application in default (AOT) mode
run: run-aot

run-aot:
	$(MVN) clean compile exec:java -pl summer-example -am -Paot

# Run the example application in JIT (Runtime) mode
run-runtime:
	$(MVN) clean compile exec:java -pl summer-example -am -Pruntime

benchmark:
	python summer-benchmark/run-benchmarks.py

fmt:
	$(MVN) spotless:apply
