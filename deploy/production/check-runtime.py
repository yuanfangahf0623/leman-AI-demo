#!/usr/bin/env python3
"""Read-only post-restart verification; prints only sanitized results."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request

os.umask(0o077)
def run(args, input=None):
    result = subprocess.run(args,input=input,text=True,capture_output=True,timeout=60)
    assert result.returncode == 0, 'Command failed: ' + args[0]
    return result.stdout.strip()

def envfile(path):
    return {k:v.strip('"') for k,v in (line.split('=',1) for line in Path(path).read_text().splitlines() if '=' in line)}

env = envfile('/opt/data-center/secrets/knowledge.env')
assert env['LEMAN_BOOTSTRAP_ADMIN_ENABLED']=='false' and 'LEMAN_ADMIN_PASSWORD' not in env
unit = dict(line.split('=',1) for line in run(['systemctl','show','leman-knowledge',
    '-p','ActiveState','-p','SubState','-p','MemoryCurrent','-p','MemoryMax','-p','MemoryHigh','-p','MemorySwapMax','-p','CPUQuotaPerSecUSec']).splitlines())
assert unit['ActiveState']=='active' and unit['MemoryMax']==str(12*1024**3) and unit['MemorySwapMax']=='0'
assert run(['systemctl','is-enabled','leman-knowledge'])=='enabled'
pg = run(['docker','exec','-i','data-center-ai-postgres','sh','-c',
    'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -h127.0.0.1 -U yudao_ai -d yudao_ai -At -v ON_ERROR_STOP=1'],
    env['AI_PGVECTOR_PASSWORD']+"\nSELECT count(*) FROM ai_vector_store; SELECT '[1,0]'::vector <=> '[0,1]'::vector;\n")
assert pg.splitlines()[-1]=='1'
runtime = envfile('/run/leman-knowledge/model.env')
curl_config = 'url = "https://api.openai.com/v1/models"\nheader = "Authorization: Bearer '+runtime['AI_API_KEY']+'"\n'
openai = run(['runuser','-u','leman-knowledge','--','curl','--max-time','35','-sS','-o','/dev/null','-w','%{http_code}','--config','-'],curl_config)
assert openai=='200', 'Model endpoint authentication failed'
http = {}
for port in [80,18080,18081]:
    with urllib.request.urlopen('http://127.0.0.1:'+str(port),timeout=20) as response:
        http[str(port)]=response.status
assert all(code==200 for code in http.values())
stored = run(['mysql','-NB','yudao_ai'], 'SELECT COUNT(*) FROM ai_chat_message; SELECT COUNT(*) FROM ai_dify_conversation;')
assert int(stored.splitlines()[0])>=4 and int(stored.splitlines()[1])>=1
result = {'checked_at':datetime.datetime.now().astimezone().isoformat(),'service':unit,'enabled':True,
          'pgvector_authenticated':True,'openai_http':int(openai),'web_http':http,
          'conversations_survived_restart':True,'bootstrap_disabled':True,
          'app_sha256':hashlib.sha256(Path('/opt/data-center/apps/knowledge/current/app.jar').read_bytes()).hexdigest(),
          'successful':True}
target=Path('/data/backup/knowledge-deploy/verification/runtime.json')
target.write_text(json.dumps(result,indent=2))
print(json.dumps(result),flush=True)
