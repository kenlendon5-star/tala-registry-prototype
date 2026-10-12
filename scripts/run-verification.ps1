git --no-pager log --oneline -3
git --no-pager status --short
node tools/check-docs.cjs 2>&1 | Select-Object -First 30
node tools/verify-artifacts.cjs 2>&1 | Select-Object -First 30
node planning/02-progress/build-board.cjs 2>&1 | Select-Object -First 20
node planning/02-progress/verify-board.cjs 2>&1 | Select-Object -First 20
