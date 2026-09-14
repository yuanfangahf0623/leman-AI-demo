#!/usr/bin/env python3
"""Authenticated deployment smoke test. Credentials and answers stay on the server."""
import json
import os
from pathlib import Path
import urllib.request
import urllib.error
from datetime import datetime

os.umask(0o077)
admin = json.loads(Path('/opt/data-center/secrets/knowledge-admin.json').read_text())
base = 'http://127.0.0.1:18081'
token = None
results = {'checked_at':datetime.now().isoformat()}
private = Path('/data/backup/knowledge-deploy/verification')
private.mkdir(exist_ok=True,parents=True)

def request(path, payload=None, authenticate=True):
    headers = {'Content-Type':'application/json','tenant-id':'1'}
    if token and authenticate:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base+'/admin-api'+path,
            data=json.dumps(payload,ensure_ascii=False).encode() if payload is not None else None,headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=230) as response:
            value = json.load(response)
    except urllib.error.HTTPError as error:
        (private/'last-http-error.txt').write_bytes(error.read())
        raise RuntimeError('HTTP ' + str(error.code) + ' at ' + path)
    if value.get('code') != 0:
        (private/'last-business-error.json').write_text(json.dumps(value,ensure_ascii=False))
        raise RuntimeError('Business error ' + str(value.get('code')) + ' at ' + path)
    return value['data']

try:
    try:
        request('/system/auth/get-permission-info',authenticate=False)
        raise AssertionError('Unauthenticated request was accepted')
    except RuntimeError as exc:
        assert str(exc).startswith('HTTP 401'), str(exc)
    results['unauthenticated_rejected']=True
    login = request('/system/auth/login',{'username':admin['username'],'password':admin['password']})
    token = login['accessToken']
    results['login']=True
    permissions = request('/system/auth/get-permission-info')
    results['menus_loaded']=bool(permissions['menus'])
    kb = request('/ai/knowledge/get?id='+str(admin['knowledge_base_id']))
    results['knowledge_base_bound']=kb['code']=='company-dify'
    chat = request('/ai/chat/completions',{'knowledgeBaseId':admin['knowledge_base_id'],
                   'question':'公司的开票信息在哪份文档里？请说明依据。','stream':False})
    (private/'first-chat.json').write_text(json.dumps(chat,ensure_ascii=False,indent=2))
    assert chat.get('answer') and chat.get('citations'), 'Answer or citations missing'
    results['rag_answer']=True
    results['citation_count']=len(chat['citations'])
    results['conversation_id']=chat['conversationId']
    followup = request('/ai/chat/completions',{'knowledgeBaseId':admin['knowledge_base_id'],
                   'conversationId':chat['conversationId'],'question':'刚才那份文档的名称是什么？','stream':False})
    (private/'followup-chat.json').write_text(json.dumps(followup,ensure_ascii=False,indent=2))
    assert '开票' in followup['answer'], 'Follow-up did not recall document'
    results['followup_memory']=True
    messages = request('/ai/chat/message/list?conversationId='+str(chat['conversationId']))
    assert len(messages)==4, 'Expected two stored question/answer pairs'
    results['stored_messages']=len(messages)
    results['successful']=True
finally:
    (private/'verification.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
    print(json.dumps(results,ensure_ascii=False),flush=True)
