# usage: prep.py <icon512.png> <outdir>  -> icon.png (512) + icon-small.png (icon at 64px in circle on a busy wallpaper, shown at 2x)
import sys, random
from PIL import Image, ImageDraw
src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB").resize((512,512), Image.LANCZOS)
im.save(f"{out}/icon.png")
random.seed(3)
W,H=300,140
wp=Image.new("RGB",(W,H),(90,130,170)); d=ImageDraw.Draw(wp)
for _ in range(40):
    x,y,r=random.randint(0,W),random.randint(0,H),random.randint(10,40)
    d.ellipse((x-r,y-r,x+r,y+r),fill=tuple(random.randint(40,230) for _ in range(3)))
def circ(img,n):
    s=img.resize((n,n),Image.LANCZOS); m=Image.new("L",(n,n),0); ImageDraw.Draw(m).ellipse((0,0,n-1,n-1),fill=255); return s,m
s,m=circ(im,64); wp.paste(s,(118,38),m)
wp.resize((W*2,H*2),Image.LANCZOS).save(f"{out}/icon-small.png")
