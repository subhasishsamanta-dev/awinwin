#!/bin/bash
# Setup script to install cron job for daily extraction

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
WRAPPER_SCRIPT="$SCRIPT_DIR/run_daily_extraction_with_upload.sh"
CRON_TAG_BEGIN="# >>> awinwin daily extraction (managed) >>>"
CRON_TAG_END="# <<< awinwin daily extraction (managed) <<<"

echo "=========================================="
echo "Setting up Daily Cron Job"
echo "=========================================="
echo ""

# Make wrapper script executable
chmod +x "$WRAPPER_SCRIPT"
echo "✅ Made wrapper script executable"

# Remove existing managed block and install a fresh one
CURRENT_CRON="$(crontab -l 2>/dev/null || true)"
CLEAN_CRON="$(printf "%s\n" "$CURRENT_CRON" \
  | sed "/$CRON_TAG_BEGIN/,/$CRON_TAG_END/d" \
  | grep -v "run_daily_extraction_with_upload.sh" || true)"

{
  printf "%s\n" "$CLEAN_CRON"
  echo "$CRON_TAG_BEGIN"
  echo "SHELL=/bin/bash"
  echo "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
  echo "0 0 * * * /bin/bash \"$WRAPPER_SCRIPT\""
  echo "$CRON_TAG_END"
} | crontab -
echo "✅ Installed/updated managed cron block"

echo ""
echo "=========================================="
echo "📋 Configuration Summary"
echo "=========================================="
echo "   Script: $WRAPPER_SCRIPT"
echo "   Schedule: Daily at 12:00 AM (midnight)"
echo "   Logs: $SCRIPT_DIR/logs/"
echo ""
echo "=========================================="
echo "🔍 Verification Commands"
echo "=========================================="
echo "   View cron jobs: crontab -l"
echo "   Test manually:  $WRAPPER_SCRIPT"
echo "   View logs:      ls -lh logs/"
echo ""
echo "✅ Cron job installed successfully!"
