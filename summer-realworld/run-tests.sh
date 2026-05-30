#!/bin/bash

# Start the application in the background
mvn exec:java -Dexec.mainClass="summer.realworld.Application" &
APP_PID=$!

# Wait for the application to start
sleep 5

# Run Hurl tests
hurl --test src/test/resources/test-auth.hurl

# Stop the application
kill $APP_PID