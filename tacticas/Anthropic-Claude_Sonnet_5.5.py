MODEL_NAME="Sonnet55"
D={'UP':(0,-1),'DOWN':(0,1),'LEFT':(-1,0),'RIGHT':(1,0)}
class A:
    def decide_action(s,p):
        b=p["block_size"];W=p["width"]//b;H=p["height"]//b
        S=p["my_score"];g=S<200
        B=[(x//b,y//b) for x,y in p["my_body"]]
        L=len(B)
        o=set(B)
        E=[(e["head"][0]//b,e["head"][1]//b) for e in p["enemies"]]
        if S>=299:
            for e in p["enemies"]:o|={(x//b,y//b) for x,y in e["body"]}
            for x,y in E:o|={(x+u,y+v) for u,v in D.values()}
        F=[(x//b,y//b) for x,y in p["foods"]] or [B[0]]
        h=B[0]
        def d(a,c):
            x=abs(a[0]-c[0]);y=abs(a[1]-c[1])
            if g:x=min(x,W-x);y=min(y,H-y)
            return x+y
        G=[f for f in F if all(d(e,f)>d(h,f) for e in E)] or F
        N=L+8
        def m(c,a):
            x=c[0]+D[a][0];y=c[1]+D[a][1]
            if g:return x%W,y%H
            if 0<=x<W and 0<=y<H:return x,y
        best=None;bs=-9e9
        for a in p["valid_actions"]:
            n=m(h,a)
            if not n or n in o:continue
            v={n};q=[n]
            for c in q:
                if len(v)>=N:break
                for e in D:
                    t=m(c,e)
                    if t and t not in o and t not in v:v.add(t);q.append(t)
            r=len(v)
            k=r if r<N else 9999-min(d(n,f) for f in G)
            if k>bs:bs=k;best=a
        return best or p["valid_actions"][0]
def create_agent():return A()
