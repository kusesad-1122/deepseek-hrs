#!/usr/bin/env python3
# Generate the bundle package.json: dsh + web-app as direct deps,
# every other @deepseek-ai package pinned via npm overrides so a
# half-published upstream release can never break the build.
import json, sys
lock = json.load(open(sys.argv[1]))
deps, overrides = {}, {}
for name, ver in sorted(lock.items()):
    if name in ('@deepseek-ai/dsh', '@deepseek-ai/dsh-web-app'):
        deps[name] = ver
    else:
        overrides[name] = ver
out = {
    'name': 'dsh-bundle',
    'version': '1.0.0',
    'private': True,
    'dependencies': deps,
    'overrides': overrides,
}
json.dump(out, sys.stdout, indent=2)
