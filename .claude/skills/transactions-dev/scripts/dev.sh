#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"

ACTION="${1:-start}"

if [[ "$ACTION" == "-h" || "$ACTION" == "--help" ]]; then
    ACTION="help"
elif [[ $# -gt 0 ]]; then
    shift
fi

SKIP_TESTS=false
FORCE=false
TEST_CLASS=""
SERVICE="api"
TAIL=200
FOLLOW=false
TIMEOUT=240


usage() {

    cat <<'USAGE'
Usage:

  dev.sh start [--skip-tests] [--timeout SECONDS]
  dev.sh stop
  dev.sh reset --force [--skip-tests] [--timeout SECONDS]
  dev.sh test [--class TEST_CLASS]
  dev.sh verify [--timeout SECONDS]
  dev.sh logs [--service api|mysql|all] [--tail N] [--follow]
USAGE
}


while [[ $# -gt 0 ]]; do

    case "$1" in

        --skip-tests)

            SKIP_TESTS=true
            shift
            ;;

        --force)

            FORCE=true
            shift
            ;;

        --class)

            if [[ $# -lt 2 ]]; then
                echo "Missing value for --class" >&2
                exit 2
            fi

            TEST_CLASS="$2"
            shift 2
            ;;

        --service)

            if [[ $# -lt 2 ]]; then
                echo "Missing value for --service" >&2
                exit 2
            fi

            SERVICE="$2"
            shift 2
            ;;

        --tail)

            if [[ $# -lt 2 ]]; then
                echo "Missing value for --tail" >&2
                exit 2
            fi

            TAIL="$2"
            shift 2
            ;;

        --follow)

            FOLLOW=true
            shift
            ;;

        --timeout)

            if [[ $# -lt 2 ]]; then
                echo "Missing value for --timeout" >&2
                exit 2
            fi

            TIMEOUT="$2"
            shift 2
            ;;

        -h|--help)

            usage
            exit 0
            ;;

        *)

            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done


require_command() {

    local command_name="$1"

    if ! command -v "$command_name" >/dev/null 2>&1; then

        echo "Required command '$command_name' was not found on PATH." >&2
        exit 1
    fi
}


assert_positive_integer() {

    local name="$1"
    local value="$2"

    if [[ ! "$value" =~ ^[0-9]+$ ]]; then

        echo "$name must be a positive integer, but '$value' was provided." >&2
        exit 2
    fi

    if [[ "$value" -lt 1 ]]; then

        echo "$name must be greater than zero." >&2
        exit 2
    fi
}


assert_docker_ready() {

    require_command docker

    if ! docker info >/dev/null 2>&1; then

        echo "Docker is installed but the Docker daemon is not available. Start Docker Desktop and try again." >&2
        exit 1
    fi

    if ! docker compose version >/dev/null 2>&1; then

        echo "Docker Compose is not available through 'docker compose'." >&2
        exit 1
    fi
}


get_env_value() {

    local name="$1"
    local default_value="$2"

    local current_value=""

    current_value="$(
        printenv "$name" 2>/dev/null ||
        true
    )"

    if [[ -n "$current_value" ]]; then

        printf '%s\n' "$current_value"
        return
    fi

    local env_file="$REPO_ROOT/.env"

    if [[ -f "$env_file" ]]; then

        local line=""

        line="$(
            grep -E \
                "^[[:space:]]*${name}[[:space:]]*=" \
                "$env_file" |
            tail -n 1 ||
            true
        )"

        if [[ -n "$line" ]]; then

            local value="${line#*=}"

            value="$(
                printf '%s' "$value" |
                sed \
                    -e 's/^[[:space:]]*//' \
                    -e 's/[[:space:]]*$//'
            )"

            case "$value" in

                \"*\")

                    value="${value#\"}"
                    value="${value%\"}"
                    ;;

                \'*\')

                    value="${value#\'}"
                    value="${value%\'}"
                    ;;
            esac

            printf '%s\n' "$value"

            return
        fi
    fi

    printf '%s\n' "$default_value"
}


api_base_url() {

    local port=""

    port="$(
        get_env_value \
            APP_PORT \
            8080
    )"

    if [[ ! "$port" =~ ^[0-9]+$ ]]; then

        echo "APP_PORT must be an integer, but '$port' was configured." >&2
        exit 1
    fi

    printf 'http://localhost:%s\n' "$port"
}


run_maven_tests() {

    local wrapper="$REPO_ROOT/mvnw"

    if [[ ! -f "$wrapper" ]]; then

        echo "Maven wrapper was not found at '$wrapper'." >&2
        exit 1
    fi

    chmod +x "$wrapper" 2>/dev/null || true

    if [[ -z "$TEST_CLASS" ]]; then

        "$wrapper" clean test

    else

        "$wrapper" \
            "-Dtest=$TEST_CLASS" \
            test
    fi
}


