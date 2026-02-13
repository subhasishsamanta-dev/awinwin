#!/bin/bash

##############################################################################
# Daily Swedish Players Extraction and Upload Script
# Runs at 12 AM every day
# 
# Workflow:
# 1. Clear target files
# 2. Run SwedishPlayersExtractor to fetch player data
# 3. Wait for extraction to complete
# 4. Run ApiUploader to upload data to the API
# 5. Log all activities
##############################################################################

# Set up logging
LOG_DIR="/home/bit1148/Videos/Elite Sports All/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/daily_extraction_$(date +%Y%m%d_%H%M%S).log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script paths
PROJECT_DIR="/home/bit1148/Videos/Elite Sports All"
MAVEN_CMD="$PROJECT_DIR/mvnw"

# Timestamp function
timestamp() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')]"
}

# Log function
log() {
    echo "$(timestamp) $1" | tee -a "$LOG_FILE"
}

# Error function
error() {
    echo -e "$(timestamp) ${RED}ERROR: $1${NC}" | tee -a "$LOG_FILE"
}

# Success function
success() {
    echo -e "$(timestamp) ${GREEN}✓ $1${NC}" | tee -a "$LOG_FILE"
}

# Info function
info() {
    echo -e "$(timestamp) ${BLUE}ℹ $1${NC}" | tee -a "$LOG_FILE"
}

##############################################################################
# START OF EXECUTION
##############################################################################

log "==================== DAILY EXTRACTION STARTED ===================="

# Change to project directory
cd "$PROJECT_DIR" || {
    error "Failed to change to project directory: $PROJECT_DIR"
    exit 1
}

log "Project directory: $PROJECT_DIR"
log "Log file: $LOG_FILE"

# Step 1: Clear target files
info "Step 1: Clearing target files..."
FILES_TO_CLEAR=(
    "recent_swedish_players_data.json"
    "recent_swedish_players_ids.txt"
    "recent_swedish_players_profiles.jsonl"
    "recent_swedish_players_urls.txt"
    "swedish_extractor_status.json"
    "team.txt"
)

for file in "${FILES_TO_CLEAR[@]}"; do
    if [ -f "$file" ]; then
        truncate -s 0 "$file"
        log "  ✓ Cleared: $file"
    else
        log "  ⚠ File not found (will be created): $file"
    fi
done
success "Files cleared successfully"

# Step 2: Build the Maven project
info "Step 2: Building Maven project..."
if ! $MAVEN_CMD clean package -q -DskipTests 2>> "$LOG_FILE"; then
    error "Maven build failed!"
    exit 1
fi
success "Maven project built successfully"

# Step 3: Run SwedishPlayersExtractor
info "Step 3: Running SwedishPlayersExtractor..."
log "Starting SwedishPlayersExtractor at $(date '+%Y-%m-%d %H:%M:%S')"

if ! java -cp "$PROJECT_DIR/target/classes:$PROJECT_DIR/target/dependency/*" \
         -Dfile.encoding=UTF-8 \
         com.brainium.core.SwedishPlayersExtractor >> "$LOG_FILE" 2>&1; then
    error "SwedishPlayersExtractor execution failed!"
    exit 1
fi

log "SwedishPlayersExtractor completed at $(date '+%Y-%m-%d %H:%M:%S')"
success "SwedishPlayersExtractor finished successfully"

# Step 4: Verify extraction output
info "Step 4: Verifying extraction output..."
if [ ! -f "recent_swedish_players_data.json" ] || [ ! -s "recent_swedish_players_data.json" ]; then
    error "No player data was extracted. Check SwedishPlayersExtractor logs."
    exit 1
fi

PLAYER_COUNT=$(grep -o "\"userId\"" recent_swedish_players_data.json | wc -l)
log "  ✓ Extracted data file found with approximately $PLAYER_COUNT players"
success "Extraction output verified"

# Step 5: Run ApiUploader
info "Step 5: Running ApiUploader..."
log "Starting ApiUploader at $(date '+%Y-%m-%d %H:%M:%S')"

if ! java -cp "$PROJECT_DIR/target/classes:$PROJECT_DIR/target/dependency/*" \
         -Dfile.encoding=UTF-8 \
         com.brainium.core.ApiUploader >> "$LOG_FILE" 2>&1; then
    error "ApiUploader execution failed!"
    exit 1
fi

log "ApiUploader completed at $(date '+%Y-%m-%d %H:%M:%S')"
success "ApiUploader finished successfully"

##############################################################################
# COMPLETION
##############################################################################

success "==================== DAILY EXTRACTION COMPLETED ===================="
success "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"

# Create a summary log
cat >> "$LOG_FILE" << EOF

=== EXECUTION SUMMARY ===
Start Time: $(date '+%Y-%m-%d %H:%M:%S')
End Time: $(date '+%Y-%m-%d %H:%M:%S')
Status: SUCCESS
Players Extracted: $PLAYER_COUNT
Upload Status: COMPLETED

For full details, see: $LOG_FILE
EOF

exit 0
