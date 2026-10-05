#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('summary',Path(__file__).with_name('summarize_profile.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class SummaryTests(unittest.TestCase):
    def test_nested_and_missing(self):
        r=m.summarize(['[server] SR connect: 300 ms, roads=200, requested=1, phases(ms)={edit=299.0}',
            '[server] SR edit: 290 ms, roads=200, requested=1, phases(ms)={structure_plan=200.5, terrain_chunk_access=12.25}',
            '[server] SR edit: 100 ms, roads=200, requested=1, phases(ms)={structure_plan=80.0}',
            'player private name at position 100 200 300'])
        self.assertEqual(r['records'],3)
        self.assertEqual(r['operations']['edit']['total_ms']['median'],195)
        self.assertEqual(r['operations']['edit']['phase_ms']['terrain_chunk_access']['count'],1)
        self.assertEqual(r['operations']['connect']['total_ms']['median'],300)
        self.assertNotIn('private',str(r));self.assertNotIn('position',str(r))
    def test_groups_and_load(self):
        a=m.parse('SR edit: 1,200 ms, roads=20,000, requested=1,200, phases(ms)={foo=1.2E2}')
        b=m.parse('SR edit: 1.200 ms, roads=20.000, requested=1.200, phases(ms)={foo=1.2E2}')
        self.assertEqual(a,b);self.assertEqual(a[1:4],(1200,20000,1200))
        self.assertEqual(m.parse('SR load: 300 ms, roads=4,000')[2],4000)
    def test_invalid(self):
        for text in ['a=NaN','a=-1','a=Infinity','a=1,a=2']:
            r=m.summarize(['SR edit: 2 ms, roads=3, requested=1, phases(ms)={'+text+'}'])
            self.assertEqual(r['records'],0);self.assertEqual(r['malformed_records'],1)
    def test_empty_phases(self):
        r=m.summarize(['SR edit: 0 ms, roads=0, requested=0, phases(ms)={}'])
        self.assertEqual(r['operations']['edit']['phase_ms'],{})
        self.assertEqual(r['operations']['edit']['total_ms']['p95_nearest_rank'],0)
    def test_percentile(self):
        self.assertEqual(m.stats(list(range(1,21)))['p95_nearest_rank'],19)
if __name__=='__main__':unittest.main()
