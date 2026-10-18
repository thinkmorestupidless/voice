#!/bin/sh
# Starts what a cluster would supply, for a laptop: Voice's Postgres with ankka's schema, the
# reasoning service with its own Postgres and the schema of the ankka release it is built on, and
# Keycloak with the staff realm. With `graph`, also Kafka, Neo4j and the graph sink.
set -eu
cd "$(dirname "$0")/.."

sbt -batch schema
./scripts/reasoning-ddl.sh

if [ "${1:-}" = "graph" ]; then
  VOICE_GRAPH_KAFKA=kafka:9092 VOICE_GRAPH_NEO4J=bolt://neo4j:7687 \
    docker compose --profile reasoning --profile staff --profile graph up -d
else
  docker compose --profile reasoning --profile staff up -d
fi
