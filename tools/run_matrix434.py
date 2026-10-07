#!/usr/bin/env python3
"""Run independent real-planner model cases; no world writes are simulated.

Compile first: bash tools/test_ramp39_model.sh
Per-process timeout is a TEST failure, never a production search deadline.
"""
import argparse, concurrent.futures, json, pathlib, re, subprocess, sys

p=argparse.ArgumentParser()
p.add_argument('--out',required=True)
p.add_argument('--type',choices=['ordinary','highway'],default='ordinary')
p.add_argument('--end',choices=['outgoing','east','west'],default='outgoing')
p.add_argument('--left',action='store_true')
p.add_argument('--flexible',action='store_true')
p.add_argument('--workers',type=int,default=2)
p.add_argument('--timeout',type=int,default=90)
a=p.parse_args();out=pathlib.Path(a.out);out.mkdir(parents=True,exist_ok=True)
cp='build/ramp39-core/classes:build/ramp39-model/classes:src/main/resources'

def run(rc):
 row,col=rc;label=f'{chr(65+row)}{col}';log=out/f'{label}.log'
 cmd=['java','-Xmx768m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-cp',cp,
      'com.sora.splineroads.world.RampMatrix434Validation',str(row),str(col),str(a.left).lower(),a.type,str(out/'paths')]
 if a.end!='outgoing':cmd += ['500' if a.end=='east' else '0','flexible' if a.flexible else 'exact']
 elif a.flexible:raise ValueError('Flexible test currently requires explicit east or west fixture')
 with log.open('w') as f:
  try:code=subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT,timeout=a.timeout).returncode
  except subprocess.TimeoutExpired:f.write(f'\nTEST_TIMEOUT {a.timeout}s\n');code=124
 text=log.read_text();result=next((s for s in text.splitlines() if s.startswith('RESULT ')),f'RESULT {label} FAIL exit={code}')
 print(result,flush=True)
 values={k:float(v) for k,v in re.findall(r'(length|max_grade|ymin|ymax|vertical_travel|ms)=([0-9.]+)',result)}
 return {'case':label,'passed':code==0,'result':result,**values}

with concurrent.futures.ThreadPoolExecutor(max_workers=a.workers) as ex:
 results=list(ex.map(run,((r,c) for r in range(4) for c in range(1,9))))
summary={'fixture':vars(a),'passed':sum(x['passed'] for x in results),'total':len(results),'results':results}
(out/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
print(f"MATRIX434 {summary['passed']}/{summary['total']} passed ({a.type}, {a.end}, left={a.left})")
sys.exit(0 if summary['passed']==summary['total'] else 1)
