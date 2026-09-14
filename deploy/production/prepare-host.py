#!/usr/bin/env python3
"""One-time server preparation. Run as root with the unpacked release path; never prints credentials."""
import datetime
import gzip
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

os.umask(0o077)
release = Path(sys.argv[1]).resolve()
assert str(release).startswith('/opt/data-center/apps/knowledge/releases/')
env_path = Path('/opt/data-center/secrets/knowledge.env')
assert not env_path.exists(), 'Already prepared; inspect existing deployment before running again'

def run(args, text=None):
    result = subprocess.run(args, input=text, text=True, capture_output=True, timeout=180)
    if result.returncode:
        raise RuntimeError('Command failed (details suppressed): ' + args[0])
    return result.stdout

def sql(statement):
    return run(['mysql', '--default-character-set=utf8mb4', '-NB', 'yudao_ai'], statement)

backup = Path('/data/backup/knowledge-deploy') / datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
backup.mkdir(parents=True)
dump = subprocess.run(['mysqldump', '--single-transaction', '--routines', '--events', '--triggers',
                       '--set-gtid-purged=OFF', 'yudao_ai'], capture_output=True, check=True)
with gzip.open(backup/'yudao_ai-before.sql.gz', 'wb') as stream:
    stream.write(dump.stdout)
run(['tar', '-czf', str(backup/'nginx-before.tar.gz'), '-C', '/', 'etc/nginx'])
print('PRE_DEPLOY_BACKUP=' + str(backup), flush=True)

resources = release/'sql'
schema_files = [
    'system-base-20260509.sql', 'ai-module.sql', 'ai-module-upgrade-20260522-fastgpt-citation-source.sql',
    'ai-module-upgrade-20260528-chatgpt-meeting-actions.sql', 'ai-module-upgrade-20260603-teams-meeting-sync.sql',
    'system-menu-pages-20260509.sql', 'ai-module-upgrade-20260604-finance-invoice.sql',
    'ai-module-upgrade-20260624-rfq-email-automation.sql', 'ai-module-upgrade-20260709-lead-agent.sql',
    'system-menu-runtime-repair-20260828.sql']
for name in schema_files:
    sql((resources/name).read_text(encoding='utf-8-sig'))
sql((release/'deploy/production/dify-conversation.sql').read_text())

assert sql('SELECT COUNT(*) FROM ai_knowledge_base;').strip() == '0', 'Existing knowledge bases need explicit mapping'
sql("""INSERT INTO ai_knowledge_base(tenant_id,name,code,description,visibility,department_ids,vector_store_type,embedding_model,chat_model)
       VALUES(1,'公司知识库（Dify）','company-dify','接入现有 Dify 知识库查询。资料在 Dify 中维护，本机历史资料后续迁移。','public','*','pgvector','text-embedding-3-small','dify');""")
kb = int(sql("SELECT id FROM ai_knowledge_base WHERE tenant_id=1 AND code='company-dify';").strip())
sql("UPDATE infra_config SET value='dify',remark='dify=Dify 应用；local=本地检索；fastgpt=旧引擎' WHERE `key`='ai.rag.engine';")
sql("UPDATE system_menu SET name='Dify',path='http://192.168.19.239:18080',updater='deployment' WHERE name='FastGPT' AND deleted=0;")
sql("UPDATE infra_config SET value='local' WHERE `key`='AI_DOCUMENT_STORAGE_TYPE';")
sql("UPDATE infra_config SET value='false' WHERE `key` IN ('ai.invoice.recognition.fallback-to-mock','ai.invoice.mock-approval-enabled');")

mysql_password = secrets.token_urlsafe(36)
run(['mysql'], "CREATE USER 'leman_knowledge'@'localhost' IDENTIFIED BY '" + mysql_password + "';\n"
    "GRANT SELECT,INSERT,UPDATE,DELETE ON yudao_ai.* TO 'leman_knowledge'@'localhost';")
source = dict(line.split('=',1) for line in Path('/opt/data-center/secrets/dify.env').read_text().splitlines()
              if line and not line.startswith('#') and '=' in line)
