#!/usr/bin/env bash
# Manage the local tiny-postgres Podman container used by hibernate-test.
set -euo pipefail

NAME="${PG_CONTAINER:-tiny-postgres}"
IMAGE="${PG_IMAGE:-docker.io/library/postgres:alpine}"
VOLUME="${PG_VOLUME:-tiny-postgres-data}"
PORT="${PG_PORT:-5432}"
USER="${PG_USER:-postgres}"
PASSWORD="${PG_PASSWORD:-postgres}"
DB="${PG_DB:-hibernate_test}"

exists()  { podman container exists "$NAME"; }
running() { [ "$(podman inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" = "true" ]; }

wait_ready() {
  for _ in $(seq 1 30); do
    podman exec "$NAME" pg_isready -q -U "$USER" && { echo "Ready on localhost:$PORT"; return; }
    sleep 1
  done
  echo "Postgres did not become ready in 30s" >&2; exit 1
}

create() {
  podman run -d --name "$NAME" \
    -e POSTGRES_USER="$USER" -e POSTGRES_PASSWORD="$PASSWORD" -e POSTGRES_DB="$DB" \
    -p "$PORT:5432" -v "$VOLUME:/var/lib/postgresql" \
    "$IMAGE" >/dev/null
  echo "Created $NAME"
}

confirm() {
  read -r -p "$1 [y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }
}

usage() {
  cat <<EOF
Usage: $(basename "$0") <command> [args]

Commands:
  start             Create (if needed) and start the container
  stop              Stop the container
  restart           Restart the container
  status            Show container status and readiness
  logs [-f]         Show container logs (pass -f to follow)
  psql [args]       Open psql in database '$DB' (extra args passed to psql)
  exec <sql>        Run a single SQL statement
  dump [file]       Dump database to file (default: ${DB}-<timestamp>.sql)
  restore <file>    Restore a SQL dump into the database
  reset             Drop the container and volume, then recreate (DESTROYS DATA)
  remove            Remove the container (keeps the data volume)
  purge             Remove the container and data volume (DESTROYS DATA)
  url               Print the JDBC URL and credentials

Settings can be overridden with env vars: PG_CONTAINER, PG_IMAGE, PG_VOLUME,
PG_PORT, PG_USER, PG_PASSWORD, PG_DB.
EOF
}

cmd="${1:-}"; shift || true
case "$cmd" in
  start)
    if ! exists; then create; elif ! running; then podman start "$NAME" >/dev/null; echo "Started $NAME"; else echo "$NAME already running"; fi
    wait_ready ;;
  stop)
    exists && podman stop "$NAME" >/dev/null && echo "Stopped $NAME" || echo "$NAME does not exist" ;;
  restart)
    podman restart "$NAME" >/dev/null && wait_ready ;;
  status)
    if ! exists; then echo "$NAME does not exist"; exit 0; fi
    podman ps -a --filter "name=^${NAME}$" --format "{{.Names}}  {{.Status}}  {{.Ports}}"
    running && podman exec "$NAME" pg_isready -U "$USER" || true ;;
  logs)
    podman logs "$@" "$NAME" ;;
  psql)
    podman exec -it "$NAME" psql -U "$USER" -d "$DB" "$@" ;;
  exec)
    [ $# -ge 1 ] || { echo "Usage: $0 exec \"<sql>\"" >&2; exit 1; }
    podman exec "$NAME" psql -U "$USER" -d "$DB" -c "$*" ;;
  dump)
    file="${1:-${DB}-$(date +%Y%m%d-%H%M%S).sql}"
    podman exec "$NAME" pg_dump -U "$USER" -d "$DB" --clean --if-exists > "$file"
    echo "Dumped to $file" ;;
  restore)
    [ -f "${1:-}" ] || { echo "Usage: $0 restore <file>" >&2; exit 1; }
    podman exec -i "$NAME" psql -q -o /dev/null -U "$USER" -d "$DB" -v ON_ERROR_STOP=1 < "$1"
    echo "Restored $1" ;;
  reset)
    confirm "This deletes all data in $NAME ($VOLUME). Continue?"
    podman rm -f "$NAME" >/dev/null 2>&1 || true
    podman volume rm -f "$VOLUME" >/dev/null 2>&1 || true
    create; wait_ready ;;
  remove)
    podman rm -f "$NAME" >/dev/null && echo "Removed $NAME (volume $VOLUME kept)" ;;
  purge)
    confirm "This deletes $NAME and volume $VOLUME. Continue?"
    podman rm -f "$NAME" >/dev/null 2>&1 || true
    podman volume rm -f "$VOLUME" >/dev/null 2>&1 || true
    echo "Purged $NAME and $VOLUME" ;;
  url)
    echo "jdbc:postgresql://localhost:$PORT/$DB  user=$USER  password=$PASSWORD" ;;
  ""|-h|--help|help)
    usage ;;
  *)
    echo "Unknown command: $cmd" >&2; usage; exit 1 ;;
esac
