#!/usr/bin/env python3
"""Publish a verified UI bundle, copying hashed assets before atomically switching index.html."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tarfile

archive = Path('/tmp/knowledge-delivery.tar.gz')
assert hashlib.sha256(archive.read_bytes()).hexdigest() == '33e2ec1088fafdc594847e3b40cb48862face974d3c535c13c7b5855387c39fa'
stage = Path('/tmp/knowledge-delivery')
stage.mkdir(exist_ok=True)
with tarfile.open(archive) as stream:
    stream.extractall(stage,filter='data')
release = Path('/opt/data-center/apps/knowledge/current').resolve()
for item in (stage/'frontend').rglob('*'):
    relative = item.relative_to(stage/'frontend')
    if str(relative) == 'index.html':
        continue
    target = release/'frontend'/relative
    if item.is_dir():
        target.mkdir(exist_ok=True)
        target.chmod(0o755)
    else:
        shutil.copyfile(item,target)
        target.chmod(0o644)
temporary = release/'frontend/.index-next.html'
shutil.copyfile(stage/'frontend/index.html',temporary)
temporary.chmod(0o644)
os.replace(temporary,release/'frontend/index.html')
shutil.copytree(stage/'production',release/'deploy/production',dirs_exist_ok=True)
for item in (release/'deploy/production').rglob('*'):
    item.chmod(0o755 if item.is_dir() else 0o644)
shutil.copyfile(stage/'foundation-README.md','/opt/data-center/apps/foundation/README.md')
shutil.copyfile(__file__,release/'deploy/production/publish-ui.py')
(release/'deploy/production/publish-ui.py').chmod(0o644)
subprocess.run(['nginx','-t'],check=True)
subprocess.run(['systemctl','start','--no-block','data-center-host-backup'],check=True)
print('FINAL_UI_PUBLISHED; BACKUP_STARTED')
