#!/bin/bash
# Daily extraction and upload script for Swedish Players
# Runs SwedishPlayersExtractor followed by ApiUploader

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Load environment variables from .env file
if [ -f .env ]; then
    set -a
    source .env
    set +a
else
    echo "ERROR: .env file not found!"
    exit 1
fi

# Create logs directory
mkdir -p logs
LOG_FILE="logs/daily_extraction_$(date +%Y%m%d_%H%M%S).log"

{
    echo "=========================================="
    echo "🚀 Starting Daily Extraction"
    echo "Started at: $(date)"
    echo "=========================================="
    
    # Step 1: Run SwedishPlayersExtractor
    echo ""
    echo "📥 [1/2] Running SwedishPlayersExtractor..."
    mvn exec:java -Dexec.mainClass=com.brainium.core.SwedishPlayersExtractor
    EXTRACT_EXIT=$?
    
    if [ $EXTRACT_EXIT -eq 0 ]; then
        echo "✅ Extraction completed successfully"
        # Wait 10 seconds before uploading
        echo "⏳ Waiting 10 seconds before API upload..."
        sleep 10
        # Step 2: Run ApiUploader
        echo ""
        echo "📤 [2/2] Running ApiUploader..."
        mvn exec:java -Dexec.mainClass=com.brainium.core.ApiUploader
        UPLOAD_EXIT=$?
        
        if [ $UPLOAD_EXIT -eq 0 ]; then
            echo "✅ API upload completed successfully"
            # Delete status and data files after successful upload
            echo "🧹 Cleaning up status and data files..."
            rm -f status.json swedish_extractor_status.json recent_swedish_players_data.json recent_swedish_players_profiles.jsonl recent_swedish_players_ids.txt recent_swedish_players_urls.txt team.txt
            echo "🗑️  Deleted: status.json, swedish_extractor_status.json, recent_swedish_players_data.json, recent_swedish_players_profiles.jsonl, recent_swedish_players_ids.txt, recent_swedish_players_urls.txt, team.txt"
            OVERALL_STATUS="SUCCESS"
        else
            echo "❌ API upload failed (exit code: $UPLOAD_EXIT)"
            OVERALL_STATUS="PARTIAL_FAILURE"
        fi
    else
        echo "❌ Extraction failed (exit code: $EXTRACT_EXIT)"
        echo "⏭️  Skipping API upload"
        OVERALL_STATUS="FAILURE"
    fi
    
    echo ""
    echo "=========================================="
    echo "📊 Final Status: $OVERALL_STATUS"
    echo "Finished at: $(date)"
    echo "=========================================="
    
} 2>&1 | tee -a "$LOG_FILE"

# Exit with appropriate code
if [ "$OVERALL_STATUS" = "SUCCESS" ]; then
    exit 0
elif [ "$OVERALL_STATUS" = "PARTIAL_FAILURE" ]; then
    exit 2
else
    exit 1
fi
