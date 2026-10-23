#!/usr/bin/env bash
# PostgreSQL을 deploy/backups/에 덤프하고 7일 지난 파일은 지운다. 무료 VM에는 관리형 백업이 없으므로 cron으로 매일 돌린다.
#   crontab -e  →  0 4 * * * bash /home/ubuntu/CleanEat/deploy/backup-db.sh >> /home/ubuntu/backup.log 2>&1
# 복구: gunzip -c backups/cleaneat-XXXX.sql.gz | docker compose exec -T db psql -U cleaneat -d cleaneat
set -euo pipefail
cd "$(dirname "$0")"

mkdir -p backups
file="backups/cleaneat-$(date +%Y%m%d-%H%M).sql.gz"
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' | gzip > "$file"
find backups -name 'cleaneat-*.sql.gz' -mtime +7 -delete
echo "backup: $file"
