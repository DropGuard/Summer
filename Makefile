.PHONY: all clean compile install test test-module it it-module run benchmark fmt check pre-commit arch realworld coverage coverage-module


export MAVEN_OPTS := --sun-misc-unsafe-memory-access=allow

all: compile

clean:
	mvn clean

compile:
	mvn compile

install:
	mvn install -DskipTests

test:
	mvn test

test-module:
ifndef MODULE
	$(error Usage: make test-module MODULE=summer-example [TEST=ClassName])
endif
ifdef TEST
	mvn test -pl $(MODULE) -am -Dtest="$(TEST)" -Dsurefire.useFile=false -Dsurefire.failIfNoSpecifiedTests=false
else
	mvn test -pl $(MODULE) -am -Dsurefire.useFile=false
endif

# Integration tests (Testcontainers) — need a running Docker daemon. Activated
# with -Dit so plain `make test` / `mvn verify` stay Docker-free and fast.
it:
	mvn verify -Dit -Dsurefire.useFile=false

it-module:
ifndef MODULE
	$(error Usage: make it-module MODULE=summer-data-redis [TEST=ClassName])
endif
ifdef TEST
	mvn verify -pl $(MODULE) -am -Dit -Dtest="$(TEST)" -Dsurefire.useFile=false -Dsurefire.failIfNoSpecifiedTests=false
else
	mvn verify -pl $(MODULE) -am -Dit -Dsurefire.useFile=false
endif

run:
	mvn compile exec:java -pl summer-example -am

realworld:
	mvn compile exec:java -f samples/summer-realworld/pom.xml

benchmark:
	python summer-benchmark/run-benchmarks.py

fmt:
	mvn spotless:apply -pl '!summer-dependencies,!summer-build-parent'

check:
	mvn spotless:check -pl '!summer-dependencies,!summer-build-parent'

pre-commit: fmt check clean test

arch:
	mvn test -pl summer-archunit
coverage:
	mvn clean test
coverage-module:
ifndef MODULE
	$(error Usage: make coverage-module MODULE=summer-example)
endif
	mvn test jacoco:report -pl $(MODULE) -am
