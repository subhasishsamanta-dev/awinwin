#!/bin/bash

##############################################################################
# Cron Job Setup Script
# Sets up the daily 12 AM execution for Swedish Players Extraction
##############################################################################

PROJECT_DIR="/home/bit1148/Videos/Elite Sports All"
SCRIPT_PATH="$PROJECT_DIR/run_daily_extraction.sh"
LOG_DIR="$PROJECT_DIR/logs"

# Ensure log directory exists
mkdir -p "$LOG_DIR"

echo "=========================================="
echo "Cron Job Setup Script"
echo "=========================================="
echo ""
echo "Project Directory: $PROJECT_DIR"
echo "Script Path: $SCRIPT_PATH"
echo "Log Directory: $LOG_DIR"
echo ""

# Check if script exists and is executable
if [ ! -x "$SCRIPT_PATH" ]; then
    echo "ERROR: Script is not executable at $SCRIPT_PATH"
    exit 1
fi

# Get current crontab
CURRENT_CRON=$(crontab -l 2>/dev/null | grep -v "^#" | grep "run_daily_extraction.sh" || true)

if [ -n "$CURRENT_CRON" ]; then
    echo "⚠️  Cron job already exists:"
    echo "   $CURRENT_CRON"
    echo ""
    read -p "Do you want to replace it? (y/n): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
    # Remove existing cron job
    (crontab -l 2>/dev/null | grep -v "run_daily_extraction.sh"; echo "") | crontab -
fi

# Add new cron job - 12 AM daily (0 0 * * *)
NEW_CRON="0 0 * * * cd $PROJECT_DIR && $SCRIPT_PATH >> $LOG_DIR/cron.log 2>&1"

# Install new cron job
(crontab -l 2>/dev/null; echo "$NEW_CRON") | crontab -

echo "✓ Cron job configured successfully!"
echo ""
echo "Schedule Details:"
echo "  Time: Every day at 12:00 AM (00:00)"
echo "  Command: $SCRIPT_PATH"
echo "  Working Directory: $PROJECT_DIR"
echo "  Logging: $LOG_DIR/cron.log"
echo ""

# Display current crontab
echo "Current Cron Schedule:"
crontab -l 2>/dev/null | grep "run_daily_extraction.sh" || echo "No cron job found"

echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "The extraction will run automatically at 12 AM every day."
echo "Check logs at: $LOG_DIR/"
echo ""
echo "To view/edit crontab: crontab -e"
echo "To remove cron job: crontab -r"
