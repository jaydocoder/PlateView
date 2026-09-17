#!/usr/bin/env bash

migration_version_is_at_least() {
    local expected_version="$1"
    local applied_version="$2"

    [[ "$expected_version" =~ ^[0-9]+$ ]] || return 1
    [[ "$applied_version" =~ ^[0-9]+$ ]] || return 1
    (( 10#$applied_version >= 10#$expected_version ))
}

integer_is_at_least() {
    local actual="$1"
    local minimum="$2"

    [[ "$actual" =~ ^[0-9]+$ ]] || return 1
    [[ "$minimum" =~ ^[0-9]+$ ]] || return 1
    (( actual >= minimum ))
}

decimal_is_not_greater_than() {
    local actual="$1"
    local maximum="$2"

    [[ "$actual" =~ ^[0-9]+([.][0-9]+)?$ ]] || return 1
    [[ "$maximum" =~ ^[0-9]+([.][0-9]+)?$ ]] || return 1
    awk -v actual="$actual" -v maximum="$maximum" 'BEGIN { exit !(actual <= maximum) }'
}

is_immutable_ghcr_image() {
    local image_reference="$1"

    [[ "$image_reference" =~ ^ghcr\.io/[a-z0-9._-]+/[a-z0-9._-]+@sha256:[a-f0-9]{64}$ ]]
}

deployment_requires_backup() {
    local current_commit="$1"
    local migration_changes="$2"

    [[ -z "$current_commit" || "$migration_changes" == "true" ]]
}
