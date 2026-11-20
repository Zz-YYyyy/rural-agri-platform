"""Bundle the reviewed OpenAPI fragments and check local contract consistency.

Run with --write after editing source fragments; default checks generated files.
Uses jsonschema for DTO examples. Does not start services or perform HTTP tests.
"""
from __future__ import annotations

import argparse
from copy import deepcopy
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / 'docs/api'
FRAGMENTS = DIRECTORY / '分模块契约'
OUTPUT = DIRECTORY / '接口契约.openapi.json'
FIELDS = DIRECTORY / '接口字段说明.md'
METHODS = {'get','post','put','patch','delete'}

def require(condition, message):
    if not condition:
        raise ValueError(message)

def read_json(path):
    raw=path.read_bytes()
    require(not raw.startswith(b'\xef\xbb\xbf') and b'\r' not in raw and raw.endswith(b'\n'),f'Encoding/newline: {path}')
    def pairs(items):
        result={}
        for key,value in items:
            require(key not in result,f'Duplicate JSON key {key}: {path}')
            result[key]=value
        return result
    return json.loads(raw.decode('utf-8'),object_pairs_hook=pairs)

def bundle():
    result=read_json(FRAGMENTS/'公共定义.json')
    for path in sorted(FRAGMENTS.glob('*.json')):
        if path.name=='公共定义.json': continue
        fragment=read_json(path)
        for endpoint,methods in fragment['paths'].items():
            target=result['paths'].setdefault(endpoint,{})
            for method,operation in methods.items():
                require(method not in target,f'Duplicate operation {method} {endpoint}')
                target[method]=operation
        for name,schema in fragment['components']['schemas'].items():
            require(name not in result['components']['schemas'],f'Duplicate schema {name}')
            result['components']['schemas'][name]=schema
    result['paths']=dict(sorted(result['paths'].items()))
    result['components']['schemas']=dict(sorted(result['components']['schemas'].items()))
    result['tags']=[{'name':name} for name in sorted({tag for methods in result['paths'].values() for op in methods.values() for tag in op.get('tags',[])})]
    return result

def deref(document, value):
    if '$ref' not in value: return value
    reference=value['$ref']
    require(reference.startswith('#/'),f'External reference unsupported: {reference}')
    target=document
    for segment in reference[2:].split('/'):
        target=target[segment.replace('~1','/').replace('~0','~')]
    return target

def visit(value, function, location='$'):
    if isinstance(value,dict):
        function(value,location)
        for key,child in value.items(): visit(child,function,location+'.'+key)
    elif isinstance(value,list):
        for index,child in enumerate(value): visit(child,function,f'{location}[{index}]')

def for_jsonschema(value):
    if isinstance(value,list): return [for_jsonschema(item) for item in value]
    if not isinstance(value,dict): return value
    result={key:for_jsonschema(item) for key,item in value.items() if key not in ('nullable','example','discriminator','xml')}
    if value.get('nullable') and 'type' in result:
        result['type']=[result['type'],'null']
    return result

