# -*- coding: utf-8 -*-
"""小组件自测：尺寸取值 + 各尺寸下的行高/内容分级/卡片比例"""
def pick(minW,minH,maxW,maxH):
    w=max(minW,maxW); h=max(minH,maxH)
    return (w or 250),(h or 300)

print("① 尺寸取值（min/max → 采用）")
for a in [(0,0,0,0),(250,300,0,0),(250,300,250,420),(250,300,250,300),
          (250,180,250,420),(250,420,250,420)]:
    w,h=pick(*a); print("   min %-8s max %-8s -> %dx%d"%(a[0:2],a[2:4],w,h))

pad,headH,weekH=8,20,15
COL=7.0
print("\n② 各高度下的排版（250dp 宽，rows=4）")
for H in (300,350,380,420,470):
    gridH=H-pad-headH-weekH-pad
    rows=4; rowH=gridH/rows
    lines=4 if rowH>=66 else (3 if rowH>=50 else (2 if rowH>=25 else 1))
    colW=(250-pad*2)/COL
    ch=min(rowH-3, colW*2.1)
    info={1:"课名",2:"课名+地点",3:"课名+地点+节次",4:"课名+地点+节次+时间"}[lines]
    print("   H=%3ddp rowH=%5.1f lines=%d 卡片=%.1fx%.1f (%.2f:1) 间隔%.1f  %s"
          %(H,rowH,lines,ch,colW,ch/colW,rowH-ch,info))

print("\n③ 一周课时数变化（4×6，H=420）")
for rows in (4,5,6,7):
    gridH=420-pad-headH-weekH-pad; rowH=gridH/rows
    lines=4 if rowH>=66 else (3 if rowH>=50 else (2 if rowH>=25 else 1))
    print("   rows=%d rowH=%5.1f lines=%d 卡片=%.1f"%(rows,rowH,lines,min(rowH-3,(250-16)/7*2.1)))

print("\n④ 跨节课程 span 计算")
for j1,j2 in [(1,2),(1,4),(2,3),(3,4),(5,6),(1,8)]:
    print("   %d-%d节 -> span=%d"%(j1,j2,max(1,(j2+1)//2-(j1-1)//2)))
