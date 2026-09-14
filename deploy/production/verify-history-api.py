"""Verify migrated metadata, readable source objects and the existing Dify entry point."""
import gzip,hashlib,json,os,urllib.request,urllib.error
from pathlib import Path
os.umask(0o077)
p=Path('/data/backup/knowledge-history/20260909')
admin=json.loads(Path('/opt/data-center/secrets/knowledge-admin.json').read_text())
token=None
def request(path,payload=None,raw=False):
    headers={'Content-Type':'application/json','tenant-id':'1'}
    if token:headers['Authorization']='Bearer '+token
    req=urllib.request.Request('http://127.0.0.1:18081/admin-api'+path,
        data=None if payload is None else json.dumps(payload).encode(),headers=headers)
    with urllib.request.urlopen(req,timeout=230) as response: data=response.read()
    if raw:return data
    value=json.loads(data)
    assert value['code']==0,'Business error '+str(value['code'])+' at '+path
    return value['data']
token=request('/system/auth/login',{'username':admin['username'],'password':admin['password']})['accessToken']
results={'login':True,'knowledge_bases':[],'preview_files':0}
for kb in (1,10,12,13):
    result=request('/ai/knowledge/get?id='+str(kb));assert result['id']==kb
    results['knowledge_bases'].append(kb)
manifest=json.loads((p/'source/manifest.json').read_text())
docs=json.load(gzip.open(p/'source/ai_document.json.gz','rt',encoding='utf-8'))
for item in manifest['files']:
    if not item['active'] or 'sha256' not in item:continue
    # A document under a soft-deleted knowledge base is intentionally inaccessible.
    doc=next(x for x in docs if x['id']==item['document_id'])
    if doc['knowledge_base_id'] not in (10,12,13):continue
    content=request('/ai/document/preview?id='+str(item['document_id']),raw=True)
    assert hashlib.sha256(content).hexdigest()==item['sha256'],'Preview checksum mismatch'
    results['preview_files']+=1
chat=request('/ai/chat/completions',{'knowledgeBaseId':0,'question':'公司的开票信息在哪份文档里？请说明依据。','stream':False})
assert chat.get('answer') and chat.get('citations')
results['dify_default_entry']=True
results['verification_conversation_id']=chat['conversationId']
results['citation_count']=len(chat['citations'])
# Existing administrator policy permits reading same-tenant conversations.
conversations=json.load(gzip.open(p/'source/ai_chat_conversation.json.gz','rt',encoding='utf-8'))
messages=json.load(gzip.open(p/'source/ai_chat_message.json.gz','rt',encoding='utf-8'))
results['historical_conversations_read']=0
results['historical_messages_read']=0
for historical in conversations:
    if historical['deleted']!={'$bytes':'00'}:continue
    actual=request('/ai/chat/message/list?conversationId='+str(historical['id']+1000000))
    expected=[x for x in messages if x['conversation_id']==historical['id'] and x['deleted']=={'$bytes':'00'}]
    assert len(actual)==len(expected)
    byid={x['id']:x for x in actual}
    for item in expected:
        row=byid[item['id']+1000000]
        assert row['content']==item['content'] and row['role']==item['role']
    results['historical_conversations_read']+=1
    results['historical_messages_read']+=len(actual)
results['access_policy']='existing_same_tenant_admin_scope'
(p/'api-verification.json').write_text(json.dumps(results,indent=2))
print(json.dumps(results))
