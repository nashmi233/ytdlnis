"""Capture real Android UI using a clearly labelled instrumentation fixture."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path('screenshots')
def adb(*args):
    return subprocess.check_output(['adb', *args])

def dump():
    adb('shell', 'uiautomator', 'dump', '/sdcard/share-window.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/share-window.xml'))

def shot(name):
    time.sleep(1)
    (out / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))

def tap(name):
    for _ in range(15):
        for node in dump().iter('node'):
            if node.get('resource-id', '').endswith(':id/' + name):
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                time.sleep(1)
                return
        time.sleep(1)
    raise RuntimeError('Missing view: ' + name)

adb('shell', 'cmd', 'statusbar', 'expand-notifications')
time.sleep(2)
shot('notification-preview')
texts = ' '.join(n.get('text', '') for n in dump().iter('node'))
assert '37%' in texts and 'مقطع تجريبي' in texts, texts
assert 'quickjs' not in texts and 'https://' not in texts, texts
adb('shell', 'cmd', 'statusbar', 'collapse')
adb('shell', 'am', 'start', '-n', 'com.nashmi233.downloader/com.deniscerri.ytdl.MainActivity')
time.sleep(5)
tap('historyFragment')
shot('download-share-preview')
tap('downloads_download_button_type')
shot('share-sheet-preview')
activities = adb('shell', 'dumpsys', 'activity', 'activities').decode()
assert 'ChooserActivity' in activities or 'ResolverActivity' in activities
print('Simple notification, per-video share button and Android chooser verified.')
