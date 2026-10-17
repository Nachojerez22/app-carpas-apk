import sys, pypdf, json
r = pypdf.PdfReader(sys.argv[1])
out=[]
for i,p in enumerate(r.pages):
    for a in p.get("/Annots") or []:
        a=a.get_object()
        uri=a.get("/A",{}).get("/URI")
        if uri: out.append((i+1,[round(float(x)) for x in a["/Rect"]],uri))
print(len(out))
for o in out[:12]: print(o)
json.dump(out, open(sys.argv[2],"w"))
