#!/bin/bash
# Setup script to install cron job for daily extraction

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
WRAPPER_SCRIPT="$SCRIPT_DIR/run_daily_extraction_with_upload.sh"

echo "=========================================="
echo "Setting up Daily Cron Job"
echo "=========================================="
echo ""

# Make wrapper script executable
chmod +x "$WRAPPER_SCRIPT"
echo "✅ Made wrapper script executable"

# Remove existing cron jobs for this project
crontab -l 2>/dev/null | grep -v "run_daily_extraction" | crontab -
echo "✅ Removed old cron jobs"

# Add new cron job: Run daily at 12:00 AM (midnight)
(crontab -l 2>/dev/null; echo "0 0 * * * $WRAPPER_SCRIPT") | crontab -
echo "✅ Installed new cron job"

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
