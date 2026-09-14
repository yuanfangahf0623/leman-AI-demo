"""Generate a transaction for knowledge history only, preserving production configuration."""
import gzip,json,sys
from pathlib import Path
p=Path(sys.argv[1]); manifest=json.loads((p/'manifest.json').read_text(encoding='utf-8'))
def decode(x):
    if '$bytes' in x:return bytes.fromhex(x['$bytes'])
    if '$datetime' in x:return x['$datetime']
    if '$decimal' in x:return x['$decimal']
    return x
def read(t):return json.load(gzip.open(p/(t+'.json.gz'),'rt',encoding='utf-8'),object_hook=decode)
def literal(v):
    if v is None:return 'NULL'
    if isinstance(v,bytes):return "X'"+v.hex()+"'"
    if isinstance(v,(int,float)):return str(v)
    return "CONVERT(X'"+str(v).encode().hex()+"' USING utf8mb4)"
def user(v):return v if v in (None,0,1) else v+1000000
tables=list(manifest['tables'])
counts={}
(p/'expected').mkdir(exist_ok=True)
def encode(v):
    if isinstance(v,bytes):return {'$bytes':v.hex()}
    raise TypeError(type(v).__name__)
with gzip.open(p/'merge.sql.gz','wt',encoding='utf-8') as f:
    f.write('SET NAMES utf8mb4; START TRANSACTION;\n')
    for t in tables:
        rows=read(t)
        if t in ('system_role','system_post'):continue
        if t=='system_users': rows=[r for r in rows if r['id']!=1]
        if t=='system_dept': rows=[r for r in rows if r['id']!=100]
        # Existing admin keeps production roles/posts, credentials and profile.
        if t in ('system_user_role','system_user_post'):rows=[r for r in rows if r['user_id']!=1]
        for r in rows:
            if t=='system_users':r['id']=user(r['id'])
            if t.startswith('ai_chat_'):r['id']+=1000000
            for col in ('user_id','leader_user_id'):
                if col in r:r[col]=user(r[col])
            for col in ('creator','updater'):
                if str(r.get(col,'')).isdigit():r[col]=str(user(int(r[col])))
            if t=='ai_chat_message':r['conversation_id']+=1000000
            if t=='ai_chat_citation':r['message_id']+=1000000
            if t=='ai_data_source':r['sync_enabled']=0
            if t=='ai_document' and str(r.get('source_uri','')).startswith('file:'):
                r['source_uri']='file:///data/ai/knowledge/documents/'+r['object_key']
            cols=','.join('`'+k+'`' for k in r)
            f.write('INSERT INTO `'+t+'` ('+cols+') VALUES ('+','.join(literal(v) for v in r.values())+');\n')
        counts[t]=len(rows)
        with gzip.open(p/'expected'/(t+'.json.gz'),'wt',encoding='utf-8') as target:
            json.dump(rows,target,ensure_ascii=False,default=encode)
    f.write('COMMIT;\n')
(p/'merge-counts.json').write_text(json.dumps(counts,indent=2),encoding='utf-8')
print(json.dumps(counts))
