#!/usr/bin/env bash
# Limpia archivos generados antes de subir a git:  ./scripts/limpiar-proyecto.sh [--git]
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf target .idea .vscode out build
find . -name "*.iml" -not -path "./.git/*" -delete
find . -name ".DS_Store" -delete
if [[ "${1:-}" == "--git" && -d .git ]]; then
  git rm -r --cached --ignore-unmatch -q target .idea || true
fi
echo "Listo."
