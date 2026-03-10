#!/bin/bash
# Load local env vars and start Spring Boot
set -a
source .env.local
set +a

./gradlew bootRun
