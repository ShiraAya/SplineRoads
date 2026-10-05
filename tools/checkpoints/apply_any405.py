from pathlib import Path, PurePosixPath
import base64, hashlib, json, zlib

# Reviewed local source delta. Source must match BOTH input and output hashes.
# The transfer artifact was downloaded and compared byte-for-byte before this repair.
p=Path('tools/checkpoints/any405.payload')
s=p.read_text().strip()
def sha(data): return hashlib.sha256(data).hexdigest()
correct='24f0b729486f1a0e65e6928f70e778f25c96c72ae8acfa8af7e5474fdc80471f'
if sha(s.encode())!=correct:
    assert sha(s.encode())=='b8bb0988984dabfe624b3618d9ed98221d0de75be4cf56d29a47ca729fabc67a', 'Unrecognized transfer bytes'
    repairs=[(405,406,''),(708,709,''),(753,754,'+'),(1328,1329,''),(1384,1385,'g'),(1443,1445,'JI'),(1822,1824,'k9'),(1941,1943,'3'),(2190,2193,'ng71'),(2338,2338,'x'),(2339,2340,''),(2345,2345,'2'),(2346,2347,''),(7008,7010,'TZ')]
    for a,b,v in reversed(repairs): s=s[:a]+v+s[b:]
assert sha(s.encode())==correct, 'Transfer repair did not reproduce exact reviewed payload'
obj=json.loads(zlib.decompress(base64.b64decode(s,validate=True)))
assert obj.keys()=={'before','after','edits'}
assert obj['before'].keys()==obj['after'].keys()==obj['edits'].keys()
prepared={}
for name, edits in obj['edits'].items():
    rel=PurePosixPath(name)
    assert not rel.is_absolute() and '..' not in rel.parts and '\\' not in name
    assert name=='build.gradle' or name.startswith(('src/','tools/'))
    dest=Path(name);old=dest.read_bytes() if dest.exists() else None
    assert (sha(old) if old is not None else None)==obj['before'][name], 'Changed baseline: '+name
    lines=(old or b'').decode('utf-8').splitlines(keepends=True)
    for start,end,text in reversed(edits):
        assert 0<=start<=end<=len(lines)
        lines[start:end]=[text]
    new=''.join(lines).encode('utf-8')
    assert sha(new)==obj['after'][name], 'Not the reviewed output: '+name
    prepared[dest]=new
# Validate all paths/hashes before changing any source. Do not partially apply on a conflict.
for dest, data in prepared.items():
    dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
    print('APPLIED',dest,sha(data))
p.write_text(s+'\n')
progress=Path('PROGRESS.md');previous=progress.read_text()
progress.write_text('# WIP: 任意所选车道的保留车道分离，源码已提交待本次CI\n\n本轮开始2026-10-05T13:01:02Z。TEMPORARY允许内侧/中间/外侧与单车道；DETACH原限制不变。真实分段路面/侧壁、碰撞栅格、净空三角形、拾取、标线裁切、持久化与删除恢复一致；新增沿原车道先行64/96/128格候选，给自动升降后转出留空间。\n\n本地112几何案例、112规划/记录/删除案例通过，但世界/NBT为测试适配器；本提交完整Forge编译尚待CI，未进行客户端/GPU/真实存档测试。版本0.40.3-alpha、存档36、协议54，需备份及同步客户端服务器。13项文档问题另行修复，不把本项通过当成全部问题完成。\n\n---\n\n## 历史检查点\n\n'+previous)
print('PASS: all',len(prepared),'reviewed source files applied exactly; awaiting this commit tests')
