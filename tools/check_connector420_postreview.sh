#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_connector420.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
mkdir -p build/checkpoint-logs
python tools/postreview420/check_editor_guards.py . build/connector420-postreview "$CP" 2>&1 | tee build/checkpoint-logs/EditorGuardPost420.txt
