# -*- coding: utf-8 -*-
"""按 WidgetProvider.draw() 最新逻辑（按实际课位分行 + 三档文字）出预览图"""
from PIL import Image, ImageDraw, ImageFont
FONT="/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FONTB="/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
DENS=3.0
def dp(v): return int(round(v*DENS))
def font(sz,bold=False):
    return ImageFont.truetype(FONTB if bold else FONT, max(6,int(round(sz))))
def hx(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))
def blend(c,t,k): return tuple(int(c[i]+(t[i]-c[i])*k) for i in range(3))
def alpha(c,a): return (c[0],c[1],c[2],a)
_MEAS=[None]
def meas(txt,f): return _MEAS[0].textlength(txt,font=f)
def fit(s,maxw,f):
    if not s: return ""
    if meas(s,f)<=maxw: return s
    n=len(s)
    while n>1 and meas(s[:n]+"…",f)>maxw: n-=1
    return s[:1] if n<=1 else s[:n]+"…"
import PIL.ImageDraw as _ID
class M:
    def __init__(s,d): s.d=d
    def textbbox(s,xy,txt,font=None,anchor=None): return s.d.textbbox(xy,txt,font=font)
def measure(d,txt,f): return d.textlength(txt,font=f)

# (课名, 地点, 周几0-6, j1, j2, 色)
COURSES=[
 ("英语","6A-528",1,1,2,"#7C6BB0"),("高数","东10A-104",2,1,2,"#5B87B8"),
 ("制图","6A-411",3,1,2,"#C08A52"),("计算机","西综合313",4,1,2,"#9C6B8E"),
 ("高数","东10A-104",0,3,4,"#5B87B8"),("制图","6A-217",1,3,4,"#C08A52"),
 ("资环","六一阶",2,3,4,"#4E9E8F"),("高数","东10A-104",3,3,4,"#5B87B8"),
 ("制图","6A-411",4,3,4,"#C08A52"),
 ("计算机","西综合313",0,5,6,"#9C6B8E"),("游泳","东游泳场1",2,5,6,"#5F8AA8"),
 ("心理","西2-302",3,5,6,"#A8637F"),("思政","6B-104",4,5,6,"#6E8F76"),
 ("心理","西2-302",0,7,8,"#A8637F"),("思政","6B-104",2,7,8,"#6E8F76"),
]
WD=["一","二","三","四","五","六","日"]; TODAY=5; WEEK=10

