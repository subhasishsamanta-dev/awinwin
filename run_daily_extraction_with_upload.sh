#!/bin/bash
# Daily extraction and upload script for Swedish Players

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

if [ -x "$SCRIPT_DIR/mvnw" ]; then
    MAVEN_CMD="$SCRIPT_DIR/mvnw"
elif command -v mvn >/dev/null 2>&1; then
    MAVEN_CMD="mvn"
else
    exit 1
fi

if [ -f .env ]; then
    set +a
    source .env
    set -a
else
    exit 1
fi

mkdir -p logs
LOG_FILE="logs/daily_extraction_$(date +%Y%m%d_%H%M%S).log"

on_exit() {
    exit_code=$?
    echo "❗ Script exiting with code: $exit_code" | tee -a "$LOG_FILE"
}
trap on_exit EXIT INT TERM HUP

# --- PROCESS 1: SwedishPlayersExtractor ---
echo "=========================================="
echo "🚀 [PROCESS 1/3] Starting SwedishPlayersExtractor"
echo "Started at: $(date)"
echo "=========================================="

EXTRACT_ATTEMPTS=0
MAX_EXTRACT_RETRIES=3
while [ $EXTRACT_ATTEMPTS -lt $MAX_EXTRACT_RETRIES ]; do
    echo "✅ SwedishPlayersExtractor attempt $((EXTRACT_ATTEMPTS+1))/$MAX_EXTRACT_RETRIES started" | tee -a "$LOG_FILE"
    "$MAVEN_CMD" exec:java -Dexec.mainClass=com.brainium.core.SwedishPlayersExtractor 2>&1 | tee -a "$LOG_FILE"
    EXTRACT_EXIT=${PIPESTATUS[0]}
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$LOG_FILE"
    echo " SwedishPlayersExtractor exited with code: $EXTRACT_EXIT" | tee -a "$LOG_FILE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$LOG_FILE"
    
    if [ $EXTRACT_EXIT -eq 0 ]; then
        echo "✅ SwedishPlayersExtractor completed successfully" | tee -a "$LOG_FILE"
        break
    fi
    
    EXTRACT_ATTEMPTS=$((EXTRACT_ATTEMPTS+1))
    echo "⚠️  Extraction failed (attempt $EXTRACT_ATTEMPTS/$MAX_EXTRACT_RETRIES, exit code: $EXTRACT_EXIT). Retrying in 10s..." | tee -a "$LOG_FILE"
    sleep 10
done

if [ $EXTRACT_EXIT -ne 0 ]; then
    echo "❌ SwedishPlayersExtractor failed after $MAX_EXTRACT_RETRIES attempts" | tee -a "$LOG_FILE"
    exit 1
fi

# --- WAIT 10 SECONDS BEFORE NEXT PROCESS ---
echo ""
echo "⏳ Waiting 10 seconds before starting ApiUploader..." | tee -a "$LOG_FILE"
sleep 10

# --- PROCESS 2: ApiUploader ---
echo "=========================================="
echo "🚀 [PROCESS 2/3] Starting ApiUploader"
echo "Started at: $(date)"
echo "=========================================="

UPLOAD_ATTEMPTS=0
MAX_UPLOAD_RETRIES=3
while [ $UPLOAD_ATTEMPTS -lt $MAX_UPLOAD_RETRIES ]; do
    echo "✅ ApiUploader attempt $((UPLOAD_ATTEMPTS+1))/$MAX_UPLOAD_RETRIES started" | tee -a "$LOG_FILE"
    "$MAVEN_CMD" exec:java -Dexec.mainClass=com.brainium.core.ApiUploader 2>&1 | tee -a "$LOG_FILE"
    UPLOAD_EXIT=${PIPESTATUS[0]}

    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$LOG_FILE"
    echo " ApiUploader exited with code: $UPLOAD_EXIT" | tee -a "$LOG_FILE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$LOG_FILE"

    if [ $UPLOAD_EXIT -eq 0 ]; then
        echo "✅ ApiUploader completed successfully" | tee -a "$LOG_FILE"
        break
    fi

    UPLOAD_ATTEMPTS=$((UPLOAD_ATTEMPTS+1))
    echo "⚠️  Upload failed (attempt $UPLOAD_ATTEMPTS/$MAX_UPLOAD_RETRIES, exit code: $UPLOAD_EXIT). Retrying in 15s..." | tee -a "$LOG_FILE"
    sleep 15
done

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$LOG_FILE"
if [ $UPLOAD_EXIT -ne 0 ]; then
    echo "❌ ApiUploader failed. Terminating script with uploader exit code: $UPLOAD_EXIT" | tee -a "$LOG_FILE"
    exit $UPLOAD_EXIT
fi

# --- WAIT 10 SECONDS BEFORE CLEANUP ---
echo ""
echo "⏳ Waiting 10 seconds before cleanup..." | tee -a "$LOG_FILE"
sleep 10

# --- PROCESS 3: Cleanup ---
echo "=========================================="
echo "🚀 [PROCESS 3/3] Cleaning up files"
echo "Started at: $(date)"
echo "=========================================="

rm -f swedish_extractor.lock swedish_extractor.pid recent_swedish_players_data.json swedish_extractor_status.json
CLEANUP_EXIT=$?

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$LOG_FILE"
echo "✅ Cleanup exited with code: $CLEANUP_EXIT" | tee -a "$LOG_FILE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$LOG_FILE"

echo ""
echo "=========================================="
echo "✅ All processes completed successfully!"
echo "Completed at: $(date)"
echo "=========================================="