wait_for_service_healthy() {

    local service="$1"
    local timeout="$2"

    local start_time=$SECONDS

    while (( SECONDS - start_time < timeout )); do

        local container_id=""

        container_id="$(
            docker compose ps -q "$service" 2>/dev/null |
            head -n 1 ||
            true
        )"

        if [[ -n "$container_id" ]]; then

            local status=""

            status="$(
                docker inspect \
                    --format \
                    '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
                    "$container_id" \
                    2>/dev/null ||
                true
            )"

            if [[ "$status" == "healthy" ]]; then

                echo "[OK] $service is healthy."
                return 0
            fi

            if \
                [[ "$status" == "unhealthy" ]] ||
                [[ "$status" == "exited" ]] ||
                [[ "$status" == "dead" ]]
            then

                echo "[ERROR] $service entered state '$status'." >&2

                docker compose logs \
                    --tail 120 \
                    "$service" ||
                    true

                return 1
            fi
        fi

        sleep 2
    done

    docker compose logs \
        --tail 120 \
        "$service" ||
        true

    echo "Timed out waiting for service '$service' to become healthy after ${timeout}s." >&2

    return 1
}


wait_for_api_health() {

    local timeout="$1"

    require_command curl

    local base_url=""

    base_url="$(
        api_base_url
    )"

    local health_url="$base_url/actuator/health"

    local start_time=$SECONDS

    while (( SECONDS - start_time < timeout )); do

        local body=""

        body="$(
            curl \
                -fsS \
                --max-time 5 \
                "$health_url" \
                2>/dev/null ||
            true
        )"

        if [[ "$body" == *'"status":"UP"'* ]]; then

            echo "[OK] API health endpoint reports UP."

            return 0
        fi

        sleep 2
    done

    echo "API health endpoint did not report UP within ${timeout}s: $health_url" >&2

    return 1
}


run_smoke_checks() {

    local base_url=""

    base_url="$(
        api_base_url
    )"

    wait_for_api_health 30

    curl \
        -fsS \
        --max-time 10 \
        "$base_url/transactions/types/__transactions_dev_skill_missing__" \
        >/dev/null

    echo "[OK] Read-only API smoke check passed."
}


show_status() {

    echo
    echo "Docker Compose status:"

    docker compose ps
}


start_stack() {

    assert_docker_ready

    if [[ "$SKIP_TESTS" == true ]]; then

        echo "[SKIP] Maven test suite was skipped by request."

    else

        echo "Running Maven test suite before startup..."

        run_maven_tests

        echo "[OK] Maven test suite passed."
    fi

    echo "Building and starting MySQL + API..."

    docker compose up \
        --build \
        -d

    wait_for_service_healthy \
        mysql \
        "$TIMEOUT"

    wait_for_service_healthy \
        api \
        "$TIMEOUT"

    run_smoke_checks

    show_status

    local base_url=""

    base_url="$(
        api_base_url
    )"

    echo
    echo "[SUCCESS] Transactions challenge is ready."
    echo "API:    $base_url"
    echo "Health: $base_url/actuator/health"
}


assert_positive_integer \
    "--timeout" \
    "$TIMEOUT"

assert_positive_integer \
    "--tail" \
    "$TAIL"


cd "$REPO_ROOT"


case "$ACTION" in

    start)

        echo "=== Mendel Transactions: start ==="

        start_stack
        ;;


    stop)

        echo "=== Mendel Transactions: stop ==="

        assert_docker_ready

        docker compose down

        echo "[SUCCESS] Stack stopped. MySQL volume was preserved."
        ;;


    reset)

        echo "=== Mendel Transactions: reset ==="

        if [[ "$FORCE" != true ]]; then

            echo "Reset deletes the persistent MySQL volume. Re-run with --force only when data destruction is intentional." >&2

            exit 1
        fi

        assert_docker_ready

        echo "[WARNING] Removing containers and persistent MySQL data..."

        docker compose down \
            -v \
            --remove-orphans

        echo "[OK] Persistent MySQL data removed."

        start_stack
        ;;


    test)

        echo "=== Mendel Transactions: test ==="

        assert_docker_ready

        run_maven_tests

        echo "[SUCCESS] Requested tests passed."
        ;;


    verify)

        echo "=== Mendel Transactions: verify ==="

        assert_docker_ready

        docker compose config \
            --quiet

        echo "[OK] compose.yaml is valid."

        wait_for_service_healthy \
            mysql \
            "$TIMEOUT"

        wait_for_service_healthy \
            api \
            "$TIMEOUT"

        run_smoke_checks

        show_status

        echo "[SUCCESS] Stack verification passed."
        ;;


    logs)

        assert_docker_ready

        if \
            [[ "$SERVICE" != "api" ]] &&
            [[ "$SERVICE" != "mysql" ]] &&
            [[ "$SERVICE" != "all" ]]
        then

            echo "--service must be api, mysql, or all." >&2
            exit 2
        fi

        args=(
            logs
            --tail
            "$TAIL"
        )

        if [[ "$FOLLOW" == true ]]; then

            args+=(
                --follow
            )
        fi

        if [[ "$SERVICE" != "all" ]]; then

            args+=(
                "$SERVICE"
            )
        fi

        docker compose \
            "${args[@]}"
        ;;


    help)

        usage
        ;;


    *)

        echo "Unknown action: $ACTION" >&2

        usage >&2

        exit 2
        ;;
esac