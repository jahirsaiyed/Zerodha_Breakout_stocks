#!/usr/bin/env bash
# Backup PostgreSQL database to a local file.
# Optionally uploads to S3 if AWS_S3_BUCKET is set.
#
# Usage:
#   ./scripts/backup.sh
#   AWS_S3_BUCKET=my-bucket ./scripts/backup.sh
#
# Scheduled via cron — example (daily at 2:00 AM):
#   0 2 * * * /opt/trading/scripts/backup.sh >> /var/log/trading-backup.log 2>&1

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/trading}"
DB_NAME="${DB_NAME:-trading}"
DB_USERNAME="${DB_USERNAME:-trading}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
RETAIN_DAYS="${RETAIN_DAYS:-14}"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
FILENAME="${DB_NAME}_${TIMESTAMP}.sql.gz"
FILEPATH="${BACKUP_DIR}/${FILENAME}"

mkdir -p "${BACKUP_DIR}"

echo "[$(date)] Starting backup: ${FILENAME}"
PGPASSWORD="${DB_PASSWORD}" pg_dump \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USERNAME}" \
  -d "${DB_NAME}" \
  --no-owner \
  --no-acl \
  | gzip > "${FILEPATH}"

echo "[$(date)] Backup written: ${FILEPATH} ($(du -sh "${FILEPATH}" | cut -f1))"

# Upload to S3 if bucket is configured
if [[ -n "${AWS_S3_BUCKET:-}" ]]; then
  S3_PATH="s3://${AWS_S3_BUCKET}/db-backups/${FILENAME}"
  echo "[$(date)] Uploading to ${S3_PATH}"
  aws s3 cp "${FILEPATH}" "${S3_PATH}"
  echo "[$(date)] S3 upload complete"
fi

# Remove backups older than RETAIN_DAYS
echo "[$(date)] Removing backups older than ${RETAIN_DAYS} days"
find "${BACKUP_DIR}" -name "${DB_NAME}_*.sql.gz" -mtime "+${RETAIN_DAYS}" -delete

echo "[$(date)] Backup complete"
