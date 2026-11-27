/ Simple test functions to verify remote (IPC) calls work.
/ Load on the server after json_kdb_table.q:  \l test_remote.q
h:hopen `:192.168.11.200:5173

/ Return all rows from BU_HIER
getBuHier:{[]
    :h" select from BU_HIER";
 }

/ Return the row count of BU_HIER (quick connectivity check)
getBuHierCount:{[]
    :h "count BU_HIER";
 }
