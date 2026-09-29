from data import *
S=lambda n:f"svg/{n}.svg"
WD=['월','화','수','목','금','토','일']
def wday(d): return WD[d%7]   # Sep 1 2026 = Tue -> index 1
SZ={'prop_tree_fruit':1.18,'prop_tree_big':1.08,'prop_tree_small':0.92,'prop_hole':1.0,'prop_sprout_ready':0.98,'prop_acorn_buried':0.86}
MW=[(1,'9월 1주','8.31 – 9.6',0,6),(2,'9월 2주','9.7 – 9.13',7,13),(3,'9월 3주','9.14 – 9.20',14,20),(4,'9월 4주','9.21 – 9.27',21,27),(5,'9월 5주','9.28 – 10.4',28,34)]
TODAY=29
def week_items(w): return [i for i in items if w[3]<=i['day']<=w[4]]
def days_of(w):
    start=w[3]; return [start+k for k in range(7)]
def strip(w,W,base=40,ground=0,labels=True,lab_cls='dl',depth=True,emptyw=0.55):
    """Absolutely positioned plants; slot width weighted by count. ground = y of soil line from top."""
    its=week_items(w); ds=days_of(w)
    cnt={d:sum(1 for i in its if i['day']==d) for d in ds}
    per_row=3
    wt={d:(max(emptyw,min(c,per_row)) if c else emptyw) for d,c in cnt.items()}
    tot=sum(wt.values()); unit=W/tot; x=0; out=[]; labs=[]
    for d in ds:
        sw=wt[d]*unit; c=cnt[d]; dit=[i for i in its if i['day']==d]
        # back row first (drawn behind)
        back=dit[per_row:per_row*2] if depth else []
        front=dit[:per_row]
        for layer,grp in ((1,back),(0,front)):
            n=len(grp)
            for k,it in enumerate(grp):
                s=base*SZ[asset(it)]*(0.78 if layer else 1)
                cx=x+sw*(k+0.5)/max(n,1)+(sw*0.12 if layer else 0)
                y=ground-(10 if layer else 0)
                hl=' hl' if it['state']=='sprout' else ''
                out.append(f'<img class="pl{hl}" src="{S(asset(it))}" style="left:{cx-s/2:.1f}px;top:{y-s:.1f}px;width:{s:.1f}px;height:{s:.1f}px;z-index:{5-layer}">')
        if labels and (c or d==TODAY):
            future=' fut' if d>TODAY else ''
            today=' td' if d==TODAY else ''
            dd=31 if d==0 else (d if d<=30 else d-30)
            cx=x+sw/2
            labs.append(f'<span class="{lab_cls}{today}{future}" style="left:{cx-20:.1f}px;width:40px">{dd}<em>{wday(d)}</em></span>')
        x+=sw
    return ''.join(out),''.join(labs)
def row_nonempty(r): return bool(r)