state = json.loads(Path('/data/backup/dify-deploy/20260907/migration-state.json').read_text())
assert state['app_id'] == '3ba7161c-9388-4c61-8c65-59c38969fca2'
admin_password = secrets.token_urlsafe(30) + '!'
values = {
    'SERVER_PORT':'48081', 'SERVER_ADDRESS':'127.0.0.1', 'SPRING_SQL_INIT_MODE':'never',
    'SPRING_DATASOURCE_URL':'jdbc:mysql://127.0.0.1:3307/yudao_ai?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
    'SPRING_DATASOURCE_USERNAME':'leman_knowledge', 'SPRING_DATASOURCE_PASSWORD':mysql_password,
    'AI_RAG_ENGINE':'dify', 'AI_DIFY_BASE_URL':'http://127.0.0.1:18080/v1',
    'AI_DIFY_API_KEY':state['app_token'], 'AI_DIFY_DATASET_ID':state['dataset_id'],
    'AI_DIFY_TENANT_ID':'1', 'AI_DIFY_KNOWLEDGE_BASE_ID':str(kb),
    'AI_MODEL_PROVIDER':'openai-compatible', 'AI_BASE_URL':'https://api.openai.com/v1',
    'AI_CHAT_MODEL':'gpt-5.5', 'AI_EMBEDDING_MODEL':'text-embedding-3-small',
    'AI_VECTOR_STORE_TYPE':'pgvector', 'AI_PGVECTOR_JDBC_URL':'jdbc:postgresql://127.0.0.1:5433/yudao_ai',
    'AI_PGVECTOR_USERNAME':'yudao_ai', 'AI_PGVECTOR_PASSWORD':source['AI_PGVECTOR_PASSWORD'],
    'AI_PGVECTOR_DIMENSIONS':'1536', 'AI_DOCUMENT_STORAGE_TYPE':'local',
    'AI_DOCUMENT_STORAGE_BASE_PATH':'/data/ai/knowledge/documents',
    'AI_DOCUMENT_OCR_ENABLED':'false', 'AI_INVOICE_MOCK_APPROVAL_ENABLED':'false',
    'AI_INVOICE_RECOGNITION_FALLBACK_TO_MOCK':'false', 'CHATGPT_ACTIONS_ENABLED':'false',
    'TEAMS_MEETING_SYNC_ENABLED':'false', 'RFQ_EMAIL_SYNC_ENABLED':'false', 'RFQ_IMAP_ENABLED':'false',
    'LEMAN_BOOTSTRAP_ADMIN_ENABLED':'true', 'LEMAN_ADMIN_USERNAME':'knowledge-admin',
    'LEMAN_ADMIN_PASSWORD':admin_password, 'LEMAN_ADMIN_TENANT_ID':'1', 'LEMAN_ADMIN_DEPT_ID':'100',
    'LOGGING_FILE_NAME':'/var/log/data-center/knowledge/backend.log',
}
for key, value in values.items():
    assert not any(c in value for c in '\r\n"\\'), 'Invalid environment field: ' + key
with env_path.open('x') as stream:
    stream.write(''.join(key + '="' + value + '"\n' for key,value in values.items()))
access = {'url':'http://192.168.19.239:18081','username':'knowledge-admin','password':admin_password,
          'knowledge_base_id':kb, 'backup':str(backup)}
Path('/opt/data-center/secrets/knowledge-admin.json').write_text(json.dumps(access,ensure_ascii=False,indent=2))
if subprocess.run(['id','leman-knowledge'], capture_output=True).returncode:
    run(['useradd','--system','--home','/nonexistent','--shell','/usr/sbin/nologin','leman-knowledge'])
for path in ['/data/ai/knowledge/documents','/var/log/data-center/knowledge']:
    Path(path).mkdir(parents=True,exist_ok=True)
    run(['chown','-R','leman-knowledge:leman-knowledge',path])
    Path(path).chmod(0o700)
run(['chown','leman-knowledge:leman-knowledge','/data/ai/knowledge'])
# Allow service users to traverse the shared apps parent without listing it.
apps = Path('/opt/data-center/apps')
apps.chmod(apps.stat().st_mode | 0o001)
for path in release.rglob('*'):
    path.chmod(0o755 if path.is_dir() else 0o644)
release.chmod(0o755)
print('DATABASE_AND_PROTECTED_CONFIGURATION_READY; knowledge_base_id=' + str(kb))