def draw(wdp,hdp,dark=False,tag="",tier=0):
    W,H=dp(wdp),dp(hdp)
    bg=(22,20,15) if dark else (255,255,255)
    fg=(242,237,227) if dark else (36,33,28)
    sub=(139,129,114) if dark else (154,160,174)
    im=Image.new("RGBA",(W,H),(0,0,0,0)); d=ImageDraw.Draw(im); _MEAS[0]=d
    d.rounded_rectangle([0,0,W,H],dp(20),fill=bg)
    pad=dp(8); headH=dp(20); weekH=dp(15)
    # 顶部
    f=font(dp(12.5),True); d.text((pad,pad+dp(2)),"第 %d 周"%WEEK,font=f,fill=fg)
    f=font(dp(10.5)); t="11/14 周六"
    d.text((W-pad-measure(d,t,f),pad+dp(3)),t,font=f,fill=sub)
    # 表头
    gridTop=pad+headH+weekH; gridH=H-gridTop-pad
    colW=(W-pad*2)/7.0
    for i in range(7):
        cx=pad+i*colW+colW/2.0; cy=pad+headH+dp(6)
        f=font(dp(9.5), i==TODAY)
        d.text((cx,cy),WD[i],font=f,fill=fg if i==TODAY else sub,anchor="mm")
        if i==TODAY:
            d.ellipse([cx-dp(1.6),cy+dp(6.5)-dp(1.6),cx+dp(1.6),cy+dp(6.5)+dp(1.6)],fill=fg)
    starts=sorted(set(c[3] for c in COURSES)); rows=len(starts)
    rowH=gridH/float(rows)
    lines=tier+2
    if rowH<dp(52) and lines>2: lines-=1
    if rowH<dp(26): lines=1
    for ri,j1 in enumerate(starts):
        top=gridTop+ri*rowH
        for wd in range(7):
            cell=[c for c in COURSES if c[2]==wd and c[3]==j1]; m=len(cell)
            for k,(nm,pl,_,j1b,j2,colh) in enumerate(cell):
                span=max(1,(j2-j1+2)//2)
                if ri+span>rows: span=rows-ri
                spanH=span*rowH
                ch=spanH-dp(1.5)
                c0=top+dp(0.75)
                inner=max(0,ch-dp(2))
                t=c0+dp(1)+inner*k/m
                b=c0+ch-dp(1)-inner*(m-1-k)/m
                col=hx(colh)
                soft=blend(col,(0,0,0) if dark else (255,255,255),0.72)
                tc=blend(col,(255,255,255) if dark else (0,0,0),0.78 if dark else 0.55)
                l=pad+wd*colW+dp(1.5); r=pad+(wd+1)*colW-dp(1.5)
                d.rounded_rectangle([l,t,r,b],dp(8.5),fill=soft)
                cellH=b-t; avail=(r-l)-dp(2)
                if lines>=3: fs=min(dp(12.5),cellH*0.28)
                elif lines==2: fs=min(dp(12),cellH*0.38)
                else: fs=min(dp(12),cellH*0.55)
                fs=max(dp(5.5),fs)
                fn=font(fs,True)
                while measure(d,nm,fn)>avail and fs>dp(7.5):
                    fs-=dp(0.3); fn=font(fs,True)
                nm2=fit(nm,avail,fn)
                cx=(l+r)/2.0; cy=(t+b)/2.0
                fsP=max(dp(6),fs*0.62)
                while pl and measure(d,pl,font(fsP))>avail and fsP>dp(3.8): fsP-=dp(0.2)
                fp=font(fsP)
                pl1=fit(pl,avail,fp); pl2=""
                hasP=bool(pl) and lines>=2
                showPd=lines>=3
                pe=fit("%d-%d节"%(j1b,j2),avail*1.15,fp) if showPd else ""
                TM={1:"08:00",3:"10:10",5:"14:00",7:"16:00",9:"19:00"}
                tm=TM.get(j1b,"")
                showTm=lines>=4 and bool(tm)
                te=fit(tm,avail*1.20,fp) if showTm else ""
                gapP=fsP*1.32
                nlines=1
                if hasP: nlines += 1
                if showPd: nlines += 1
                if showTm: nlines += 1
                if showTm: nlines += 1
                blockH=fs*0.80+gapP*(nlines-1)+fsP*0.10
                y=cy-blockH/2.0+fs*0.80
                d.text((cx,y),nm2,font=fn,fill=tc,anchor="mm")
                if hasP:
                    y+=gapP*1.15; d.text((cx,y),pl1,font=fp,fill=alpha(tc,205),anchor="mm")
                    if pl2:
                        y+=gapP; d.text((cx,y),pl2,font=fp,fill=alpha(tc,205),anchor="mm")
                if showPd:
                    y+=gapP; d.text((cx,y),pe,font=fp,fill=alpha(tc,205),anchor="mm")
                if showTm:
                    y+=gapP; d.text((cx,y),te,font=fp,fill=alpha(tc,205),anchor="mm")
                if showTm:
                    y+=gapP; d.text((cx,y),te,font=fp,fill=alpha(tc,205),anchor="mm")
    # 裁到圆角
    mask=Image.new("L",(W,H),0); ImageDraw.Draw(mask).rounded_rectangle([0,0,W,H],dp(20),fill=255)
    im.putalpha(mask)
    out=Image.new("RGBA",(W,H),(238,238,238,255)); out.alpha_composite(im)
    out.convert("RGB").save("/tmp/w_%s.png"%tag)
    print(tag, "%dx%dpx"%(W,H), "rows=%d"%rows, "rowH=%.1fdp"%(rowH/DENS), "lines=%d"%lines)

for tag,w,h,t in [("4x4",250,300,0),("4x5",250,400,1),("4x6",250,490,2)]:
    draw(w,h,False,tag,t)
