MODEL_NAME="Sonnet55"
D={'UP':(0,-1),'DOWN':(0,1),'LEFT':(-1,0),'RIGHT':(1,0)}
class A:
    def decide_action(s,p):
        b=p["block_size"];W=p["width"]//b;H=p["height"]//b
        g=p["my_score"]<200
        B=[(x//b,y//b) for x,y in p["my_body"]]
        L=len(B)
        T={c:L-i for i,c in enumerate(B)}
        o=set()
        for e in p["enemies"]:
            o|={(x//b,y//b) for x,y in e["body"]}
            if p["my_score"]>=299:
                x,y=e["head"];x//=b;y//=b
                o|={(x+u,y+v) for u,v in D.values()}
        F=[(x//b,y//b) for x,y in p["foods"]] or [B[0]]
        N=L*3+40
        def m(c,a):
            x=c[0]+D[a][0];y=c[1]+D[a][1]
            if g:return x%W,y%H
            if 0<=x<W and 0<=y<H:return x,y
        best=None;bs=-9e9
        for a in p["valid_actions"]:
            n=m(B[0],a)
            if not n or n in o or T.get(n,0)>1:continue
            e=n in F
            v={n:1};q=[n]
            for c in q:
                if len(v)>=N:break
                for d in D:
                    t=m(c,d)
                    if t and t not in o and t not in v and T.get(t,0)<=v[c]+1-e:v[t]=v[c]+1;q.append(t)
            r=len(v)
            c=9e9
            for f in F:
                dx=abs(n[0]-f[0]);dy=abs(n[1]-f[1])
                if g:dx=min(dx,W-dx);dy=min(dy,H-dy)
                c=min(c,dx+dy)
            k=r*9-99999
            if r>=N or B[-1] in v:k=-c+r/999-(L>3 and r<L)*999
            if k>bs:bs=k;best=a
        return best or p["valid_actions"][0]
def create_agent():return A()
