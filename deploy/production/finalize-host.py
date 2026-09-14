#!/usr/bin/env python3
"""Install the final operational bundle, disable bootstrap password resets, and restart this app only."""
import os
from pathlib import Path
import shutil
import subprocess
import tarfile

os.umask(0o077)
stage = Path('/tmp/knowledge-finalize')
stage.mkdir(exist_ok=True)
with tarfile.open('/tmp/knowledge-finalize.tar.gz') as archive:
    archive.extractall(stage, filter='data')
release = Path('/opt/data-center/apps/knowledge/current').resolve()
for source in (stage/'production').iterdir():
    if source.is_file():
        target = release/'deploy/production'/source.name
        shutil.copyfile(source,target)
        target.chmod(0o644)
for source in (stage/'foundation').iterdir():
    target = Path('/opt/data-center/apps/foundation')/source.name
    shutil.copyfile(source,target)
    target.chmod(0o755)
config = Path('/opt/data-center/secrets/knowledge.env')
lines = [line for line in config.read_text().splitlines() if not line.startswith('LEMAN_ADMIN_PASSWORD=')]
lines = ['LEMAN_BOOTSTRAP_ADMIN_ENABLED="false"' if line.startswith('LEMAN_BOOTSTRAP_ADMIN_ENABLED=') else line for line in lines]
config.write_text('\n'.join(lines)+'\n')
config.chmod(0o600)
subprocess.run(['bash','-n','/opt/data-center/apps/foundation/backup-host.sh'],check=True)
subprocess.run(['systemctl','restart','leman-knowledge.service'],check=True)
subprocess.run(['systemctl','start','--no-block','data-center-host-backup.service'],check=True)
print('FINAL_CONFIG_INSTALLED; APP_RESTARTED; BACKUP_STARTED')
