/ Remote kdb connection - all tables live on this server
if[not `h in key `.; h:hopen `:192.168.11.200:5173];

/ Runs a functional select on the server: remoteSelect[`Actuals; whereClause]
remoteSelect:{[t;w] h ({[t;w] ?[t;w;0b;()]};t;w)};

getAggregateActuals:{
 [hierarchyType;hierarchyId;portfolioId;currencyCode;toDate]

    hierarchyType:`$upper string hierarchyType;

    if[not hierarchyType in `BU`GEO;
        '"hierarchyType must be BU or GEO"];

    / ---------------------------------------------------
    / Currency
    / ---------------------------------------------------

    ccy:$[
        null currencyCode;
        `USD;
        $[
            currencyCode~`;
            `USD;
            `$upper string currencyCode
        ]
    ];

    / ---------------------------------------------------
    / Date
    / ---------------------------------------------------

    dt:$[
        null toDate;
        h "exec max T0_DATE from Actuals";
        toDate
    ];

    / ---------------------------------------------------
    / Resolve hierarchy metadata
    / ---------------------------------------------------

    closureTbl:$[
        hierarchyType=`BU;
        `BU_LEAF_CLOSURE;
        `GEO_LEAF_CLOSURE
    ];

    / BU_LEAF_CLOSURE and GEO_LEAF_CLOSURE both use NODE_ID / LEAF_NODE_ID
    parentCol:`NODE_ID;

    leafCol:`LEAF_NODE_ID;

    actualCol:$[
        hierarchyType=`BU;
        `BU_ID;
        `GEO_ID
    ];

    / ---------------------------------------------------
    / Leaf resolution
    / ---------------------------------------------------

    leafRows:remoteSelect[closureTbl;enlist (=;parentCol;hierarchyId)];

    leaves:distinct leafRows leafCol;

    if[0=count leaves;
        leaves:enlist hierarchyId];

    leaves:`s#asc leaves;

    / ---------------------------------------------------
    / Build Actuals filter
    / ---------------------------------------------------

    whereClause:enlist (=;`T0_DATE;dt);

    if[not portfolioId~`ALL;
        whereClause,:enlist (=;`PORTFOLIO_ID;enlist portfolioId)];

    whereClause,:enlist (in;actualCol;enlist leaves);

    rows:remoteSelect[`Actuals;whereClause];

    / ---------------------------------------------------
    / Empty Result
    / ---------------------------------------------------

    if[0=count rows;

        tCols:`T0,`$"T_",/:string 1+til 36;

        result:([]
            ACCOUNT_ID:`long$();
            ACCOUNT_NAME:`symbol$();
            TO_DATE:`date$());

        result:result,'flip tCols!(count tCols)#enlist `float$();

        :result;
    ];

    / ---------------------------------------------------
    / Currency Conversion
    / FX_RATE = USD value of one unit of currency
    / ---------------------------------------------------

    ccyDict:h "exec CCY_CODE!FX_RATE from CCY_Master";

    if[not ccy in key ccyDict;
        '"Unknown currency code"];

    targetRate:ccyDict ccy;

    multipliers:(ccyDict rows`CCY_CODE)%targetRate;

    rows:update
        T0:T0*multipliers,
        T_1:T_1*multipliers,
        T_2:T_2*multipliers,
        T_3:T_3*multipliers,
        T_4:T_4*multipliers,
        T_5:T_5*multipliers,
        T_6:T_6*multipliers,
        T_7:T_7*multipliers,
        T_8:T_8*multipliers,
        T_9:T_9*multipliers,
        T_10:T_10*multipliers,
        T_11:T_11*multipliers,
        T_12:T_12*multipliers,
        T_13:T_13*multipliers,
        T_14:T_14*multipliers,
        T_15:T_15*multipliers,
        T_16:T_16*multipliers,
        T_17:T_17*multipliers,
        T_18:T_18*multipliers,
        T_19:T_19*multipliers,
        T_20:T_20*multipliers,
        T_21:T_21*multipliers,
        T_22:T_22*multipliers,
        T_23:T_23*multipliers,
        T_24:T_24*multipliers,
        T_25:T_25*multipliers,
        T_26:T_26*multipliers,
        T_27:T_27*multipliers,
        T_28:T_28*multipliers,
        T_29:T_29*multipliers,
        T_30:T_30*multipliers,
        T_31:T_31*multipliers,
        T_32:T_32*multipliers,
        T_33:T_33*multipliers,
        T_34:T_34*multipliers,
        T_35:T_35*multipliers,
        T_36:T_36*multipliers
    from rows;

    / ---------------------------------------------------
    / Aggregation
    / ---------------------------------------------------

    agg:0!select
        T0:sum T0,
        T_1:sum T_1,
        T_2:sum T_2,
        T_3:sum T_3,
        T_4:sum T_4,
        T_5:sum T_5,
        T_6:sum T_6,
        T_7:sum T_7,
        T_8:sum T_8,
        T_9:sum T_9,
        T_10:sum T_10,
        T_11:sum T_11,
        T_12:sum T_12,
        T_13:sum T_13,
        T_14:sum T_14,
        T_15:sum T_15,
        T_16:sum T_16,
        T_17:sum T_17,
        T_18:sum T_18,
        T_19:sum T_19,
        T_20:sum T_20,
        T_21:sum T_21,
        T_22:sum T_22,
        T_23:sum T_23,
        T_24:sum T_24,
        T_25:sum T_25,
        T_26:sum T_26,
        T_27:sum T_27,
        T_28:sum T_28,
        T_29:sum T_29,
        T_30:sum T_30,
        T_31:sum T_31,
        T_32:sum T_32,
        T_33:sum T_33,
        T_34:sum T_34,
        T_35:sum T_35,
        T_36:sum T_36
    by ACCOUNT_ID
    from rows;

    / ---------------------------------------------------
    / Account Names
    / ---------------------------------------------------

    hasAcct:`ACCOUNT_MASTER in h "tables[]";

    if[hasAcct;
        agg:agg lj 1!h "select ACCOUNT_ID,ACCOUNT_NAME from ACCOUNT_MASTER"];

    if[not hasAcct;
        agg:update ACCOUNT_NAME:`$string ACCOUNT_ID
            from agg];

    / ---------------------------------------------------
    / Add Date
    / ---------------------------------------------------

    agg:update TO_DATE:dt from agg;

    / ---------------------------------------------------
    / Final Projection
    / ---------------------------------------------------

    :0!select
        ACCOUNT_ID,
        ACCOUNT_NAME,
        TO_DATE,
        T0,
        T_1,
        T_2,
        T_3,
        T_4,
        T_5,
        T_6,
        T_7,
        T_8,
        T_9,
        T_10,
        T_11,
        T_12,
        T_13,
        T_14,
        T_15,
        T_16,
        T_17,
        T_18,
        T_19,
        T_20,
        T_21,
        T_22,
        T_23,
        T_24,
        T_25,
        T_26,
        T_27,
        T_28,
        T_29,
        T_30,
        T_31,
        T_32,
        T_33,
        T_34,
        T_35,
        T_36
    from agg;
 }