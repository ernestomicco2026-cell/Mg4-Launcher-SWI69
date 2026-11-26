import sys, logging
from loguru import logger; logger.remove()
from androguard.misc import AnalyzeAPK
apk = sys.argv[1]
a, ds, dx = AnalyzeAPK(apk)
codes = {1:"general/ICarGeneralService",2:"config/ICarConfigService",3:"evs/ICarEvsService",4:"ev/ICarEvService",5:"ipk/ICarIpkService",
 6:"eol/ICarEolService",7:"hvac/ICarHvacService",8:"vs/ICarVehicleSettingService",9:"feature/IFeatureService",10:"audio/ICarAudioService",
 11:"carstate/ICarStateService",12:"clock/ICarTimeService",13:"lamp/ICarLampService",14:"lock/ICarLockService",15:"comfortablet/ICarComfortabletService",16:"tbox/ITboxService"}
classes = {}
for d in ds:
    for c in d.get_classes(): classes[c.get_name()] = c
rows=[]
for code, path in codes.items():
    iface = "Lcom/saicmotor/carapi/%s;" % path
    stub = "Lcom/saicmotor/carapi/%s$Stub;" % path
    ic, sc = classes.get(iface), classes.get(stub)
    if not ic or not sc: print("MISSING", code, path, file=sys.stderr); continue
    tx = {}
    for f in sc.get_fields():
        n = f.get_name()
        if n.startswith("TRANSACTION_"):
            v = f.get_init_value()
            tx[n[12:]] = v.get_value() if v else None
    meth = {}
    for m in ic.get_methods():
        meth.setdefault(m.get_name(), []).append(m.get_descriptor())
    for name, ds_ in meth.items():
        if len(ds_)!=1: continue
        d = ds_[0].replace(" ","")
        if not (name.startswith("get") or name.startswith("is")): continue
        if d not in ("()I","()F","()Z"): continue
        if tx.get(name) is None: print("NOTX", path, name, file=sys.stderr); continue
        rows.append((code, path.split('/')[1], tx[name], name, d[-1]))
rows.sort(key=lambda r:(r[0], r[2]))
for r in rows: print("%d\t%s\t%d\t%s\t%s" % r)
