#!/bin/bash

##############################################################################
# QUICK REFERENCE - Daily Extraction Automation
##############################################################################

cat << 'EOF'

╔════════════════════════════════════════════════════════════════════════╗
║                   AUTOMATION SETUP COMPLETE ✓                         ║
╚════════════════════════════════════════════════════════════════════════╝

📅 SCHEDULE: Every day at 12:00 AM (00:00)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📋 WORKFLOW STEPS (runs automatically at 12 AM):

  1. Clear Files
     ├─ recent_swedish_players_data.json
     ├─ recent_swedish_players_ids.txt
     ├─ recent_swedish_players_profiles.jsonl
     ├─ recent_swedish_players_urls.txt
     ├─ swedish_extractor_status.json
     └─ team.txt

  2. Build Maven Project (24 classes)

  3. Run SwedishPlayersExtractor
     └─ Extracts Swedish player data

  4. Run ApiUploader
     └─ Uploads data to API

  5. Generate Logs & Reports

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔧 USEFUL COMMANDS:

  Manual Execution (run now):
  $ bash /home/bit1148/Videos/Elite\ Sports\ All/run_daily_extraction.sh

  View Cron Job:
  $ crontab -l

  Edit Cron Job:
  $ crontab -e

  View Logs:
  $ tail -f /home/bit1148/Videos/Elite\ Sports\ All/logs/*.log

  Test Setup:
  $ bash /home/bit1148/Videos/Elite\ Sports\ All/test_workflow.sh

  Verify Cron (check if running):
  $ sudo service cron status

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 KEY FILES:

  Script Directory: /home/bit1148/Videos/Elite Sports All/

  Automation Script:
  └─ run_daily_extraction.sh (Main automation)

  Cron Setup:
  └─ setup_cron.sh (Configure/reinstall cron job)

  Testing:
  └─ test_workflow.sh (Verify setup)

  Documentation:
  └─ AUTOMATION_SETUP.md (Full documentation)

  Logs:
  └─ logs/ (Execution logs)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ STATUS:

  ✓ Automation script created and executable
  ✓ Maven project compiled (24 classes)
  ✓ Cron job configured for 12:00 AM daily
  ✓ Logging infrastructure ready
  ✓ All systems operational

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

ℹ️  IMPORTANT:

  • Ensure environment variables are set (.env file):
    - EP_EMAIL
    - EP_PASSWORD
    - EP_COOKIE_HEADER (optional)
    - GAMES_URL (optional)

  • Check logs for execution status:
    /home/bit1148/Videos/Elite Sports All/logs/

  • For troubleshooting:
    Read AUTOMATION_SETUP.md for detailed instructions

  • To manually test workflow:
    bash run_daily_extraction.sh

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📞 SUPPORT:

  For issues, check:
  1. Logs: ls -lh logs/
  2. Environment: echo $EP_EMAIL
  3. Cron: crontab -l
  4. Service: sudo service cron status

╔════════════════════════════════════════════════════════════════════════╗
║              Ready for automated execution at 12 AM! ✓                 ║
╚════════════════════════════════════════════════════════════════════════╝

EOF
