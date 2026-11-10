#!/bin/sh
# 受控本地切换；不 source .env，不把 Compose 凭据输出到终端。
set -eu
exec python3 - "$0" "$@" <<'PYTHON'
import argparse, json, os, re, shutil, subprocess, sys, tempfile, uuid
from pathlib import Path
from urllib.parse import urlsplit
os.umask(0o077)
parser=argparse.ArgumentParser(description='检查或切换本地独立 Admin Client（只影响 Admin/IAM/System）')
parser.add_argument('--apply',action='store_true')
parser.add_argument('--gateway-client-id',required=True)
parser.add_argument('--tenant-id',required=True,type=int)
parser.add_argument('--user-type',required=True,choices=['employee','member','odc','supplier'])
parser.add_argument('--origin',required=True)
parser.add_argument('--authority',required=True)
args=parser.parse_args(sys.argv[2:])
root=Path(sys.argv[1]).resolve().parents[1]
services=['macula-cloud-system','macula-cloud-iam','macula-cloud-admin']
compose=['docker','compose','--project-directory',str(root),'--env-file',str(root/'.env'),'-f',str(root/'docker-compose.yml'),'--profile','apps']
stage='preflight'
backup=None

def run(command, input=None):
    result=subprocess.run(command,input=input,text=True,capture_output=True)
    if result.returncode:
        if backup: (backup/(stage+'.stderr')).write_text(result.stderr)
        raise RuntimeError('command failed')
    return result.stdout

def local_url(value):
    url=urlsplit(value)
    return url.scheme=='http' and url.hostname in ['localhost','127.0.0.1','::1'] and not url.username and not url.password and url.path in ['', '/'] and not url.query and not url.fragment

