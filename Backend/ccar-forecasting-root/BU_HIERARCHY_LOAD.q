/ BU_HIERARCHY - fast load from BU_HIERARCHY1.csv using the native CSV parser (0:)
/ Replaces BU_HIERARCHY_INSERT.q, whose 1MB single-line literal is very slow for q to parse.
/
/ Column types: BU_ID J, BU_NAME * (string), LEVEL_ID J, BU_ID_LEVEL1..10 J  -> "J*JJJJJJJJJJJ"
/ Empty CSV cells become 0Nj (long null), same as the old insert script.
/
/ Usage (pick one):
/  A) CSV is on the KDB server itself - run in the server's q session:
/       q)\l BU_HIERARCHY_LOAD.q
/     (set csvPath below to the CSV's location on the server)
/
/  B) CSV is on your machine, KDB is remote - run in a LOCAL q session:
/       q)\l BU_HIERARCHY_LOAD.q
/       q)pushTo[`:192.168.11.200:5173:test:test@123]
/     The table is parsed locally and sent to the server as one IPC message.

csvPath:`:ccar-core/src/main/resources/BU_HIERARCHY1.csv

BU_HIERARCHY:("J*JJJJJJJJJJJ";enlist",") 0: csvPath

-1 "BU_HIERARCHY loaded: ",string[count BU_HIERARCHY]," rows";

/ push the locally-loaded table to a remote KDB process (option B)
pushTo:{[conn]
  h:hopen (conn;30000);                       / 30s connect timeout
  h(set;`BU_HIERARCHY;BU_HIERARCHY);
  n:h"count BU_HIERARCHY";
  hclose h;
  -1 "Pushed BU_HIERARCHY to remote: ",string[n]," rows";
  n }
