/ Sample fact tables for ccar-forecasting-service: CCAR_FORECAST, CCAR_ACTUALS, CCAR_VARIANCE
/
/ Layout expected by the service (see ccar.forecasting.* in its application.properties):
/   BU_ID j | GEO_ID j | ACCOUNT_ID j | PORTFOLIO_ID s | T0_DATE d | T0 j | T_1 .. T_36 j
/ One row per (leaf node, account, portfolio, T0 date). BU_ID/GEO_ID are the hierarchy node the row is booked
/ against; the service sums every row under the requested node using BU_HIERARCHY's BU_ID_LEVELn columns.
/ Forecast: T_n = T0 + n months. Actuals / variance: T_n = T0 - n months. Null = no data (UI shows "-").
/
/ Usage (pick one):
/  A) in the KDB server's own q session (BU_HIERARCHY already loaded there):
/       q)\l CCAR_FACT_TABLES_LOAD.q
/       q)build[]
/  B) from a LOCAL q session, against the remote server:
/       q)\l CCAR_FACT_TABLES_LOAD.q
/       q)pullBuildPush[`:192.168.11.200:5173:test:test@123]

accts:210001 400001 500001;                          / Balance, Revenue, Expense
base:accts!800 8 70f;                                / per-leaf magnitude of each account
ports:`PORT_0001`PORT_0002;
dates:2025.12.31 2026.03.31 2026.06.30 2026.09.30;
pcols:`T0,`$"T_",/:string 1+til 36;

build:{[]
  leaves:exec BU_ID from BU_HIERARCHY where LEVEL_ID=max LEVEL_ID;
  geos:$[`GEO_HIERARCHY in tables[]; exec GEO_ID from GEO_HIERARCHY where LEVEL_ID=max LEVEL_ID; enlist 0N];
  n:count leaves;
  mk:{[l;g;n;d;p;a] ([] BU_ID:l; GEO_ID:n?g; ACCOUNT_ID:n#a; PORTFOLIO_ID:n#p; T0_DATE:n#d)}[leaves;geos;n];
  k:raze mk ./: dates cross ports cross accts;
  b:(base k`ACCOUNT_ID)*1+0.5*(count k)?1.0;
  fv:{[b;m] `long$b*1+0.004*m}[b] each til 37;
  / no actuals older than 29 months for the earliest T0 date -> nulls
  av:{[b;d;m] v:`long$b*1-0.003*m; $[m<30; v; ?[d=first dates; 0N; v]]}[b;k`T0_DATE] each til 37;
  `CCAR_FORECAST set k,'flip pcols!fv;
  `CCAR_ACTUALS set k,'flip pcols!av;
  `CCAR_VARIANCE set k,'flip pcols!av-fv;
  -1 "CCAR_FORECAST/CCAR_ACTUALS/CCAR_VARIANCE built: ",string[count k]," rows each";
  count k }

/ option B: pull the hierarchies, build locally, push the three tables (one IPC message each)
pullBuildPush:{[conn]
  h:hopen (conn;30000);
  `BU_HIERARCHY set h"0!select from BU_HIERARCHY";
  if[h"`GEO_HIERARCHY in tables[]"; `GEO_HIERARCHY set h"0!select from GEO_HIERARCHY"];
  build[];
  {[h;t] h(set;t;value t)}[h] each `CCAR_FORECAST`CCAR_ACTUALS`CCAR_VARIANCE;
  -1 "Pushed: ",", " sv {string[x],"=",string y}'[`CCAR_FORECAST`CCAR_ACTUALS`CCAR_VARIANCE;
      h"count each (CCAR_FORECAST;CCAR_ACTUALS;CCAR_VARIANCE)"];
  hclose h; }
