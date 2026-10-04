"""Validate upload formats, screenshot fidelity and retained icon provenance."""
from pathlib import Path
from PIL import Image
import hashlib,json,xml.etree.ElementTree as E
BASE=Path(__file__).resolve().parent
REPO=BASE.parents[1]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def verify(p,size,mode):
 im=Image.open(p);im.verify();im=Image.open(p)
 assert (im.size,im.mode,im.format)==(size,mode,'PNG'),str(p)
 return {'path':str(p.relative_to(BASE)),'dimensions':list(size),'mode':mode,'format':'PNG','bytes':p.stat().st_size,'sha256':digest(p)}
result={'release_source':'c93405de372222d141dafa010cfdf5340989ac60','screenshots':[]}
manifest=json.loads((BASE/'phone/evidence/composition.json').read_text())
assert len(manifest)==6
for item in manifest:
 p=BASE/'phone/production'/item['file'];check=verify(p,(1080,1920),'RGB')
 x,y,w,h=item['placement'];expected=Image.open(BASE/'phone'/item['raw']).convert('RGB').crop(item['crop']).resize((w,h),Image.Resampling.LANCZOS)
 assert expected.tobytes()==Image.open(p).crop((x,y,x+w,y+h)).tobytes(),str(p)
 check['captured_ui_matches_raw_after_declared_crop_and_resize']=True
 result['screenshots'].append(check)
result['feature']=verify(BASE/'feature/feature-graphic-1024x500.png',(1024,500),'RGB')
result['icon']=verify(BASE/'icon/app-icon-512x512.png',(512,512),'RGBA')
assert result['icon']['bytes']<=1024*1024
assert digest(BASE/'icon/app-icon-512x512.png')==digest(REPO/'app/src/main/ic_launcher-playstore.png')
result['icon']['identical_to_released_source']=True
result['raw_captures']={p.name:digest(p) for p in sorted((BASE/'phone/raw').glob('*.png'))}
assert len(result['raw_captures'])==8
for p in (BASE/'phone/evidence').glob('*.xml'):
 for n in E.parse(p).getroot().iter('node'):
  assert n.get('package')=='rew.lightgames.zensudoku' or not(n.get('text') or n.get('content-desc')),str(p)
result['legacy_launcher_outputs']=[]
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
 for stem in ['ic_launcher','ic_launcher_round']:
  p=REPO/f'app/src/main/res/mipmap-{density}/{stem}.png';im=Image.open(p);assert im.size==(size,size)
  result['legacy_launcher_outputs'].append({'path':str(p.relative_to(REPO)),'dimensions':list(im.size),'sha256':digest(p)})
ns='{http://schemas.android.com/apk/res/android}'
for stem in ['ic_launcher','ic_launcher_round']:
 root=E.parse(REPO/f'app/src/main/res/mipmap-anydpi-v26/{stem}.xml').getroot()
 assert root.find('background').get(ns+'drawable')=='@color/ic_launcher_background'
 assert root.find('foreground').get(ns+'drawable')=='@drawable/ic_launcher_foreground'
result['adaptive_sources_consistent']=True
(BASE/'review/validation.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS: six RGB 1080x1920 screenshots; RGB 1024x500 feature; unchanged RGBA 512x512 icon; ten legacy outputs; adaptive references; eight raw hashes; screenshot pixel fidelity; XML contains no external personal text.')
