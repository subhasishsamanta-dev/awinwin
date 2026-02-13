# Daily Extraction & Upload Automation Setup

## Overview

This automation system runs the Swedish Players extraction and API upload workflow **every day at 12:00 AM (midnight)**.

### Workflow Process

```
12:00 AM (Daily)
    ↓
[1] Clear Target Files
    ├─ recent_swedish_players_data.json
    ├─ recent_swedish_players_ids.txt
    ├─ recent_swedish_players_profiles.jsonl
    ├─ recent_swedish_players_urls.txt
    ├─ swedish_extractor_status.json
    └─ team.txt
    ↓
[2] Build Maven Project
    ↓
[3] Run SwedishPlayersExtractor
    │  └─ Extracts recent Swedish player data
    │
[4] Wait for Extraction to Complete
    ↓
[5] Verify Output Data
    ↓
[6] Run ApiUploader
    │  └─ Uploads extracted data to API
    │
[7] Log Execution Results
```

## Files

### Main Scripts

1. **`run_daily_extraction.sh`** - Main automation script
   - Clears files
   - Builds Maven project
   - Runs SwedishPlayersExtractor
   - Runs ApiUploader
   - Manages logging

2. **`setup_cron.sh`** - Cron job configuration script
   - Installs/updates cron job
   - Sets time to 12:00 AM daily

3. **`test_workflow.sh`** - Testing and verification script
   - Verifies setup status
   - Displays cron configuration
   - Shows log files

### Logging

All execution logs are stored in: `/home/bit1148/Videos/Elite Sports All/logs/`

**Log files:**
- `daily_extraction_YYYYMMDD_HHMMSS.log` - Detailed execution logs
- `cron.log` - Cron scheduler logs

## Setup Status

✅ **Current Status: CONFIGURED AND READY**

```
✓ Automation script created and executable
✓ Maven project built (24 classes compiled)
✓ Cron job configured for 12:00 AM daily
✓ Logging infrastructure ready
```

## Cron Job Details

**Schedule:** 12:00 AM (00:00) every day  
**Command:** 
```bash
0 0 * * * cd /home/bit1148/Videos/Elite Sports All && /home/bit1148/Videos/Elite Sports All/run_daily_extraction.sh >> /home/bit1148/Videos/Elite Sports All/logs/cron.log 2>&1
```

**Location:** System crontab (user: `bit1148`)

## Manual Execution

To manually run the extraction workflow without waiting for 12 AM:

```bash
bash /home/bit1148/Videos/Elite Sports All/run_daily_extraction.sh
```

This will:
1. Execute immediately
2. Create a detailed log with timestamp
3. Display progress in the terminal
4. Return exit code 0 on success, non-zero on failure

## Monitoring and Logs

### View Recent Logs

```bash
# List all logs
ls -lh /home/bit1148/Videos/Elite Sports All/logs/

# View latest execution log (most recent)
tail -f /home/bit1148/Videos/Elite Sports All/logs/*.log

# View specific log
tail -100 /home/bit1148/Videos/Elite Sports All/logs/daily_extraction_YYYYMMDD_HHMMSS.log
```

### Log Format

Each execution log includes:
- Timestamp of all operations
- File clearing status
- Maven build output
- SwedishPlayersExtractor output
- Player count extracted
- ApiUploader output
- Final summary with status

## Managing Cron Job

### View Current Cron Job

```bash
crontab -l
```

### Edit Cron Job

```bash
crontab -e
```

### Remove Cron Job

```bash
crontab -r
```

### Reinstall Cron Job

```bash
bash /home/bit1148/Videos/Elite Sports All/setup_cron.sh
```

## Troubleshooting

### Script Not Running

1. **Check if cron service is running:**
   ```bash
   sudo service cron status
   ```

2. **Verify cron job exists:**
   ```bash
   crontab -l | grep run_daily_extraction
   ```

3. **Check system logs:**
   ```bash
   sudo grep CRON /var/log/syslog | tail -20
   ```

### Build Failures

If Maven build fails:
1. Check Java version: `java -version`
2. Verify dependencies: `./mvnw dependency:tree`
3. Clean and rebuild: `./mvnw clean package -DskipTests`

### Extraction Failures

1. Check environment variables:
   ```bash
   echo $EP_EMAIL
   echo $EP_PASSWORD
   echo $EP_COOKIE_HEADER
   ```

2. Verify data files exist:
   ```bash
   ls -la /home/bit1148/Videos/Elite Sports All/recent_swedish_players*
   ```

3. Check execution permissions:
   ```bash
   ls -lh /home/bit1148/Videos/Elite Sports All/run_daily_extraction.sh
   ```

### Upload Failures

1. Verify API endpoint is reachable:
   ```bash
   curl -I https://webdev11.mydevfactory.com/nabaruna-sinha/awinwin/public/api/update-scrap-player-details
   ```

2. Check network connectivity:
   ```bash
   ping webdev11.mydevfactory.com
   ```

## Testing

### Quick Test of Setup

```bash
bash /home/bit1148/Videos/Elite Sports All/test_workflow.sh
```

This verifies:
- Scripts are executable
- Maven build is complete
- Cron job is configured
- Log directory exists

### Full Workflow Test

```bash
bash /home/bit1148/Videos/Elite Sports All/run_daily_extraction.sh
```

This runs the complete workflow manually to test all components.

## Environment Variables Required

The following environment variables must be set (typically in `.env` file):

- `EP_EMAIL` - Elite Prospects login email
- `EP_PASSWORD` - Elite Prospects login password
- `EP_COOKIE_HEADER` - Optional: Elite Prospects cookies
- `GAMES_URL` - Optional: Elite Prospects games URL

Verify with:
```bash
echo "Email: $EP_EMAIL"
echo "Password: ${EP_PASSWORD:0:3}***"
```

## Statistics

**Project Structure:**
- Java Classes: 24 compiled
- Main Entry Point: SwedishPlayersExtractor
- Dependencies: Gson, jsoup, Retrofit2, OpenCSV, Jackson, etc.

**Processing:**
- Countries: Sweden (SWE)
- Data Output: JSON, JSONL, CSV formats
- API Endpoint: https://webdev11.mydevfactory.com/nabaruna-sinha/awinwin/public/api/

## Success Indicators

✅ Automation is working correctly when:
1. Cron job exists: `crontab -l` shows the job
2. Files are cleared daily: Check timestamps of target files
3. Logs are created: New logs appear in `/logs/` directory
4. Players extracted: Player count > 0 in logs
5. Data uploaded: ApiUploader shows successful uploads

## Additional Commands

```bash
# Monitor cron execution in real-time
sudo tail -f /var/log/syslog | grep CRON

# Check disk space for logs
du -sh /home/bit1148/Videos/Elite Sports All/logs/

# Verify script syntax
bash -n /home/bit1148/Videos/Elite Sports All/run_daily_extraction.sh

# Get last execution time
stat /home/bit1148/Videos/Elite Sports All/logs/*.log | grep Modify
```

## Support & Documentation

For issues or questions:
1. Check logs: `tail /home/bit1148/Videos/Elite Sports All/logs/*.log`
2. Run test: `bash test_workflow.sh`
3. Check environment: `echo $EP_EMAIL $EP_PASSWORD`
4. Verify network: `ping webdev11.mydevfactory.com`

---

**Setup Date:** 2026-02-13  
**Last Modified:** 2026-02-13  
**Status:** ✅ ACTIVE AND RUNNING