def validate(document):
    from jsonschema import Draft4Validator, FormatChecker
    require(document['openapi']=='3.0.3','Expected OpenAPI 3.0.3')
    reqs=set(re.findall(r'^### (FR-[A-Z]+-\d+)\b',(ROOT/'docs/requirements/需求文档.md').read_text(encoding='utf-8'),re.M))
    cases=set(re.findall(r'^\| (AC-[A-Z]+-\d+) \|',(ROOT/'docs/testing/验收场景清单.md').read_text(encoding='utf-8'),re.M))
    tables=set(re.findall(r'CREATE TABLE `(\w+)`',(ROOT/'backend/src/main/resources/db/migration/V1__initial_schema.sql').read_text(encoding='utf-8')))
    operation_ids=set()
    routes={}
    def check_ref(value,location):
        if '$ref' in value:
            require(len(value)==1,f'$ref siblings ignored by OpenAPI 3.0: {location}')
            try: deref(document,value)
            except (KeyError,TypeError): raise ValueError(f'Unresolved ref at {location}: {value["$ref"]}')
        if value.get('type')=='object' and 'properties' in value:
            require(set(value.get('required',[])) <= value['properties'].keys(),f'Unknown required property: {location}')
            require(value.get('additionalProperties') is False,f'Explicit object field whitelist missing: {location}')
    visit(document,check_ref)
    for endpoint,methods in document['paths'].items():
        require(endpoint.startswith('/api/v1/'),f'Path prefix: {endpoint}')
        route_shape=re.sub(r'\{[^}]+\}','{}',endpoint)
        require(route_shape not in routes or routes[route_shape]==endpoint,f'Ambiguous path templates: {endpoint}')
        routes[route_shape]=endpoint
        for method,op in methods.items():
            require(method in METHODS,f'Unsupported operation method: {endpoint} {method}')
            op_id=op['operationId']
            require(op_id not in operation_ids,f'Duplicate operationId: {op_id}')
            operation_ids.add(op_id)
            require(op.get('description') and op.get('summary'),f'Missing explanation: {op_id}')
            require(op['x-permission'] in ('PUBLIC','MEMBER','FARMER','ADMIN'),f'Permission: {op_id}')
            allowed_security = ([], [{}, {'bearerAuth':[]}]) if op['x-permission']=='PUBLIC' else ([{'bearerAuth':[]}],)
            require(op.get('security') in allowed_security,f'Auth mismatch: {op_id}')
            require(op.get('x-requirements') and set(op['x-requirements'])<=reqs,f'FR mapping: {op_id}: {set(op.get("x-requirements",[]))-reqs}')
            require(op.get('x-acceptance') and set(op['x-acceptance'])<=cases,f'AC mapping: {op_id}: {set(op.get("x-acceptance",[]))-cases}')
            require(set(op.get('x-tables',[]))<=tables,f'Table mapping: {op_id}')
            params=[deref(document,p) for p in op.get('parameters',[])]
            require(len({(p['in'],p['name']) for p in params})==len(params),f'Duplicate parameter: {op_id}')
            path_params={p['name'] for p in params if p['in']=='path' and p.get('required') is True}
            require(path_params==set(re.findall(r'\{([^}]+)\}',endpoint)),f'Path parameter mismatch: {op_id}')
            require(any(status.startswith('2') for status in op['responses']),f'Missing success: {op_id}')
            for err in op.get('x-errors',[]):
                require(str(err['status']) in op['responses'],f'Undeclared error status: {op_id}')
            if method=='get': require('requestBody' not in op,f'GET body: {op_id}')
    converted=for_jsonschema(document)
    checked=0
    def validate_examples(value,location):
        nonlocal checked
        schema=None
        if 'schema' in value and 'example' in value:
            schema=value['schema']
        elif 'example' in value and any(key in value for key in ('type','oneOf','allOf','$ref')):
            schema=value
        if schema is None: return
        wrapper={'components':converted['components'],'allOf':[for_jsonschema(schema)]}
        Draft4Validator.check_schema(wrapper)
        failures=list(Draft4Validator(wrapper,format_checker=FormatChecker()).iter_errors(value['example']))
        require(not failures,f'Example invalid at {location}: {failures[0].message if failures else ""}')
        checked+=1
    visit(document,validate_examples)
    return len(operation_ids),checked

def label(schema):
    if '$ref' in schema: return schema['$ref'].rsplit('/',1)[-1]
    if schema.get('type')=='array': return 'array<'+label(schema['items'])+'>'
    if 'oneOf' in schema: return 'oneOf('+', '.join(label(s) for s in schema['oneOf'])+')'
    return schema.get('type','组合结构')

def constraints(schema):
    output=[]
    for key,chinese in [('minLength','最短'),('maxLength','最长'),('minimum','最小'),('maximum','最大'),('minItems','最少项'),('maxItems','最多项'),('format','格式'),('pattern','匹配'),('default','默认'),('nullable','允许null')]:
        if key in schema: output.append(chinese+'='+str(schema[key]))
    if 'enum' in schema: output.append('枚举='+json.dumps(schema['enum'],ensure_ascii=False))
    return '；'.join(output) or '—'

def safe(value):
    return str(value).replace('|','\\|').replace('\n',' ')

