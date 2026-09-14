"""Root-only backup, staging and validation for the historical knowledge migration."""
import datetime,gzip,hashlib,json,os,subprocess,sys,tarfile
from pathlib import Path
os.umask(0o077)
p=Path('/data/backup/knowledge-history/20260909')
def run(args,data=None):
    r=subprocess.run(args,input=data,capture_output=True)
    if r.returncode: raise RuntimeError('Command failed: '+args[0]+' '+r.stderr.decode(errors='replace')[:300])
    return r.stdout
def mysql(sql,db='yudao_ai'):
    return run(['mysql','--default-character-set=utf8mb4','-NB',db],sql.encode()).decode().strip()
def pg(sql,db='yudao_ai'):
    return run(['docker','exec','-i','data-center-ai-postgres','sh','-c','exec psql -U "$POSTGRES_USER" -d '+db+' -At -v ON_ERROR_STOP=1'],sql.encode()).decode().strip()
mode=sys.argv[1]
if mode=='cleanup':
    assert (p/'applied.json').is_file() and (p/'api-verification.json').is_file()
    assert run(['systemctl','is-active','leman-knowledge']).strip()==b'active'
    mysql('DROP DATABASE knowledge_candidate_20260909; DROP DATABASE knowledge_history_20260909;')
    pg('DROP DATABASE knowledge_history_20260909;','template1')
    print('TEMPORARY_DATABASES_REMOVED; protected migration backups retained')
elif mode=='backup':
    p.mkdir(parents=True,exist_ok=False)
    with gzip.open(p/'server-mysql-before.sql.gz','wb') as f:
        f.write(run(['mysqldump','--single-transaction','--routines','--events','--triggers','--set-gtid-purged=OFF','yudao_ai']))
    with gzip.open(p/'server-postgres-before.sql.gz','wb') as f:
        f.write(run(['docker','exec','data-center-ai-postgres','sh','-c','exec pg_dump -U "$POSTGRES_USER" -d yudao_ai --no-owner --no-acl']))
    run(['tar','-czf',str(p/'server-files-config-before.tar.gz'),'-C','/','data/ai/knowledge','opt/data-center/secrets/knowledge.env','opt/data-center/apps/knowledge/current'])
    protected=['system_users','infra_config','ai_knowledge_base','ai_dify_conversation','ai_chat_conversation','ai_chat_message','ai_chat_citation']
    snapshot={t:mysql('SELECT * FROM `'+t+'` ORDER BY 1') for t in protected}
    (p/'protected-before.json').write_text(json.dumps(snapshot))
    print('BACKUP_READY '+str(p))
elif mode=='inventory':
    print(pg("SELECT column_name,data_type FROM information_schema.columns WHERE table_name='ai_vector_store' ORDER BY ordinal_position"))
    print('vector_count='+pg('SELECT count(*) FROM ai_vector_store'))
