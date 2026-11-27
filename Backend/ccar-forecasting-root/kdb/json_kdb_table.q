/ Builds tables from table_data_json/*.json locally, then pushes them to the remote server via handle h

/Forcast
json:raze read0 `:table_data_json/Forcast.json

data:.j.k json

Forcast:flip data

Forcast:flip Forcast

/ Actuals

json:raze read0 `:table_data_json/Actuals.json

data:.j.k json

Actuals:flip data

Actuals:flip Actuals

/ .j.k returns numbers as float and strings as char lists - cast to proper kdb types
/ T0_DATE arrives as "31-Dec-25" -> 2025.12.31
parseDMY:{[s] p:"-" vs s; m:1+("JAN";"FEB";"MAR";"APR";"MAY";"JUN";"JUL";"AUG";"SEP";"OCT";"NOV";"DEC")?upper p 1; "D"$"20",p[2],".",(-2#"0",string m),".",-2#"0",p 0}

Actuals:update `long$BU_ID,`long$LEVEL_ID,`long$OP_CODE_ID,`long$ACCOUNT_ID,
    `$BU_NAME,`$PARENT_BU_NAME,`$CCY_CODE,`$PORTFOLIO_ID,
    T0_DATE:parseDMY each T0_DATE from Actuals

/ BU_HIER

json:raze read0 `:table_data_json/BU_HIER.json

data:.j.k json

BU_HIER:flip data

BU_HIER:flip BU_HIER

/ BU_HIRERARCHY

json:raze read0 `:table_data_json/BU_HIRERARCHY.json

data:.j.k json

BU_HIRERARCHY:flip data

BU_HIRERARCHY:flip BU_HIRERARCHY

/ BU_LEAF_CLOSURE

json:raze read0 `:table_data_json/BU_LEAF_CLOSURE.json

data:.j.k json

BU_LEAF_CLOSURE:flip data

BU_LEAF_CLOSURE:flip BU_LEAF_CLOSURE

BU_LEAF_CLOSURE:update `long$NODE_ID,`long$LEAF_NODE_ID from BU_LEAF_CLOSURE

/ CCY_Master

json:raze read0 `:table_data_json/CCY_Master.json

data:.j.k json

CCY_Master:flip data

CCY_Master:flip CCY_Master

/ CCY_CODE ("USD") and CCY_NAME ("US Dollar") arrive as strings - cast to symbols to match Actuals.CCY_CODE
CCY_Master:update `$CCY_CODE,`$CCY_NAME from CCY_Master

/ GEO_HIER

json:raze read0 `:table_data_json/GEO_HIER.json

data:.j.k json

GEO_HIER:flip data

GEO_HIER:flip GEO_HIER

/ GEO_HIRERARCHY

json:raze read0 `:table_data_json/GEO_HIRERARCHY.json

data:.j.k json

GEO_HIRERARCHY:flip data

GEO_HIRERARCHY:flip GEO_HIRERARCHY

/ GEO_LEAF_CLOSURE

json:raze read0 `:table_data_json/GEO_LEAF_CLOSURE.json

data:.j.k json

GEO_LEAF_CLOSURE:flip data

GEO_LEAF_CLOSURE:flip GEO_LEAF_CLOSURE

GEO_LEAF_CLOSURE:update `long$NODE_ID,`long$LEAF_NODE_ID from GEO_LEAF_CLOSURE

/ PORTFOLIO_MASTER

json:raze read0 `:table_data_json/PORTFOLIO_MASTER.json

data:.j.k json

PORTFOLIO_MASTER:flip data

PORTFOLIO_MASTER:flip PORTFOLIO_MASTER
/ ---------------------------------------------------
/ Push all tables to the remote kdb server
/ ---------------------------------------------------

if[not `h in key `.; h:hopen `:192.168.11.200:5173];

tblNames:`Forcast`Actuals`BU_HIER`BU_HIRERARCHY`BU_LEAF_CLOSURE`CCY_Master`GEO_HIER`GEO_HIRERARCHY`GEO_LEAF_CLOSURE`PORTFOLIO_MASTER;

{h (set;x;value x)} each tblNames;

/ confirm row counts on the server
show tblNames!{h ("count ",string x)} each tblNames;