def render_fields(document):
    lines=['# 接口字段说明','','本文件从分模块 OpenAPI 源定义生成，类型、必填、枚举与校验随接口契约同步。修改请先编辑源 JSON，再运行 `python scripts/build_api_contract.py --write`。这里描述接口 DTO，与数据库整行不同；可选字段未发生时省略，只有明确 nullable 的字段允许 JSON null。','','入口：[接口文档](接口文档.md)；机器契约：[接口契约.openapi.json](接口契约.openapi.json)。','','## 全部接口索引','','| 方法与路径 | 功能 | 权限 | operationId |','| --- | --- | --- | --- |']
    for path,methods in document['paths'].items():
        for method,op in methods.items(): lines.append(f'| `{method.upper()} {path}` | {safe(op["summary"])} | {op["x-permission"]} | `{op["operationId"]}` |')
    lines.extend(['','## 请求参数与响应定位','', '公共成功包络为 code/message/data/traceId；下列响应类型指 data。参数的完整语义和状态效果参见各模块说明与机器契约的 description。'])
    for path,methods in document['paths'].items():
        for method,op in methods.items():
            lines.extend(['',f'### {op["operationId"]}','',f'`{method.upper()} {path}`：{op["summary"]}。权限：{op["x-permission"]}。'])
            params=[deref(document,p) for p in op.get('parameters',[])]
            if params:
                lines.extend(['','| 参数 | 位置 | 类型 | 必填 | 校验与默认值 | 含义 |','| --- | --- | --- | --- | --- | --- |'])
                for p in params:
                    resolved=deref(document,p['schema'])
                    lines.append(f'| `{p["name"]}` | {p["in"]} | {safe(label(p["schema"]))} | {"是" if p.get("required") else "否"} | {safe(constraints(resolved))} | {safe(p.get("description",resolved.get("description","")))} |')
            for media,body in op.get('requestBody',{}).get('content',{}).items():
                schema=body['schema']
                lines.extend(['',f'请求体：`{media}`，`{label(schema)}`。'])
                if 'properties' in schema:
                    lines.extend(['','| 字段 | 类型 | 必填 | 校验与默认值 | 含义 |','| --- | --- | --- | --- | --- |'])
                    for field,value in schema['properties'].items():
                        resolved=deref(document,value)
                        lines.append(f'| `{field}` | {safe(label(value))} | {"是" if field in schema.get("required",[]) else "否"} | {safe(constraints(resolved))} | {safe(resolved.get("description","见类型定义"))} |')
            for code,response in op['responses'].items():
                if code.startswith('2'):
                    data=response['content']['application/json']['schema']['properties']['data']
                    name=label(data)
                    if 'properties' in data and 'items' in data['properties']:
                        name='分页 '+label(data['properties']['items'])+'；附total/page/pageSize'
                    lines.extend(['',f'成功 HTTP {code}，data：`{name}`。'])
            if op.get('x-errors'):
                lines.extend(['','主要业务错误：'+ '；'.join(f'{e["status"]} `{e["code"]}`：{e["message"]}' for e in op['x-errors'])+'。'])
            if op.get('x-pending-policy'):
                lines.extend(['','未决分支：'+safe(op['x-pending-policy'])+'。'])
    for name,schema in document['components']['schemas'].items():
        lines.extend(['',f'## {name}',''])
        if schema.get('description'): lines.extend([schema['description'],''])
        if schema.get('properties') is not None:
            lines.extend(['| 字段 | 类型 | 必填 | 校验与默认值 | 含义 |','| --- | --- | --- | --- | --- |'])
            for field,value in schema['properties'].items():
                resolved=deref(document,value)
                lines.append(f'| `{field}` | {safe(label(value))} | {"是" if field in schema.get("required",[]) else "否"} | {safe(constraints(resolved))} | {safe(resolved.get("description","见类型定义"))} |')
            if not schema['properties']: lines.append('| — | 空对象 | — | 不允许额外字段 | 成功无额外数据 |')
        else:
            lines.extend([f'类型：`{label(schema)}`。校验：{safe(constraints(schema))}。',''])
        if 'example' in schema: lines.extend(['','```json',json.dumps(schema['example'],ensure_ascii=False,indent=2),'```'])
    return '\n'.join(lines).rstrip()+'\n'

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write',action='store_true',help='Update bundled JSON and generated field documentation')
    args=parser.parse_args()
    contract=bundle()
    count,examples=validate(contract)
    content=json.dumps(contract,ensure_ascii=False,indent=2)+'\n'
    fields=render_fields(contract)
    if args.write:
        OUTPUT.write_text(content,encoding='utf-8',newline='\n')
        FIELDS.write_text(fields,encoding='utf-8',newline='\n')
    else:
        require(OUTPUT.read_text(encoding='utf-8')==content,'Bundled OpenAPI differs; run --write after review')
        require(FIELDS.read_text(encoding='utf-8')==fields,'Field documentation differs; run --write after review')
    print(f'PASS: {len(contract["paths"])} paths / {count} operations / {len(contract["components"]["schemas"])} schemas / {examples} valid examples.')
    print('Local contract checks only; not a complete OpenAPI meta-schema validator or HTTP integration test.')

if __name__=='__main__':
    try: main()
    except (ValueError,KeyError,OSError,ImportError) as error:
        print(f'FAIL: {error}',file=sys.stderr)
        sys.exit(1)
