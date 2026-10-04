"""Deterministic composition of genuine ADB captures; no UI reconstruction."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from pathlib import Path
import json,hashlib
BASE=Path(__file__).resolve().parent
# Locate repository-owned app typeface.
FONT=next(p/'app/src/main/res/font/ubuntu_medium.ttf' for p in BASE.parents if (p/'app/src/main/res/font/ubuntu_medium.ttf').exists())
items=[
 ('01-menu','Find your perfect pace',(0,125,1440,2790)),
 ('02-gameplay','Relax into every puzzle',(0,110,1440,2870)),
 ('03-hints','Hints when you need them',(0,110,1440,2870)),
 ('04-notes','Space to think',(0,110,1440,2870)),
 ('05-pause-resume','Pick up where you left off',(0,125,1440,2790)),
 ('06-settings','Make yourself comfortable',(0,125,1440,2790)),
]
manifest=[]
for name,title,crop in items:
 out=Image.new('RGB',(1080,1920)); pix=out.load()
 for y in range(1920):
  t=y/1919
  for x in range(1080):
   q=x/1079
   pix[x,y]=tuple(round(a*(1-t)+b*t+q*c) for a,b,c in [(248,239,0),(246,244,0),(250,247,-1)])
 d=ImageDraw.Draw(out)
 d.text((64,42),'ZEN SUDOKU',font=ImageFont.truetype(str(FONT),24),fill='#675D91')
 size=56
 while d.textlength(title,font=ImageFont.truetype(str(FONT),size))>952:size-=1
 d.text((64,92),title,font=ImageFont.truetype(str(FONT),size),fill='#292638')
 d.line((64,176,136,176),fill='#E3BB6D',width=4)
 raw=Image.open(BASE/'raw'/f'{name}.png').convert('RGB')
 shot=raw.crop(crop); w=884; h=round(shot.height*w/shot.width)
 shot=shot.resize((w,h),Image.Resampling.LANCZOS)
 x=(1080-w)//2;y=208+(1668-h)//2
 # Shadow lives entirely outside the genuine screenshot rectangle.
 shadow=Image.new('RGBA',out.size);sd=ImageDraw.Draw(shadow)
 sd.rectangle((x,y+6,x+w,y+h+6),fill=(41,38,56,26))
 shadow=shadow.filter(ImageFilter.GaussianBlur(14))
 out=Image.alpha_composite(out.convert('RGBA'),shadow).convert('RGB')
 out.paste(shot,(x,y));out.save(BASE/'production'/f'{name}.png',optimize=True)
 manifest.append({'file':name+'.png','headline':title,'raw':f'raw/{name}.png','crop':crop,'placement':[x,y,w,h],'format':'PNG','mode':'RGB','dimensions':[1080,1920]})
(BASE/'evidence/composition.json').write_text(json.dumps(manifest,indent=2)+'\n')
contact=Image.new('RGB',(1080,370),'#FFFDF9');d=ImageDraw.Draw(contact)
for i,(name,title,crop) in enumerate(items):
 im=Image.open(BASE/'production'/f'{name}.png');im.thumbnail((174,310))
 contact.paste(im,(i*180,12));d.text((i*180+8,337),name[:2],font=ImageFont.truetype(str(FONT),22),fill='#292638')
contact.save(BASE/'review-contact-sheet.jpg',quality=95)
print('Created six 1080 x 1920 RGB PNG assets and contact sheet')
