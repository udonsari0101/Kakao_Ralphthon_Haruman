import os, sys
from lib import to_svg, to_vector
from assets import ASSETS
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..")
os.makedirs(f'{out}/svg', exist_ok=True); os.makedirs(f'{out}/android-drawable', exist_ok=True)
for name, (fn, w, h, dp, title) in ASSETS.items():
    root = fn()
    open(f'{out}/svg/{name}.svg', 'w').write(to_svg(root, w, h, title))
    open(f'{out}/android-drawable/{name}.xml', 'w').write(to_vector(root, w, h, dp))
print(len(ASSETS), 'assets')
