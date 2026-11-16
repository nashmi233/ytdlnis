"""Capture unmodified screens from the installed APK on an Android emulator."""
import re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
out=Path('screenshots'); out.mkdir(exist_ok=True)
def adb(*args): return subprocess.check_output(['adb',*args])
def dump():
 adb('shell','uiautomator','dump','/sdcard/window.xml')
 raw=adb('shell','cat','/sdcard/window.xml')
 (out/'window.xml').write_bytes(raw)
 return ET.fromstring(raw)
def tap_id(name):
 for _ in range(15):
  for n in dump().iter('node'):
   if n.get('resource-id','').endswith(':id/'+name):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1);return
  time.sleep(2)
 raise RuntimeError('Missing view '+name)
def shot(name):
 time.sleep(1)
 (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
 dump()
adb('shell','am','start','-n','com.nashmi233.downloader/com.deniscerri.ytdl.MainActivity')
time.sleep(12)
tap_id('moreFragment')
shot('more')
for key in ['auto_detect_clipboard','auto_download_copied_link','save_to_gallery','ask_tiktok_download_type']:
 nodes=list(dump().iter('node'))
 assert any(n.get('resource-id','').endswith(':id/'+key) for n in nodes), key
assert not any(n.get('resource-id','').endswith(':id/'+k) for n in nodes for k in ['terminal','logs','command_templates'])
# Demonstrate persistence without changing the screenshot defaults.
tap_id('auto_download_copied_link')
adb('shell','am','force-stop','com.nashmi233.downloader')
adb('shell','am','start','-n','com.nashmi233.downloader/com.deniscerri.ytdl.MainActivity')
time.sleep(4)
tap_id('moreFragment')
assert any(n.get('checked')=='true' for n in dump().iter('node') if n.get('resource-id','').endswith(':id/auto_download_copied_link'))
tap_id('auto_download_copied_link')
# Open settings through the same row as a user.
for _ in range(3):
 if any(n.get('resource-id','').endswith(':id/settings') for n in dump().iter('node')): break
 adb('shell','input','swipe','500','1300','500','450','350');time.sleep(1)
tap_id('settings');shot('settings')
for _ in range(5):
 adb('shell','input','swipe','500','1400','500','450','350');time.sleep(.4)
shot('about')
texts=' '.join(n.get('text','') for n in dump().iter('node'))
assert 'نشمي' in texts, texts
assert not any(t in texts for t in ['Discord','Telegram','Weblate']), texts
print('Screens captured; quick settings persistence and About verified.')