def mysql(sql):
    # 在容器内部读取密码；进程参数和终端均不含凭据。
    return run(compose+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B --database=macula-system'],sql)

try:
    if not local_url(args.origin) or not local_url(args.authority) or args.origin.endswith('/') or args.authority.endswith('/'):
        raise RuntimeError('exact loopback origins required')
    uuid.UUID(args.gateway_client_id)
    context=json.loads(run(['docker','context','inspect']))[0]
    if not context['Endpoints']['docker']['Host'].startswith('unix://') or os.environ.get('DOCKER_HOST','unix://').startswith(('tcp:', 'ssh:')):
        raise RuntimeError('local Docker context required')
    public_keys=['VITE_APP_OAUTH_CLIENT_ID','VITE_APP_OAUTH_AUTHORITY','VITE_APP_ADMIN_PUBLIC_URL','VITE_APP_OAUTH_SCOPE',
        'VITE_APP_OAUTH_CLIENT_SECRET','VITE_APP_DEMO_USERNAME','VITE_APP_DEMO_PASSWORD']
    if any(key in os.environ for key in public_keys): raise RuntimeError('public Admin configuration must be managed in .env')
    config=json.loads(run(compose+['config','--format','json']))
    if config.get('name')!='macula-cloud-dev': raise RuntimeError('unexpected compose project')
    for service in services: run(['docker','image','inspect',config['services'][service]['image'],'--format','{{.Id}}'])
    rows=mysql("SELECT tenant_id,user_type,application_type,client_secret IS NOT NULL AND client_secret<>'' FROM sys_oauth2_client WHERE client_id='"+args.gateway_client_id+"';")
    row=rows.strip().split('\t')
    if len(row)!=4 or row[0]!=str(args.tenant_id) or row[1]!=args.user_type or row[2] not in ['NULL','SERVICE'] or row[3]!='1':
        raise RuntimeError('gateway scope mismatch')
    env_path=root/'.env'
    lines=env_path.read_text().splitlines(keepends=True)
    current={}
    for line in lines:
        match=re.fullmatch(r'([A-Za-z_][A-Za-z_0-9]*)=(.*?)(?:\n)?',line)
        if match: current[match[1]]=match[2].strip().strip('"').strip("'")
    admin_id=current.get('VITE_APP_OAUTH_CLIENT_ID')
    if not admin_id or admin_id==args.gateway_client_id: admin_id=str(uuid.uuid4())
    uuid.UUID(admin_id)
    if not args.apply:
        print('检查通过：本地项目、目标镜像及 Gateway 身份范围一致。尚未修改配置、数据库或容器。')
        sys.exit(0)
    backup=Path(tempfile.mkdtemp(prefix='macula-admin-cutover-',dir='/private/tmp'))
    shutil.copyfile(env_path,backup/'deploy.env')
    os.chmod(backup/'deploy.env',0o600)
    snapshots={}
    for service in services:
        container=run(compose+['ps','-q',service]).strip()
        snapshots[service]=json.loads(run(['docker','inspect',container]))[0]['Image'] if container else None
    (backup/'old-images.json').write_text(json.dumps(snapshots,indent=2))
    (backup/'flyway.txt').write_text(mysql('SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank;'))
    # 备份精确两 Client 及其关联；权限 0600，禁止自动执行反向恢复。
    where="client_id IN ('"+args.gateway_client_id+"','"+admin_id+"')"
    for table in ['sys_oauth2_client','sys_oauth2_client_external_provider']:
        dump=run(compose+['exec','-T','mysql','sh','-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --no-create-info --skip-lock-tables --complete-insert --skip-add-locks --where="$1" macula-system "$2"','sh',where,table])
        (backup/(table+'.sql')).write_text(dump)
    changes={'VITE_APP_OAUTH_CLIENT_ID':admin_id,'VITE_APP_OAUTH_AUTHORITY':args.authority,
        'VITE_APP_ADMIN_PUBLIC_URL':args.origin,'VITE_APP_OAUTH_SCOPE':'openid profile'}
    obsolete={'VITE_APP_OAUTH_CLIENT_SECRET','VITE_APP_DEMO_USERNAME','VITE_APP_DEMO_PASSWORD'}
    output=[]
    for line in lines:
        key=line.split('=',1)[0]
        if key in obsolete: continue
        if key in changes: output.append(key+'='+changes.pop(key)+'\n')
        else: output.append(line)
    if output and not output[-1].endswith('\n'): output[-1]+='\n'
    output.extend(key+'='+value+'\n' for key,value in changes.items())
    temporary=env_path.with_suffix('.env.admin-tmp')
    temporary.write_text(''.join(output));os.chmod(temporary,0o600);os.replace(temporary,env_path)
    stage='stop-old-services'
    run(compose+['stop']+services)
    stage='provision-clients'
    # 参数全部为公开身份；默认服务不会带此 enabled 开关。
    environment={
        'MACULA_IAM_ADMIN_CLIENT_PROVISIONING_ENABLED':'true',
        'MACULA_IAM_ADMIN_CLIENT_PROVISIONING_ADMIN_CLIENT_ID':admin_id,
        'MACULA_IAM_ADMIN_CLIENT_PROVISIONING_GATEWAY_CLIENT_ID':args.gateway_client_id,
        'MACULA_IAM_ADMIN_CLIENT_PROVISIONING_ADMIN_ORIGIN':args.origin,
        'MACULA_IAM_ADMIN_CLIENT_PROVISIONING_TENANT_ID':str(args.tenant_id),
        'MACULA_IAM_ADMIN_CLIENT_PROVISIONING_USER_TYPE':args.user_type,
        'SPRING_CLOUD_NACOS_DISCOVERY_ENABLED':'false',
        'SPRING_CLOUD_NACOS_CONFIG_ENABLED':'false',
        'SERVER_PORT':'0',
        'MYBATIS_PLUS_CONFIGURATION_LOG_IMPL':'org.apache.ibatis.logging.nologging.NoLoggingImpl',
        'LOGGING_LEVEL_ROOT':'ERROR',
        'LOGGING_LEVEL_DEV_MACULA':'ERROR',
        'LOGGING_LEVEL_ORG_MYBATIS':'OFF',
        'LOGGING_LEVEL_COM_BAOMIDOU':'OFF'}
    command=compose+['run','--rm','--no-deps']
    for key,value in environment.items(): command+=['-e',key+'='+value]
    run(command+['macula-cloud-system'])
    stage='start-new-services'
    run(compose+['up','-d','--no-deps','--no-build','--force-recreate','--wait']+services)
    print('已切换 System/IAM/Admin；Gateway 未变。恢复依据：'+str(backup))
except Exception:
    print('初始化失败，阶段：'+stage+'。未自动回退或清空数据库。'+(' 私密恢复依据：'+str(backup) if backup else ''),file=sys.stderr)
    sys.exit(1)
PYTHON
