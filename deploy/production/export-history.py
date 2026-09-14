"""Read-only local historical export. Credentials remain in memory; output directory must be private."""
import datetime, gzip, hashlib, json, os, subprocess, sys, winreg
from pathlib import Path
import pymysql

out = Path(sys.argv[1]).resolve()
out.mkdir(parents=True, exist_ok=True)
def env(name, default=''):
    if os.environ.get(name): return os.environ[name]
    for hive, key in [(winreg.HKEY_CURRENT_USER, 'Environment'), (winreg.HKEY_LOCAL_MACHINE, r'SYSTEM\CurrentControlSet\Control\Session Manager\Environment')]:
        try:
            with winreg.OpenKey(hive, key) as k: return winreg.QueryValueEx(k,name)[0]
        except OSError: pass
    return default
def container_env(name):
    data=json.loads(subprocess.check_output(['docker','inspect',name]))[0]
    return dict(x.split('=',1) for x in data['Config']['Env'] if '=' in x)
def dump(args, target):
    with gzip.open(out/target,'wb') as f:
        p=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        while block:=p.stdout.read(1024*1024): f.write(block)
        err=p.stderr.read(); assert p.wait()==0, 'Database dump failed'
    print(target+' completed',flush=True)
def encode(value):
    if isinstance(value,bytes): return {'$bytes':value.hex()}
    if isinstance(value,(datetime.datetime,datetime.date)): return {'$datetime':value.isoformat(sep=' ') if isinstance(value,datetime.datetime) else value.isoformat()}
    import decimal
    if isinstance(value,decimal.Decimal): return {'$decimal':str(value)}
    raise TypeError(type(value).__name__)

ce=container_env('leman-dev-mysql')
db=pymysql.connect(host='127.0.0.1',port=3306,user='root',password=ce['MYSQL_ROOT_PASSWORD'],database='yudao_ai',charset='utf8mb4',cursorclass=pymysql.cursors.DictCursor)
tables=['system_users','system_dept','system_role','system_user_role','system_user_post','system_post',
        'ai_knowledge_base','ai_knowledge_directory','ai_document','ai_document_chunk','ai_data_source',
        'ai_data_source_raw_record','ai_sync_job','ai_sync_record','ai_twohaohr_attendance_record',
        'ai_chat_conversation','ai_chat_message','ai_chat_citation']
manifest={'created':datetime.datetime.now().isoformat(),'tables':{},'files':[]}
with db.cursor() as c:
    c.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ')
    c.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
    for table in tables:
        c.execute('SELECT * FROM `'+table+'`'); rows=c.fetchall()
        with gzip.open(out/(table+'.json.gz'),'wt',encoding='utf-8') as f: json.dump(rows,f,ensure_ascii=False,default=encode)
        manifest['tables'][table]={'count':len(rows),'active':sum(r.get('deleted',b'\0') in (b'\0',0,False) for r in rows)}
        if table=='ai_document': documents=rows
    db.rollback()
base=Path(env('AI_DOCUMENT_STORAGE_BASE_PATH',r'F:\GitHub\leman-AI-demo\.data\ai-documents')).resolve()
import shutil
for doc in documents:
    key=doc.get('object_key')
    if not key: continue
    source=(base/key).resolve()
    assert source.is_relative_to(base)
    entry={'document_id':doc['id'],'key':key,'active':doc['deleted']==b'\0'}
    target=out/'files'/key
    target.parent.mkdir(parents=True,exist_ok=True)
    if source.is_file(): shutil.copyfile(source,target)
    else:
        from minio import Minio
        from urllib.parse import urlparse
        endpoint=urlparse(env('AI_DOCUMENT_MINIO_ENDPOINT','http://192.168.19.246:9000'))
        client=Minio(endpoint.netloc,access_key=env('AI_DOCUMENT_MINIO_ACCESS_KEY'),secret_key=env('AI_DOCUMENT_MINIO_SECRET_KEY'),secure=endpoint.scheme=='https')
        try: client.fget_object(env('AI_DOCUMENT_MINIO_BUCKET','yudao-ai-documents'),key,str(target))
        except Exception as e: entry['missing']=type(e).__name__
    if target.is_file():
        entry['bytes']=target.stat().st_size
        entry['sha256']=hashlib.sha256(target.read_bytes()).hexdigest()
    manifest['files'].append(entry)
(out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'tables':manifest['tables'],'files':len(manifest['files']),'missing':sum('missing' in x for x in manifest['files'])}),flush=True)
dump(['docker','exec','leman-dev-mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --events --triggers --set-gtid-purged=OFF yudao_ai'],'source-mysql.sql.gz')
dump(['docker','exec','leman-dev-postgres','sh','-c','exec pg_dump -U "$POSTGRES_USER" -d yudao_ai --no-owner --no-acl'],'source-postgres.sql.gz')
