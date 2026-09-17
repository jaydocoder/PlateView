#!/usr/bin/env bash

migration_version_is_at_least() {
    local expected_version="$1"
    local applied_version="$2"

    [[ "$expected_version" =~ ^[0-9]+$ ]] || return 1
    [[ "$applied_version" =~ ^[0-9]+$ ]] || return 1
    (( 10#$applied_version >= 10#$expected_version ))
}
