"""Try the original local MinIO for files absent from disk and NAS; never reconstruct originals."""
import hashlib,json,subprocess,sys,os,winreg
from pathlib import Path
from minio import Minio
p=Path(sys.argv[1]); manifest=json.loads((p/'manifest.json').read_text(encoding='utf-8'))
ce=dict(x.split('=',1) for x in json.loads(subprocess.check_output(['docker','inspect','leman-dev-minio']))[0]['Config']['Env'] if '=' in x)
client=Minio('127.0.0.1:9000',access_key=ce['MINIO_ROOT_USER'],secret_key=ce['MINIO_ROOT_PASSWORD'],secure=False)
buckets=[b.name for b in client.list_buckets()]
recovered=0
def env(n):
    if os.getenv(n):return os.getenv(n)
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER,'Environment') as k:return winreg.QueryValueEx(k,n)[0]
from urllib.parse import urlparse
endpoint=urlparse(env('AI_DOCUMENT_MINIO_ENDPOINT'))
nas=Minio(endpoint.netloc,access_key=env('AI_DOCUMENT_MINIO_ACCESS_KEY'),secret_key=env('AI_DOCUMENT_MINIO_SECRET_KEY'),secure=endpoint.scheme=='https')
for entry in manifest['files']:
    if 'missing' not in entry: continue
    target=p/'files'/entry['key']
    for backend,bucket in [(client,b) for b in buckets]+[(nas,env('AI_DOCUMENT_MINIO_BUCKET'))]:
        try:
            backend.fget_object(bucket,entry['key'],str(target))
            entry.pop('missing');entry['bytes']=target.stat().st_size
            entry['sha256']=hashlib.sha256(target.read_bytes()).hexdigest();recovered+=1
            break
        except Exception as e: entry['missing']=getattr(e,'code',type(e).__name__)
(p/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'recovered':recovered,'missing':sum('missing' in x for x in manifest['files']),'active_missing':sum('missing' in x and x['active'] for x in manifest['files']),'error_codes':sorted(set(x.get('missing','') for x in manifest['files']))}))
