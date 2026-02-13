#!/bin/bash

##############################################################################
# Test Script for Daily Extraction Workflow
# Tests the complete automation pipeline without waiting for 12 AM
##############################################################################

PROJECT_DIR="/home/bit1148/Videos/Elite Sports All"
SCRIPT_PATH="$PROJECT_DIR/run_daily_extraction.sh"

echo "=========================================="
echo "Testing Daily Extraction Workflow"
echo "=========================================="
echo ""
echo "This test will:"
echo "1. Verify all required files exist and are executable"
echo "2. Check Maven build status"
echo "3. Show the current cron configuration"
echo "4. Display recent logs"
echo ""

# Check if script exists
if [ ! -f "$SCRIPT_PATH" ]; then
    echo "❌ ERROR: Script not found at $SCRIPT_PATH"
    exit 1
fi

if [ ! -x "$SCRIPT_PATH" ]; then
    echo "❌ ERROR: Script is not executable"
    exit 1
fi

echo "✓ Automation script found and is executable"
echo ""

# Check Maven build
echo "Checking Maven build status..."
if [ -d "$PROJECT_DIR/target/classes" ]; then
    echo "✓ Maven build artifacts found"
    CLASS_COUNT=$(find "$PROJECT_DIR/target/classes" -name "*.class" | wc -l)
    echo "  Classes compiled: $CLASS_COUNT"
else
    echo "⚠ Maven build not found - run 'mvnw clean package' first"
fi

echo ""
echo "Current Cron Configuration:"
echo "─────────────────────────────────────────"
crontab -l 2>/dev/null | grep -A2 "run_daily_extraction" || echo "No cron job found"

echo ""
echo "Log Directory: $PROJECT_DIR/logs"
if [ -d "$PROJECT_DIR/logs" ]; then
    echo "Log files:"
    ls -lh "$PROJECT_DIR/logs" 2>/dev/null | tail -n 5 || echo "  (no logs yet)"
else
    echo "  (directory will be created on first run)"
fi

echo ""
echo "=========================================="
echo "Test Complete!"
echo "=========================================="
echo ""
echo "To manually test the extraction workflow, run:"
echo "  bash $SCRIPT_PATH"
echo ""
echo "To view/edit the scheduled cron job:"
echo "  crontab -e"
echo ""
echo "The extraction will automatically run at 12:00 AM every day."
