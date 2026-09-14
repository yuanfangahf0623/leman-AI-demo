#!/usr/bin/env python3
"""Unseal server OpenAI credentials into root-only tmpfs for systemd, never into the release."""
import importlib.util
import os
import pwd
import subprocess
from pathlib import Path

os.umask(0o077)
# Reuse the already configured company gateway, only for this dedicated service user.
uid = pwd.getpwnam('leman-knowledge').pw_uid
rules = subprocess.check_output(['ip', 'rule', 'show'], text=True)
if not any('uidrange ' + str(uid) + '-' + str(uid) in line and 'lookup 18080' in line
           for line in rules.splitlines()):
    subprocess.run(['ip', 'rule', 'add', 'priority', '18081', 'uidrange',
                    str(uid) + '-' + str(uid), 'lookup', '18080'], check=True)
spec = importlib.util.spec_from_file_location(
    'dify_credentials', '/opt/data-center/apps/dify/current/credential_config.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
credentials = module.load_credentials()
key = credentials.get('embedding_api_key') or credentials['api_key']
assert key and not any(c in key for c in '\r\n"\\'), 'Unexpected credential format'
target = Path('/run/leman-knowledge/model.env')
target.write_text('AI_API_KEY="' + key + '"\n')
target.chmod(0o600)
print('Model credentials loaded into protected runtime file')
