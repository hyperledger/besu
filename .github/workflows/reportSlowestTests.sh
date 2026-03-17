#!/usr/bin/env python3
##
## Copyright contributors to Besu.
##
## Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
## the License. You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
## an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
## specific language governing permissions and limitations under the License.
##
## SPDX-License-Identifier: Apache-2.0
##

import re
import glob
import os
import sys

count = int(sys.argv[1]) if len(sys.argv) > 1 else 10
runner = sys.argv[2] if len(sys.argv) > 2 else '?'

results = []
for f in glob.glob('**/build/test-results/**/TEST-*.xml', recursive=True):
    with open(f) as fh:
        content = fh.read()
    m = re.search(r'<testsuite\s[^>]*\bname="([^"]+)"[^>]*\btime="([^"]+)"', content)
    if not m:
        m = re.search(r'<testsuite\s[^>]*\btime="([^"]+)"[^>]*\bname="([^"]+)"', content)
        if m:
            t, name = float(m.group(1)), m.group(2)
        else:
            continue
    else:
        name, t = m.group(1), float(m.group(2))
    results.append((t, name))

if not results:
    sys.exit(0)

results.sort(reverse=True)
summary = os.environ.get('GITHUB_STEP_SUMMARY', '/dev/stdout')

lines = [
    f'## {count} Slowest Test Classes (runner {runner})\n',
    '| Rank | Class | Time |\n',
    '|------|-------|------|\n',
]
for i, (t, name) in enumerate(results[:count], 1):
    mins = int(t // 60)
    secs = t % 60
    time_str = f'{mins}m {secs:.0f}s' if mins else f'{secs:.1f}s'
    short = name.split('.')[-1]
    lines.append(f'| {i} | `{short}` | {time_str} |\n')

with open(summary, 'a') as f:
    f.writelines(lines)
