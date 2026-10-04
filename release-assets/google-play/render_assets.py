"""Render existing Android vector brand assets and compose the Play feature graphic.
Requires Pillow and CairoSVG. Run from anywhere within this repository.
No application UI is generated or reconstructed.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
import io, json, hashlib, shutil
from PIL import Image, ImageDraw, ImageFont
import cairosvg
BASE=Path(__file__).resolve().parent
REPO=BASE.parents[1]
RES=REPO/'app/src/main/res'
A='{http://schemas.android.com/apk/res/android}'
def attr(e,k,default=None): return e.get(A+k,default)
def svg_vector(path):
 root=ET.parse(path).getroot()
 svg=ET.Element('svg',{'xmlns':'http://www.w3.org/2000/svg','viewBox':f"0 0 {attr(root,'viewportWidth')} {attr(root,'viewportHeight')}"})
 def descend(src,dst):
  for e in src:
   if e.tag=='group':
    g=ET.SubElement(dst,'g',{'transform':f"translate({attr(e,'translateX','0')} {attr(e,'translateY','0')}) scale({attr(e,'scaleX','1')} {attr(e,'scaleY','1')})"});descend(e,g)
   elif e.tag=='path':
    color=attr(e,'fillColor','#000000');alpha=float(attr(e,'fillAlpha','1'))
    if len(color)==9:alpha*=int(color[1:3],16)/255;color='#'+color[3:]
    ET.SubElement(dst,'path',{'d':attr(e,'pathData'),'fill':color,'fill-opacity':str(alpha),'fill-rule':'evenodd' if attr(e,'fillType')=='evenOdd' else 'nonzero'})
   else:raise ValueError('Unsupported vector element '+e.tag)
 descend(root,svg)
 return ET.tostring(svg)
def render(name,w,h):
 return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg_vector(RES/'drawable'/f'{name}.xml'),output_width=w,output_height=h))).convert('RGBA')
if __name__=='__main__':
 import sys
 if '--sources' in sys.argv:
  for name,w,h in [('ic_logo_banner',1260,375),('ic_launcher_foreground',864,864),('ic_waterclour_background2',1024,1536)]:
   render(name,w,h).save('/tmp/zen-'+name+'.png')
  print('Existing app vectors rendered under /tmp')

def compose():
 # The released vector includes a translucent banner veil. Omit only that
 # decorative backdrop so the unchanged cloud and wordmark sit on shared artwork.
 svg=ET.fromstring(svg_vector(RES/'drawable/ic_logo_banner.xml'))
 for g in svg.iter():
  for p in list(g):
   if p.tag.endswith('path') and float(p.get('fill-opacity','1'))<1:g.remove(p)
 logo=Image.open(io.BytesIO(cairosvg.svg2png(bytestring=ET.tostring(svg),output_width=1260,output_height=375))).convert('RGBA')
 logo=logo.crop(logo.getbbox())
 bg=render('ic_waterclour_background2',1024,1536).crop((0,180,1024,680))
 out=Image.new('RGBA',(1024,500),'#FFFDF9');out=Image.alpha_composite(out,bg)
 out=Image.alpha_composite(out,Image.new('RGBA',out.size,(255,253,249,108)))
 w=856;logo=logo.resize((w,round(logo.height*w/logo.width)),Image.Resampling.LANCZOS)
 out.alpha_composite(logo,((1024-w)//2,146))
 d=ImageDraw.Draw(out);font=ImageFont.truetype(str(RES/'font/ubuntu_regular.ttf'),30)
 title='A quieter kind of puzzle.';tw=d.textlength(title,font=font)
 d.text(((1024-tw)/2,342),title,font=font,fill='#514B68')
 out.convert('RGB').save(BASE/'feature/feature-graphic-1024x500.png',optimize=True)
 shutil.copyfile(REPO/'app/src/main/ic_launcher-playstore.png',BASE/'icon/app-icon-512x512.png')
 # Review multiple sizes, masks and neighbouring backgrounds without changing outputs.
 sheet=Image.new('RGB',(1100,730),'#FFFDF9');d=ImageDraw.Draw(sheet)
 font=ImageFont.truetype(str(RES/'font/ubuntu_regular.ttf'),20)
 icon=Image.open(BASE/'icon/app-icon-512x512.png').convert('RGBA')
 for row,bgcolor in enumerate(['#FFFDF9','#292638']):
  d.rectangle((0,row*250,1100,row*250+250),fill=bgcolor)
  for i,size in enumerate([192,96,48,32]):
   im=icon.resize((size,size),Image.Resampling.LANCZOS);sheet.paste(im,(30+i*230,row*250+20),im)
   d.text((30+i*230,row*250+220),f'{size}px',font=font,fill='#746F83' if row==0 else '#FFFDF9')
 fg=render('ic_launcher_foreground',864,864)
 adaptive=Image.new('RGBA',fg.size,'#B4A9D2');adaptive.alpha_composite(fg)
 # Standard visible viewport: central 72dp of the 108dp layers.
 visible=adaptive.crop((144,144,720,720)).resize((160,160),Image.Resampling.LANCZOS)
 for i,shape in enumerate(['circle','rounded square','squircle']):
  mask=Image.new('L',(160,160));md=ImageDraw.Draw(mask)
  if shape=='circle':md.ellipse((0,0,159,159),fill=255)
  elif shape=='rounded square':md.rounded_rectangle((0,0,159,159),radius=32,fill=255)
  else:
   for y in range(160):
    for x in range(160):
     if abs((x-79.5)/79.5)**4+abs((y-79.5)/79.5)**4<=1:mask.putpixel((x,y),255)
  sheet.paste(visible,(30+i*366,520),mask);d.text((30+i*366,690),shape,font=font,fill='#292638')
 sheet.save(BASE/'review/icon-review.jpg',quality=95)
 complete=Image.new('RGB',(1200,880),'#FFFDF9')
 graphic=out.convert('RGB');graphic.thumbnail((960,469));complete.paste(graphic,(225,15))
 complete.paste(icon.resize((160,160),Image.Resampling.LANCZOS),(35,140),icon.resize((160,160),Image.Resampling.LANCZOS))
 for i,p in enumerate(sorted((BASE/'phone/production').glob('*.png'))):
  im=Image.open(p);im.thumbnail((184,328));complete.paste(im,(20+i*198,520))
 complete.save(BASE/'review/complete-play-set.jpg',quality=95)
 print('Feature graphic, unchanged Play icon export and review boards written.')
if __name__=='__main__' and '--sources' not in __import__('sys').argv:compose()
