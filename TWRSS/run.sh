#!/usr/bin/env bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

source "$SCRIPT_DIR/.venv/bin/activate" > /dev/null
python3 "$SCRIPT_DIR/main.py" "$@"