elif mode=='stage':
    archive=Path('/tmp/knowledge-history-20260909.tar.gz')
    assert hashlib.sha256(archive.read_bytes()).hexdigest()==sys.argv[2]
    with tarfile.open(archive) as tar:tar.extractall(p/'source',filter='data')
    source=p/'source'
    manifest=json.loads((source/'manifest.json').read_text())
    for item in manifest['files']:
        if 'sha256' in item:
            assert hashlib.sha256((source/'files'/item['key']).read_bytes()).hexdigest()==item['sha256']
    mysql('CREATE DATABASE knowledge_history_20260909 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    with gzip.open(source/'source-mysql.sql.gz','rb') as f:
        run(['mysql','knowledge_history_20260909'],f.read())
    pg('CREATE DATABASE knowledge_history_20260909;','template1')
    with gzip.open(source/'source-postgres.sql.gz','rb') as f:
        run(['docker','exec','-i','data-center-ai-postgres','sh','-c','exec psql -U "$POSTGRES_USER" -d knowledge_history_20260909 -v ON_ERROR_STOP=1'],f.read())
    print('STAGING_READY files='+str(sum('sha256' in x for x in manifest['files'])))
    print('staging_vectors='+pg('SELECT count(*) FROM ai_vector_store','knowledge_history_20260909'))
elif mode in ('rehearse','check-rehearsal','apply','verify'):
    source=p/'source'
    sys.path.insert(0,str(source/'python'))
    import pymysql,shutil,decimal,math
    def decode(x):
        if '$bytes' in x:return bytes.fromhex(x['$bytes'])
        return x
    def verify(dbname):
        socket=mysql('SELECT @@socket;')
        db=pymysql.connect(unix_socket=socket,user='root',database=dbname,charset='utf8mb4',cursorclass=pymysql.cursors.DictCursor)
        result={}
        with db.cursor() as c:
            for file in sorted((source/'expected').glob('*.json.gz')):
                table=file.name.removesuffix('.json.gz')
                expected=json.load(gzip.open(file,'rt',encoding='utf-8'),object_hook=decode)
                if not expected:continue
                c.execute('SHOW COLUMNS FROM `'+table+'`'); schema=c.fetchall()
                keys=[x['Field'] for x in schema if x['Key']=='PRI']
                jsoncols={x['Field'] for x in schema if x['Type']=='json'}
                c.execute('SELECT * FROM `'+table+'`')
                actual={tuple(r[k] for k in keys):r for r in c.fetchall()}
                for row in expected:
                    match=actual.get(tuple(row[k] for k in keys))
                    assert match is not None, 'Missing row in '+table
                    for col,value in row.items():
                        other=match[col]
                        if col in jsoncols and value is not None:
                            ok=json.loads(value)==json.loads(other)
                        elif isinstance(value,int) and isinstance(other,bytes):
                            ok=value==int.from_bytes(other,'big')
                        elif isinstance(value,bytes) and isinstance(other,int):
                            ok=int.from_bytes(value,'big')==other
                        elif isinstance(other,(float,decimal.Decimal)) and value is not None:
                            ok=math.isclose(float(value),float(other),rel_tol=1e-7,abs_tol=1e-9)
                        else:ok=value==other or (value is not None and other is not None and str(value)==str(other))
                        assert ok, 'Field mismatch '+table+'.'+col
                result[table]=len(expected)
        db.close();return result
    def protected():
        before=json.loads((p/'protected-before.json').read_text())
        for table,value in before.items():
            # Existing rows must still match byte-for-byte; new rows can be appended.
            now=set(mysql('SELECT * FROM `'+table+'` ORDER BY 1').splitlines())
            assert set(value.splitlines())<=now,'Production records changed: '+table
    if mode in ('rehearse','check-rehearsal'):
        name='knowledge_candidate_20260909'
        if mode=='rehearse':
            mysql('CREATE DATABASE '+name+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
            with gzip.open(p/'server-mysql-before.sql.gz','rb') as f:run(['mysql',name],f.read())
            with gzip.open(source/'merge.sql.gz','rb') as f:run(['mysql',name],f.read())
        result=verify(name)
        (p/'rehearsal.json').write_text(json.dumps(result,indent=2))
        print('REHEARSAL_VERIFIED '+json.dumps(result))
    elif mode=='apply':
        assert (p/'rehearsal.json').is_file()
        assert not (p/'applied.json').exists()
        protected()
        counts=json.loads((source/'merge-counts.json').read_text())
        for table,count in counts.items():
            if table.startswith('ai_') and table not in ('ai_knowledge_base','ai_chat_conversation','ai_chat_message','ai_chat_citation'):
                assert mysql('SELECT COUNT(*) FROM `'+table+'`')=='0','Destination not empty: '+table
        assert pg('SELECT count(*) FROM ai_vector_store')=='0'
        current=Path('/opt/data-center/apps/knowledge/current')
        release=current.parent/'releases'/'20260909_history_v1'
        assert not release.exists()
        shutil.copytree(current.resolve(),release,symlinks=True)
        shutil.copyfile(source/'app.jar',release/'app.jar')
        os.chmod(release/'app.jar',0o644)
        (p/'previous-release.txt').write_text(str(current.resolve()))
        run(['systemctl','stop','leman-knowledge'])
        try:
            manifest=json.loads((source/'manifest.json').read_text())
            for item in manifest['files']:
                if 'sha256' not in item:continue
                target=Path('/data/ai/knowledge/documents')/item['key']
                assert not target.exists()
                target.parent.mkdir(parents=True,exist_ok=True)
                shutil.copyfile(source/'files'/item['key'],target)
            run(['chown','-R','leman-knowledge:leman-knowledge','/data/ai/knowledge/documents'])
            # PostgreSQL target was verified empty. Retain vector IDs and all document/chunk references.
            columns='id,vector_id,tenant_id,knowledge_base_id,document_id,chunk_id,content,metadata,embedding,create_time,update_time'
            copy=run(['docker','exec','data-center-ai-postgres','sh','-c','exec psql -U "$POSTGRES_USER" -d knowledge_history_20260909 -q -c "COPY public.ai_vector_store ('+columns+') TO STDOUT"'])
            run(['docker','exec','-i','data-center-ai-postgres','sh','-c','exec psql -U "$POSTGRES_USER" -d yudao_ai -v ON_ERROR_STOP=1 -c "COPY public.ai_vector_store ('+columns+') FROM STDIN"'],copy)
            pg("SELECT setval(pg_get_serial_sequence('ai_vector_store','id'),(SELECT max(id) FROM ai_vector_store));")
            with gzip.open(source/'merge.sql.gz','rb') as f:run(['mysql','yudao_ai'],f.read())
            result=verify('yudao_ai');protected()
            assert mysql('SELECT COUNT(*) FROM ai_chat_message m LEFT JOIN ai_chat_conversation c ON c.id=m.conversation_id WHERE m.id>=1000000 AND c.id IS NULL')=='0'
            assert mysql('SELECT COUNT(*) FROM ai_chat_citation c LEFT JOIN ai_chat_message m ON m.id=c.message_id WHERE c.id>=1000000 AND m.id IS NULL')=='0'
            result['conversation_references_verified']=True
            result['vectors']=int(pg('SELECT count(*) FROM ai_vector_store'))
            assert result['vectors']==int(pg('SELECT count(*) FROM ai_vector_store','knowledge_history_20260909'))
            vector_digest="SELECT md5(string_agg(md5(row_to_json(t)::text), '' ORDER BY id)) FROM ai_vector_store t"
            assert pg(vector_digest)==pg(vector_digest,'knowledge_history_20260909'),'Vector content differs'
            result['vector_content_checksum_verified']=True
            result['files']=sum('sha256' in x for x in manifest['files'])
            for item in manifest['files']:
                if 'sha256' in item:assert hashlib.sha256((Path('/data/ai/knowledge/documents')/item['key']).read_bytes()).hexdigest()==item['sha256']
            (p/'applied.json').write_text(json.dumps(result,indent=2))
            next_link=current.with_name('current-history-next')
            next_link.symlink_to(release,target_is_directory=True)
            os.replace(next_link,current)
            print('APPLIED_VERIFIED '+json.dumps(result),flush=True)
        finally:run(['systemctl','start','leman-knowledge'])
    else:
        print(json.dumps(verify('yudao_ai')));protected();print('PRODUCTION_PRESERVED')
