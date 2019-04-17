# Changelog

## 1.1 RC 

### Additions and Improvements 

- Better errors for when permissioning contract is set up wrong [\#1296](https://github.com/PegaSysEng/pantheon/pull/1296) 
- Consolidate p2p node info methods [\#1288](https://github.com/PegaSysEng/pantheon/pull/1288) 
- Update permissioning smart contract interface to match updated EEA proposal [\#1287](https://github.com/PegaSysEng/pantheon/pull/1287) 
- Switch to new sync target if it exceeds the td threshold [\#1286](https://github.com/PegaSysEng/pantheon/pull/1286) 
- Fix running ATs with in-process node runner [\#1285](https://github.com/PegaSysEng/pantheon/pull/1285) 
- Simplify enode construction [\#1283](https://github.com/PegaSysEng/pantheon/pull/1283) 
- Cleanup PeerConnection interface [\#1282](https://github.com/PegaSysEng/pantheon/pull/1282) 
- Undo changes to PendingTransactions method visibility [\#1281](https://github.com/PegaSysEng/pantheon/pull/1281) 
- Use default enclave public key to generate eea_getTransactionReceipt [\#1280](https://github.com/PegaSysEng/pantheon/pull/1280) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Rollback to rocksdb 5.15.10 [\#1279](https://github.com/PegaSysEng/pantheon/pull/1279) 
- Log error when a JSON decode problem is encountered [\#1278](https://github.com/PegaSysEng/pantheon/pull/1278) 
- Create EnodeURL builder [\#1275](https://github.com/PegaSysEng/pantheon/pull/1275) 
- Keep enode nodeId stored as a BytesValue [\#1274](https://github.com/PegaSysEng/pantheon/pull/1274) 
- Feature/move subclass in pantheon command [\#1272](https://github.com/PegaSysEng/pantheon/pull/1272) 
- Expose sync mode option [\#1270](https://github.com/PegaSysEng/pantheon/pull/1270) 
- Refactor RocksDBStats [\#1266](https://github.com/PegaSysEng/pantheon/pull/1266) 
- Normalize EnodeURLs [\#1264](https://github.com/PegaSysEng/pantheon/pull/1264) 
- Build broken in Java 12 [\#1263](https://github.com/PegaSysEng/pantheon/pull/1263) 
- Make PeerDiscovertAgentTest less flakey [\#1262](https://github.com/PegaSysEng/pantheon/pull/1262) 
- Ignore extra json rpc params [\#1261](https://github.com/PegaSysEng/pantheon/pull/1261) 
- Fetch local transactions in isolation [\#1259](https://github.com/PegaSysEng/pantheon/pull/1259) 
- Update to debug trace transaction [\#1258](https://github.com/PegaSysEng/pantheon/pull/1258) 
- Use labelled timer to differentiate between rocks db metrics [\#1254](https://github.com/PegaSysEng/pantheon/pull/1254) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Migrate TransactionPool (& affiliated test) from 'core' to 'eth' [\#1251](https://github.com/PegaSysEng/pantheon/pull/1251) 
- Use single instance of Rocksdb for privacy [\#1247](https://github.com/PegaSysEng/pantheon/pull/1247) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Subscribing to sync events should receive false when in sync [\#1240](https://github.com/PegaSysEng/pantheon/pull/1240) 
- Ignore transactions from the network while behind chain head [\#1228](https://github.com/PegaSysEng/pantheon/pull/1228) 
- RocksDB Statistics in Metrics [\#1169](https://github.com/PegaSysEng/pantheon/pull/1169) 
- Add block trace RPC methods [\#1088](https://github.com/PegaSysEng/pantheon/pull/1088) (thanks to [kziemianek](https://github.com/kziemianek))

## 1.0.3

### Additions and Improvements 

- Notify of dropped messages [\#1156](https://github.com/PegaSysEng/pantheon/pull/1156) 
- Documentation updates include: 
    - Added [Permissioning Overview](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Permissioning-Overview/)
    - Added content on [Network vs Node Configuration](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Using-Configuration-File/)   
    - Updated [RAM requirements](https://docs.pantheon.pegasys.tech/en/latest/Installation/Overview/)  
    - Added [Privacy Overview](https://docs.pantheon.pegasys.tech/en/latest/Privacy/Privacy-Overview/) and [Processing Private Transactions](https://docs.pantheon.pegasys.tech/en/latest/Privacy/Private-Transaction-Processing/)
    - Renaming of Ethstats Lite Explorer to [Ethereum Lite Explorer](https://docs.pantheon.pegasys.tech/en/latest/EthStats/Lite-Block-Explorer/) (thanks to [tzapu](https://github.com/tzapu))
    - Added content on using [Truffle with Pantheon](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Truffle/)
    - Added [`droppedPendingTransactions` RPC Pub/Sub subscription](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/RPC-PubSub/#dropped-transactions) 
    - Added [`eea_*` JSON-RPC API methods](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#eea-methods)  
    - Added [architecture diagram](https://docs.pantheon.pegasys.tech/en/latest/Architecture/Overview/) 
    - Updated [permissioning CLI options](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#permissions-accounts-config-file-enabled) and [permissioned network tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Permissioned-Network/)  

### Technical Improvements 

- Choose sync target based on td rather than height [\#1256](https://github.com/PegaSysEng/pantheon/pull/1256)
- CLI ewp options [\#1246](https://github.com/PegaSysEng/pantheon/pull/1246)
- Update PantheonCommand.java [\#1245](https://github.com/PegaSysEng/pantheon/pull/1245)
- Reduce memory usage in import [\#1239](https://github.com/PegaSysEng/pantheon/pull/1239)
- Improve eea_sendRawTransaction error messages [\#1238](https://github.com/PegaSysEng/pantheon/pull/1238) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Single topic filter [\#1235](https://github.com/PegaSysEng/pantheon/pull/1235)
- Enable pipeline chain downloader for fast sync [\#1232](https://github.com/PegaSysEng/pantheon/pull/1232)
- Make contract size limit configurable [\#1227](https://github.com/PegaSysEng/pantheon/pull/1227)
- Refactor PrivacyParameters config to use builder pattern [\#1226](https://github.com/PegaSysEng/pantheon/pull/1226) (thanks to [antonydenyer](https://github.com/antonydenyer))
- Different request limits for different request types [\#1224](https://github.com/PegaSysEng/pantheon/pull/1224)
- Finish off fast sync pipeline download [\#1222](https://github.com/PegaSysEng/pantheon/pull/1222) 
- Enable fast-sync options on command line [\#1218](https://github.com/PegaSysEng/pantheon/pull/1218) 
- Replace filtering headers after the fact with calculating number to request up-front [\#1216](https://github.com/PegaSysEng/pantheon/pull/1216) 
- Support async processing while maintaining output order [\#1215](https://github.com/PegaSysEng/pantheon/pull/1215) 
- Add Unstable Options to the CLI [\#1213](https://github.com/PegaSysEng/pantheon/pull/1213) 
- Add private cluster acceptance tests [\#1211](https://github.com/PegaSysEng/pantheon/pull/1211) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Re-aligned smart contract interface to EEA client spec 477 [\#1209](https://github.com/PegaSysEng/pantheon/pull/1209) 
- Count the number of items discarded when a pipe is aborted [\#1208](https://github.com/PegaSysEng/pantheon/pull/1208) 
- Pipeline chain download - fetch and import data [\#1207](https://github.com/PegaSysEng/pantheon/pull/1207) 
- Permission provider that allows bootnodes if you have no other connections [\#1206](https://github.com/PegaSysEng/pantheon/pull/1206) 
- Cancel in-progress async operations when the pipeline is aborted [\#1205](https://github.com/PegaSysEng/pantheon/pull/1205) 
- Pipeline chain download - Checkpoints [\#1203](https://github.com/PegaSysEng/pantheon/pull/1203) 
- Push development images to public dockerhub [\#1202](https://github.com/PegaSysEng/pantheon/pull/1202) 
- Push builds of master as docker development images [\#1200](https://github.com/PegaSysEng/pantheon/pull/1200) 
- Doc CI pipeline for build and tests [\#1199](https://github.com/PegaSysEng/pantheon/pull/1199) 
- Replace the use of a disconnect listener with EthPeer.isDisconnected [\#1197](https://github.com/PegaSysEng/pantheon/pull/1197) 
- Prep chain downloader for branch by abstraction [\#1194](https://github.com/PegaSysEng/pantheon/pull/1194) 
- Maintain the state of MessageFrame in private Tx [\#1193](https://github.com/PegaSysEng/pantheon/pull/1193) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Persist private world state only if we are mining [\#1191](https://github.com/PegaSysEng/pantheon/pull/1191) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Remove SyncState from SyncTargetManager [\#1188](https://github.com/PegaSysEng/pantheon/pull/1188) 
- Acceptance tests base for smart contract node permissioning [\#1186](https://github.com/PegaSysEng/pantheon/pull/1186) 
- Fix metrics breakages [\#1185](https://github.com/PegaSysEng/pantheon/pull/1185) 
- Typo [\#1184](https://github.com/PegaSysEng/pantheon/pull/1184) (thanks to [araskachoi](https://github.com/araskachoi))
- StaticNodesParserTest to pass on Windows [\#1183](https://github.com/PegaSysEng/pantheon/pull/1183) 
- Don't mark world state as stalled until a minimum time without progress is reached [\#1179](https://github.com/PegaSysEng/pantheon/pull/1179) 
- Use header validation policy in DownloadHeaderSequenceTask [\#1172](https://github.com/PegaSysEng/pantheon/pull/1172) 
- Bond with bootnodes [\#1160](https://github.com/PegaSysEng/pantheon/pull/1160) 

## 1.0.2 

### Additions and Improvements

- Removed DB init when using `public-key` subcommand [\#1049](https://github.com/PegaSysEng/pantheon/pull/1049) 
- Output enode URL on startup [\#1137](https://github.com/PegaSysEng/pantheon/pull/1137) 
- Added Remove Peer JSON-RPC [\#1129](https://github.com/PegaSysEng/pantheon/pull/1129) 
- Added `net_enode` JSON-RPC [\#1119](https://github.com/PegaSysEng/pantheon/pull/1119) (thanks to [mbergstrand](https://github.com/mbergstrand))
- Maintain a `staticnodes.json` [\#1106](https://github.com/PegaSysEng/pantheon/pull/1106) 
- Added `tx-pool-max-size` command line parameter [\#1078](https://github.com/PegaSysEng/pantheon/pull/1078) 
- Added PendingTransactions JSON-RPC [\#1043](https://github.com/PegaSysEng/pantheon/pull/1043) (thanks to [EdwinLeeGreene](https://github.com/EdwinLeeGreene))
- Added `admin_nodeInfo` JSON-RPC [\#1012](https://github.com/PegaSysEng/pantheon/pull/1012) 
- Added `--metrics-category` CLI to only enable select metrics [\#969](https://github.com/PegaSysEng/pantheon/pull/969) 
- Documentation updates include: 
   - Updated endpoints in [Private Network Quickstart](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Private-Network-Quickstart/) (thanks to [laubai](https://github.com/laubai))
   - Updated [documentation contribution guidelines](https://github.com/PegaSysEng/pantheon/blob/master/DOC-STYLE-GUIDE.md) 
   - Added [`admin_removePeer`](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#admin_removepeer) 
   - Updated [tutorials](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Private-Clique-Network/) for printing of enode on startup 
   - Added [`txpool_pantheonTransactions`](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#txpool_pantheontransactions) 
   - Added [Transaction Pool content](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Transactions/Transaction-Pool/) 
   - Added [`tx-pool-max-size` CLI option](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#tx-pool-max-size) 
   - Updated [developer build instructions to use installDist](https://github.com/PegaSysEng/pantheon/blob/master/docs/development/running-developer-builds.md) 
   - Added [Azure quickstart tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Azure/Azure-Private-Network-Quickstart/) 
   - Enabled copy button in code blocks 
   - Added [IBFT 1.0](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/QuorumIBFT/) 
   - Added section on using [Geth attach with Pantheon](https://docs.pantheon.pegasys.tech/en/latest/JSON-RPC-API/Using-JSON-RPC-API/#geth-console)    
   - Enabled the edit link doc site to ease external doc contributions 
   - Added [EthStats docs](https://docs.pantheon.pegasys.tech/en/latest/EthStats/Overview/) (thanks to [baxy](https://github.com/baxy))
   - Updated [Postman collection](https://docs.pantheon.pegasys.tech/en/latest/JSON-RPC-API/Using-JSON-RPC-API/#postman)  
   - Added [`metrics-category` CLI option](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#metrics-category) 
   - Added information on [block time and timeout settings](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/#block-time) for IBFT 2.0 
   - Added [`admin_nodeInfo`](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#admin_nodeinfo) 
   - Added [permissions images](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Permissioning/) 
   - Added permissioning blog to [Resources](https://docs.pantheon.pegasys.tech/en/latest/Resources/Resources/) 
   - Updated [Create Permissioned Network](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Permissioned-Network/) tutorial to use `export-address` 
   - Updated [Clique](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/Clique/) and [IBFT 2.0](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/) docs to include complete genesis file  
   - Updated [Clique tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Private-Clique-Network/) to use `export-address` subcommand  
   - Added IBFT 2.0 [future message configuration options](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/#optional-configuration-options) 
 
### Technical Improvements
- Fixed so self persists to the whitelist [\#1176](https://github.com/PegaSysEng/pantheon/pull/1176)
- Fixed to add self to permissioning whitelist [\#1175](https://github.com/PegaSysEng/pantheon/pull/1175)
- Fixed permissioning issues [\#1174](https://github.com/PegaSysEng/pantheon/pull/1174)
- AdminAddPeer returns custom Json RPC error code [\#1171](https://github.com/PegaSysEng/pantheon/pull/1171)
- Periodically connect to peers from table [\#1170](https://github.com/PegaSysEng/pantheon/pull/1170)
- Improved bootnodes option error message [\#1092](https://github.com/PegaSysEng/pantheon/pull/1092) 
- Automatically restrict trailing peers while syncing [\#1167](https://github.com/PegaSysEng/pantheon/pull/1167) 
- Avoid bonding to ourselves [\#1166](https://github.com/PegaSysEng/pantheon/pull/1166) 
- Fix Push Metrics [\#1164](https://github.com/PegaSysEng/pantheon/pull/1164) 
- Synchroniser waits for new peer if best is up to date [\#1161](https://github.com/PegaSysEng/pantheon/pull/1161) 
- Don't attempt to download checkpoint headers if the number of headers is negative [\#1158](https://github.com/PegaSysEng/pantheon/pull/1158) 
- Capture metrics on Vertx event loop and worker thread queues [\#1155](https://github.com/PegaSysEng/pantheon/pull/1155) 
- Simplify node permissioning ATs [\#1153](https://github.com/PegaSysEng/pantheon/pull/1153) 
- Add metrics around discovery process [\#1152](https://github.com/PegaSysEng/pantheon/pull/1152) 
- Prevent connecting to self [\#1150](https://github.com/PegaSysEng/pantheon/pull/1150) 
- Refactoring permissioning ATs [\#1148](https://github.com/PegaSysEng/pantheon/pull/1148) 
- Added two extra Ropsten bootnodes [\#1147](https://github.com/PegaSysEng/pantheon/pull/1147) 
- Fixed TCP port handling [\#1144](https://github.com/PegaSysEng/pantheon/pull/1144) 
- Better error on bad header [\#1143](https://github.com/PegaSysEng/pantheon/pull/1143) 
- Refresh peer table while we have fewer than maxPeers connected [\#1142](https://github.com/PegaSysEng/pantheon/pull/1142) 
- Refactor jsonrpc consumption of local node permissioning controller [\#1140](https://github.com/PegaSysEng/pantheon/pull/1140) 
- Disconnect peers before the pivot block while fast syncing [\#1139](https://github.com/PegaSysEng/pantheon/pull/1139) 
- Reduce the default transaction pool size from 30,000 to 4096 [\#1136](https://github.com/PegaSysEng/pantheon/pull/1136) 
- Fail at load if static nodes not whitelisted [\#1135](https://github.com/PegaSysEng/pantheon/pull/1135) 
- Fix private transaction acceptance test [\#1134](https://github.com/PegaSysEng/pantheon/pull/1134) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Quieter exceptions when network is unreachable [\#1133](https://github.com/PegaSysEng/pantheon/pull/1133) 
- nodepermissioningcontroller used for devp2p connection filtering [\#1132](https://github.com/PegaSysEng/pantheon/pull/1132) 
- Remove duplicates from apis specified via CLI [\#1131](https://github.com/PegaSysEng/pantheon/pull/1131) 
- Synchronizer returns false if it is in sync [\#1130](https://github.com/PegaSysEng/pantheon/pull/1130) 
- Added fromHexStringStrict to check for exactly 20 byte addresses [\#1128](https://github.com/PegaSysEng/pantheon/pull/1128) 
- Fix deadlock scenario in AsyncOperationProcessor and re-enable WorldStateDownloaderTest [\#1126](https://github.com/PegaSysEng/pantheon/pull/1126) 
- Ignore WorldStateDownloaderTest [\#1125](https://github.com/PegaSysEng/pantheon/pull/1125) 
- Updated local config permissioning flags [\#1118](https://github.com/PegaSysEng/pantheon/pull/1118) 
- Pipeline Improvements [\#1117](https://github.com/PegaSysEng/pantheon/pull/1117) 
- Permissioning cli smart contract [\#1116](https://github.com/PegaSysEng/pantheon/pull/1116) 
- Adding default pending transactions value in PantheonControllerBuilder [\#1114](https://github.com/PegaSysEng/pantheon/pull/1114) 
- Fix intermittency in WorldStateDownloaderTest [\#1113](https://github.com/PegaSysEng/pantheon/pull/1113) 
- Reduce number of seen blocks and transactions Pantheon tracks [\#1112](https://github.com/PegaSysEng/pantheon/pull/1112) 
- Timeout long test [\#1111](https://github.com/PegaSysEng/pantheon/pull/1111) 
- Errorprone 2.3.3 upgrades [\#1110](https://github.com/PegaSysEng/pantheon/pull/1110) 
- Add metric to capture memory used by RocksDB table readers [\#1108](https://github.com/PegaSysEng/pantheon/pull/1108) 
- Don't allow creation of multiple gauges with the same name [\#1107](https://github.com/PegaSysEng/pantheon/pull/1107) 
- Update Peer Discovery to use NodePermissioningController [\#1105](https://github.com/PegaSysEng/pantheon/pull/1105) 
- Move starting world state download process inside WorldDownloadState [\#1104](https://github.com/PegaSysEng/pantheon/pull/1104) 
- Enable private Tx capability to Clique [\#1102](https://github.com/PegaSysEng/pantheon/pull/1102) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Enable private Tx capability to IBFT [\#1101](https://github.com/PegaSysEng/pantheon/pull/1101) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Version Upgrades [\#1100](https://github.com/PegaSysEng/pantheon/pull/1100) 
- Don't delete completed tasks from RocksDbTaskQueue [\#1099](https://github.com/PegaSysEng/pantheon/pull/1099) 
- Support flat mapping with multiple threads [\#1098](https://github.com/PegaSysEng/pantheon/pull/1098) 
- Add pipe stage name to thread while executing [\#1097](https://github.com/PegaSysEng/pantheon/pull/1097) 
- Use pipeline for world state download [\#1096](https://github.com/PegaSysEng/pantheon/pull/1096) 
- TXPool JSON RPC tweaks [\#1095](https://github.com/PegaSysEng/pantheon/pull/1095) 
- Add in-memory cache over world state download queue [\#1087](https://github.com/PegaSysEng/pantheon/pull/1087) 
- Trim default metrics [\#1086](https://github.com/PegaSysEng/pantheon/pull/1086) 
- Improve imported block log line [\#1085](https://github.com/PegaSysEng/pantheon/pull/1085) 
- Smart contract permission controller [\#1083](https://github.com/PegaSysEng/pantheon/pull/1083) 
- Add timeout when waiting for JSON-RPC, WebSocket RPC and Metrics services to stop [\#1082](https://github.com/PegaSysEng/pantheon/pull/1082) 
- Add pipeline framework to make parallel processing simpler [\#1077](https://github.com/PegaSysEng/pantheon/pull/1077) 
- Node permissioning controller [\#1075](https://github.com/PegaSysEng/pantheon/pull/1075) 
- Smart contract permission controller stub [\#1074](https://github.com/PegaSysEng/pantheon/pull/1074) 
- Expose a synchronous start method in Runner [\#1072](https://github.com/PegaSysEng/pantheon/pull/1072) 
- Changes in chain head should trigger new permissioning check for active peers [\#1071](https://github.com/PegaSysEng/pantheon/pull/1071) 
- Fix exceptions fetching metrics after world state download completes [\#1066](https://github.com/PegaSysEng/pantheon/pull/1066) 
- Accept transactions in the pool with nonce above account sender nonce [\#1065](https://github.com/PegaSysEng/pantheon/pull/1065) 
- Repair Istanbul to handle Eth/62 & Eth/63 [\#1063](https://github.com/PegaSysEng/pantheon/pull/1063) 
- Close Private Storage Provider [\#1059](https://github.com/PegaSysEng/pantheon/pull/1059) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Add labels to Pipelined tasks metrics [\#1057](https://github.com/PegaSysEng/pantheon/pull/1057) 
- Re-enable Quorum Synchronisation [\#1056](https://github.com/PegaSysEng/pantheon/pull/1056) 
- Don't log expected failures as errors [\#1054](https://github.com/PegaSysEng/pantheon/pull/1054) 
- Make findSuitablePeer abstract [\#1053](https://github.com/PegaSysEng/pantheon/pull/1053) 
- Track added at in txpool [\#1048](https://github.com/PegaSysEng/pantheon/pull/1048) 
- Fix ImportBlocksTask to only request from peers that claim to have the blocks [\#1047](https://github.com/PegaSysEng/pantheon/pull/1047) 
- Don't run the dao block validator if dao block is 0 [\#1044](https://github.com/PegaSysEng/pantheon/pull/1044) 
- Don't make unnecessary copies of data in RocksDbKeyValueStorage [\#1040](https://github.com/PegaSysEng/pantheon/pull/1040) 
- Update discovery logic to trust bootnodes only when out of sync [\#1039](https://github.com/PegaSysEng/pantheon/pull/1039) 
- Fix IndexOutOfBoundsException in DetermineCommonAncestorTask [\#1038](https://github.com/PegaSysEng/pantheon/pull/1038) 
- Add `rpc_modules` JSON-RPC [\#1036](https://github.com/PegaSysEng/pantheon/pull/1036) 
- Simple permissioning smart contract [\#1035](https://github.com/PegaSysEng/pantheon/pull/1035) 
- Refactor enodeurl to use inetaddr [\#1032](https://github.com/PegaSysEng/pantheon/pull/1032) 
- Update CLI options in mismatched genesis file message [\#1031](https://github.com/PegaSysEng/pantheon/pull/1031) 
- Remove dependence of eth.core on eth.permissioning [\#1030](https://github.com/PegaSysEng/pantheon/pull/1030) 
- Make alloc optional and provide nicer error messages when genesis config is invalid [\#1029](https://github.com/PegaSysEng/pantheon/pull/1029) 
- Handle metrics request closing before response is generated [\#1028](https://github.com/PegaSysEng/pantheon/pull/1028) 
- Change EthNetworkConfig bootnodes to always be URIs [\#1027](https://github.com/PegaSysEng/pantheon/pull/1027) 
- Avoid port conflicts in acceptance tests [\#1025](https://github.com/PegaSysEng/pantheon/pull/1025) 
- Include reference tests in jacoco [\#1024](https://github.com/PegaSysEng/pantheon/pull/1024) 
- Acceptance test - configurable gas price [\#1023](https://github.com/PegaSysEng/pantheon/pull/1023) 
- Get Internal logs and output [\#1022](https://github.com/PegaSysEng/pantheon/pull/1022) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Fix race condition in WebSocketService [\#1021](https://github.com/PegaSysEng/pantheon/pull/1021) 
- Ensure devp2p ports are written to ports file correctly [\#1020](https://github.com/PegaSysEng/pantheon/pull/1020) 
- Report the correct tcp port in PING packets when it differs from the UDP port [\#1019](https://github.com/PegaSysEng/pantheon/pull/1019) 
- Refactor transient transaction processor [\#1017](https://github.com/PegaSysEng/pantheon/pull/1017) 
- Resume world state download from existing queue [\#1016](https://github.com/PegaSysEng/pantheon/pull/1016) 
- IBFT Acceptance tests updated with longer timeout on first block [\#1015](https://github.com/PegaSysEng/pantheon/pull/1015) 
- Update IBFT acceptances tests to await first block [\#1013](https://github.com/PegaSysEng/pantheon/pull/1013) 
- Remove full hashimoto implementation as its never used [\#1011](https://github.com/PegaSysEng/pantheon/pull/1011) 
- Created SyncStatus notifications [\#1010](https://github.com/PegaSysEng/pantheon/pull/1010) 
- Address acceptance test intermittency [\#1008](https://github.com/PegaSysEng/pantheon/pull/1008) 
- Consider a world state download stalled after 100 requests with no progress [\#1007](https://github.com/PegaSysEng/pantheon/pull/1007) 
- Reduce log level when block miner is interrupted [\#1006](https://github.com/PegaSysEng/pantheon/pull/1006) 
- RunnerTest fail on Windows due to network startup timing issue [\#1005](https://github.com/PegaSysEng/pantheon/pull/1005) 
- Generate Private Contract Address [\#1004](https://github.com/PegaSysEng/pantheon/pull/1004) (thanks to [vinistevam](https://github.com/vinistevam))
- Delete the legacy pipelined import code [\#1003](https://github.com/PegaSysEng/pantheon/pull/1003) 
- Fix race condition in WebSocket AT [\#1002](https://github.com/PegaSysEng/pantheon/pull/1002) 
- Cleanup IBFT logging levels [\#995](https://github.com/PegaSysEng/pantheon/pull/995) 
- Integration Test implementation dependency for non-IntelliJ IDE [\#992](https://github.com/PegaSysEng/pantheon/pull/992) 
- Ignore fast sync and full sync tests to avoid race condition [\#991](https://github.com/PegaSysEng/pantheon/pull/991) 
- Make acceptance tests use the process based runner again [\#990](https://github.com/PegaSysEng/pantheon/pull/990) 
- RoundChangeCertificateValidator requires unique authors [\#989](https://github.com/PegaSysEng/pantheon/pull/989) 
- Make Rinkeby the benchmark chain.  [\#986](https://github.com/PegaSysEng/pantheon/pull/986) 
- Add metrics to Parallel Download pipeline [\#985](https://github.com/PegaSysEng/pantheon/pull/985) 
- Change ExpectBlockNumber to require at least the specified block number [\#981](https://github.com/PegaSysEng/pantheon/pull/981) 
- Fix benchmark compilation [\#980](https://github.com/PegaSysEng/pantheon/pull/980) 
- RPC tests can use 127.0.0.1 loopback rather than localhost [\#974](https://github.com/PegaSysEng/pantheon/pull/974) (fixes [\#956](https://github.com/PegaSysEng/pantheon/issues/956)[\#956](https://github.com/PegaSysEng/pantheon/issues/956) thanks to [glethuillier](https://github.com/glethuillier) for raising)
- Disable picocli ansi when testing [\#973](https://github.com/PegaSysEng/pantheon/pull/973) 
- Add a jmh benchmark for WorldStateDownloader [\#972](https://github.com/PegaSysEng/pantheon/pull/972) 
- Gradle dependency for JMH annotation, for IDEs that aren't IntelliJ \(… [\#971](https://github.com/PegaSysEng/pantheon/pull/971) 
- Separate download state tracking from WorldStateDownloader [\#967](https://github.com/PegaSysEng/pantheon/pull/967)
- Gradle dependency for JMH annotation, for IDEs that aren't IntelliJ [\#966](https://github.com/PegaSysEng/pantheon/pull/966) 
- Truffle HDwallet Web3 1.0 [\#964](https://github.com/PegaSysEng/pantheon/pull/964) 
- Add missing JavaDoc tags in JSONToRLP [\#963](https://github.com/PegaSysEng/pantheon/pull/963) 
- Only import block if it isn't already on the block chain [\#962](https://github.com/PegaSysEng/pantheon/pull/962) 
- CLI stack traces when debugging [\#960](https://github.com/PegaSysEng/pantheon/pull/960) 
- Create peer discovery packets on a worker thread [\#955](https://github.com/PegaSysEng/pantheon/pull/955) 
- Remove start functionality from IbftController and IbftBlockHeightMan… [\#952](https://github.com/PegaSysEng/pantheon/pull/952) 
- Cleanup IBFT executors [\#951](https://github.com/PegaSysEng/pantheon/pull/951) 
- Single threaded world state persistence [\#950](https://github.com/PegaSysEng/pantheon/pull/950) 
- Fix version number on master [\#946](https://github.com/PegaSysEng/pantheon/pull/946) 
- Change automatic benchmark  [\#945](https://github.com/PegaSysEng/pantheon/pull/945) 
- Eliminate redundant header validation [\#943](https://github.com/PegaSysEng/pantheon/pull/943) 
- RocksDbQueue Threading Tweaks [\#940](https://github.com/PegaSysEng/pantheon/pull/940) 
- Validate DAO block [\#939](https://github.com/PegaSysEng/pantheon/pull/939) 
- Complete Private Transaction Processor [\#938](https://github.com/PegaSysEng/pantheon/pull/938) (thanks to [iikirilov](https://github.com/iikirilov))
- Add metrics for netty queue length [\#932](https://github.com/PegaSysEng/pantheon/pull/932) 
- Update GetNodeDataFromPeerTask to return a map [\#931](https://github.com/PegaSysEng/pantheon/pull/931) 

## 1.0.1

Public key address export subcommand was missing in 1.0 release.

### Additions and Improvements
- Added `public-key export-address` subcommand [\#888](https://github.com/PegaSysEng/pantheon/pull/888)
- Documentation update for the [`public-key export-address`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#public-key) subcommand.
- Updated [IBFT 2.0 overview](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/) to include use of `rlp encode` command and information on setting IBFT 2.0 properties to achieve your desired block time.

## 1.0 

### Additions and Improvements 
- [IBFT 2.0](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/) 
- [Permissioning](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Permissioning/) 
- [JSON-RPC Authentication](https://docs.pantheon.pegasys.tech/en/latest/JSON-RPC-API/Authentication/) 
- Added `rlp encode` subcommand [\#965](https://github.com/PegaSysEng/pantheon/pull/965)
- Method to reload permissions file [\#834](https://github.com/PegaSysEng/pantheon/pull/834) 
- Added rebind mitigation for Websockets. [\#905](https://github.com/PegaSysEng/pantheon/pull/905) 
- Support genesis contract code [\#749](https://github.com/PegaSysEng/pantheon/pull/749) (thanks to [kziemianek](https://github.com/kziemianek)). Fixes issue [\#662](https://github.com/PegaSysEng/pantheon/issues/662). 
- Documentation updates include: 
  - Added details on [port configuration](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Networking/#port-configuration)    
  - Added [Resources page](https://docs.pantheon.pegasys.tech/en/latest/Resources/Resources/) linking to Pantheon blog posts and webinars 
  - Added [JSON-RPC Authentication](https://docs.pantheon.pegasys.tech/en/latest/JSON-RPC-API/Authentication/)  
  - Added [tutorial to create permissioned network](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Permissioned-Network/) 
  - Added [Permissioning](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Permissioning/) content 
  - Added [Permissioning API methods](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#permissioning-methods) 
  - Added [tutorial to create Clique private network](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Private-Clique-Network/)
  - Added [tutorial to create IBFT 2.0 private network](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-IBFT-Network/)
        
### Technical Improvements 
- RoundChangeCertificateValidator requires unique authors [\#997](https://github.com/PegaSysEng/pantheon/pull/997)
- RPC tests can use 127.0.0.1 loopback rather than localhost [\#979](https://github.com/PegaSysEng/pantheon/pull/979)
- Integration Test implementation dependency for non-IntelliJ IDE [\#978](https://github.com/PegaSysEng/pantheon/pull/978)
- Only import block if it isn't already on the block chain [\#977](https://github.com/PegaSysEng/pantheon/pull/977)
- Disable picocli ansi when testing [\#975](https://github.com/PegaSysEng/pantheon/pull/975)
- Create peer discovery packets on a worker thread [\#961](https://github.com/PegaSysEng/pantheon/pull/961)
- Removed Orion snapshot dependency [\#933](https://github.com/PegaSysEng/pantheon/pull/933) 
- Use network ID instead of chain ID in MainnetPantheonController. [\#929](https://github.com/PegaSysEng/pantheon/pull/929) 
- Propagate new block messages to other clients in a worker thread [\#928](https://github.com/PegaSysEng/pantheon/pull/928) 
- Parallel downloader should stop on puts if requested. [\#927](https://github.com/PegaSysEng/pantheon/pull/927) 
- Permission config file location and option under docker [\#925](https://github.com/PegaSysEng/pantheon/pull/925) 
- Fixed potential stall in world state download [\#922](https://github.com/PegaSysEng/pantheon/pull/922) 
- Refactoring to introduce deleteOnExit\(\) for temp files [\#920](https://github.com/PegaSysEng/pantheon/pull/920) 
- Reduce "Received transactions message" log from debug to trace [\#919](https://github.com/PegaSysEng/pantheon/pull/919) 
- Handle PeerNotConnected exceptions when sending wire keep alives [\#918](https://github.com/PegaSysEng/pantheon/pull/918) 
- admin_addpeers: error if node not whitelisted [\#917](https://github.com/PegaSysEng/pantheon/pull/917) 
- Expose the Ibft MiningCoordinator [\#916](https://github.com/PegaSysEng/pantheon/pull/916) 
- Check perm api against perm cli [\#915](https://github.com/PegaSysEng/pantheon/pull/915) 
- Update metrics when completing a world state request with existing data [\#914](https://github.com/PegaSysEng/pantheon/pull/914) 
- Improve RocksDBQueue dequeue performance [\#913](https://github.com/PegaSysEng/pantheon/pull/913) 
- Error when removing bootnodes from nodes whitelist [\#912](https://github.com/PegaSysEng/pantheon/pull/912) 
- Incremental Optimization\(s\) on BlockBroadcaster [\#911](https://github.com/PegaSysEng/pantheon/pull/911) 
- Check permissions CLI dependencies [\#909](https://github.com/PegaSysEng/pantheon/pull/909) 
- Limit the number of times we retry peer discovery interactions [\#908](https://github.com/PegaSysEng/pantheon/pull/908) 
- IBFT to use VoteTallyCache [\#907](https://github.com/PegaSysEng/pantheon/pull/907) 
- Add metric to expose number of inflight world state requests [\#906](https://github.com/PegaSysEng/pantheon/pull/906) 
- Bootnodes not on whitelist - improve errors [\#904](https://github.com/PegaSysEng/pantheon/pull/904) 
- Make chain download cancellable [\#901](https://github.com/PegaSysEng/pantheon/pull/901) 
- Enforce accounts must start with 0x [\#900](https://github.com/PegaSysEng/pantheon/pull/900) 
- When picking fast sync pivot block, use the peer with the best total difficulty [\#899](https://github.com/PegaSysEng/pantheon/pull/899) 
- Process world state download data on a worker thread [\#898](https://github.com/PegaSysEng/pantheon/pull/898) 
- CLI mixin help [\#895](https://github.com/PegaSysEng/pantheon/pull/895) ([macfarla](https://github.com/macfarla))
- Use absolute datapath instead of relative. [\#894](https://github.com/PegaSysEng/pantheon/pull/894). Fixes issue [\#854](https://github.com/PegaSysEng/pantheon/issues/854).
- Fix task queue so that the updated failure count for requests is stored [\#893](https://github.com/PegaSysEng/pantheon/pull/893) 
- Fix authentication header [\#891](https://github.com/PegaSysEng/pantheon/pull/891) 
- Reorganize eth tasks [\#890](https://github.com/PegaSysEng/pantheon/pull/890) 
- Unit tests of BlockBroadcaster [\#887](https://github.com/PegaSysEng/pantheon/pull/887) 
- Fix authentication file validation errors [\#886](https://github.com/PegaSysEng/pantheon/pull/886) 
- Fixing file locations under docker [\#885](https://github.com/PegaSysEng/pantheon/pull/885) 
- Handle exceptions properly in EthScheduler [\#884](https://github.com/PegaSysEng/pantheon/pull/884)
- More bootnodes for goerli [\#880](https://github.com/PegaSysEng/pantheon/pull/880)
- Rename password hash command [\#879](https://github.com/PegaSysEng/pantheon/pull/879) 
- Add metrics for EthScheduler executors [\#878](https://github.com/PegaSysEng/pantheon/pull/878) 
- Disconnect peer removed from node whitelist [\#877](https://github.com/PegaSysEng/pantheon/pull/877) 
- Reduce logging noise from invalid peer discovery packets and handshaking [\#876](https://github.com/PegaSysEng/pantheon/pull/876) 
- Detect stalled world state downloads [\#875](https://github.com/PegaSysEng/pantheon/pull/875) 
- Limit size of Ibft future message buffer [\#873](https://github.com/PegaSysEng/pantheon/pull/873) 
- Ibft2: Replace NewRound with extended Proposal [\#872](https://github.com/PegaSysEng/pantheon/pull/872) 
- Fixed admin_addPeer to periodically check maintained connections [\#871](https://github.com/PegaSysEng/pantheon/pull/871) 
- WebSocket method permissions [\#870](https://github.com/PegaSysEng/pantheon/pull/870)
- Select new pivot block when world state becomes unavailable [\#869](https://github.com/PegaSysEng/pantheon/pull/869) 
- Introduce FutureUtils to reduce duplicated code around CompletableFuture [\#868](https://github.com/PegaSysEng/pantheon/pull/868) 
- Implement world state cancel [\#867](https://github.com/PegaSysEng/pantheon/pull/867) 
- Renaming authentication configuration file CLI command [\#865](https://github.com/PegaSysEng/pantheon/pull/865) 
- Break out RoundChangeCertificate validation [\#864](https://github.com/PegaSysEng/pantheon/pull/864) 
- Disconnect peers where the common ancestor is before our fast sync pivot [\#862](https://github.com/PegaSysEng/pantheon/pull/862) 
- Initial scaffolding for block propagation [\#860](https://github.com/PegaSysEng/pantheon/pull/860) 
- Fix NullPointerException when determining fast sync pivot [\#859](https://github.com/PegaSysEng/pantheon/pull/859) 
- Check for invalid token [\#856](https://github.com/PegaSysEng/pantheon/pull/856) 
- Moving NodeWhitelistController to permissioning package [\#855](https://github.com/PegaSysEng/pantheon/pull/855) 
- Fix state download race condition by creating a TaskQueue API [\#853](https://github.com/PegaSysEng/pantheon/pull/853) 
- Changed separator in JSON RPC permissions [\#852](https://github.com/PegaSysEng/pantheon/pull/852) 
- WebSocket acceptance tests now can use WebSockets [\#851](https://github.com/PegaSysEng/pantheon/pull/851) 
- IBFT notifies EthPeer when remote node has a better block [\#849](https://github.com/PegaSysEng/pantheon/pull/849) 
- Support resuming fast-sync downloads [\#848](https://github.com/PegaSysEng/pantheon/pull/848) 
- Tweak Fast Sync Config [\#847](https://github.com/PegaSysEng/pantheon/pull/847) 
- RPC authentication configuration validation + tests. [\#846](https://github.com/PegaSysEng/pantheon/pull/846) 
- Tidy-up FastSyncState persistence [\#845](https://github.com/PegaSysEng/pantheon/pull/845) 
- Do parallel extract signatures in the parallel block importer. [\#844](https://github.com/PegaSysEng/pantheon/pull/844) 
- Fix 'the Input Is Too Long' Error on Windows [\#843](https://github.com/PegaSysEng/pantheon/pull/843) (thanks to [glethuillier](https://github.com/glethuillier)). Fixes issue [\#839](https://github.com/PegaSysEng/pantheon/issues/839). 
- Remove unnecessary sleep [\#842](https://github.com/PegaSysEng/pantheon/pull/842) 
- Shutdown improvements [\#841](https://github.com/PegaSysEng/pantheon/pull/841)
- Speed up shutdown time [\#838](https://github.com/PegaSysEng/pantheon/pull/838) 
- Add metrics to world state downloader [\#837](https://github.com/PegaSysEng/pantheon/pull/837) 
- Store pivot block header [\#836](https://github.com/PegaSysEng/pantheon/pull/836) 
- Clique should use beneficiary of zero on epoch blocks [\#833](https://github.com/PegaSysEng/pantheon/pull/833) 
- Clique should ignore proposals for address 0 [\#831](https://github.com/PegaSysEng/pantheon/pull/831) 
- Fix intermittency in FullSyncDownloaderTest [\#830](https://github.com/PegaSysEng/pantheon/pull/830) 
- Added the authentication service to the WebSocket service [\#829](https://github.com/PegaSysEng/pantheon/pull/829)
- Extract creation and init of ProtocolContext into a re-usable class [\#828](https://github.com/PegaSysEng/pantheon/pull/828) 
- Prevent duplicate commit seals in ibft header [\#827](https://github.com/PegaSysEng/pantheon/pull/827) 
- Validate Ibft vanity data length [\#826](https://github.com/PegaSysEng/pantheon/pull/826) 
- Refactored json rpc authentication to be provided as a service [\#825](https://github.com/PegaSysEng/pantheon/pull/825) 
- Handle unavailable world states [\#824](https://github.com/PegaSysEng/pantheon/pull/824) 
- Password in JWT payload [\#823](https://github.com/PegaSysEng/pantheon/pull/823)
- Homogenize error messages when required parameters are set [\#822](https://github.com/PegaSysEng/pantheon/pull/822) ([glethuillier](https://github.com/glethuillier)). Fixes issue [\#821](https://github.com/PegaSysEng/pantheon/issues/821). 
- Set remote peer chain head to parent of block received in NEW\_BLOCK\_MESSAGE [\#819](https://github.com/PegaSysEng/pantheon/pull/819) 
- Peer disconnects should not result in stack traces [\#818](https://github.com/PegaSysEng/pantheon/pull/818) 
- Abort previous builds [\#817](https://github.com/PegaSysEng/pantheon/pull/817) 
- Parallel build stages [\#816](https://github.com/PegaSysEng/pantheon/pull/816) 
- JWT authentication for JSON-RPC [\#815](https://github.com/PegaSysEng/pantheon/pull/815) 
- Log errors that occur while finding a common ancestor [\#814](https://github.com/PegaSysEng/pantheon/pull/814) 
- Shuffled log levels [\#813](https://github.com/PegaSysEng/pantheon/pull/813)
- Prevent duplicate IBFT messages being processed by state machine [\#811](https://github.com/PegaSysEng/pantheon/pull/811) 
- Fix Orion startup ports [\#810](https://github.com/PegaSysEng/pantheon/pull/810) 
- Commit world state continuously [\#809](https://github.com/PegaSysEng/pantheon/pull/809) 
- Improve block propagation time [\#808](https://github.com/PegaSysEng/pantheon/pull/808) 
- JSON-RPC authentication cli options & acceptance tests [\#807](https://github.com/PegaSysEng/pantheon/pull/807) 
- Remove privacy not supported warning [\#806](https://github.com/PegaSysEng/pantheon/pull/806) (thanks to [vinistevam](https://github.com/vinistevam))
- Wire up Private Transaction Processor [\#805](https://github.com/PegaSysEng/pantheon/pull/805) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Apply a limit to the number of responses in RespondingEthPeer.respondWhile [\#803](https://github.com/PegaSysEng/pantheon/pull/803) 
- Avoid requesting empty block bodies from the network. [\#802](https://github.com/PegaSysEng/pantheon/pull/802)
- Handle partial responses to get receipts requests [\#801](https://github.com/PegaSysEng/pantheon/pull/801) 
- Rename functions in Ibft MessageValidator [\#800](https://github.com/PegaSysEng/pantheon/pull/800) 
- Upgrade GoogleJavaFormat to 1.7 [\#795](https://github.com/PegaSysEng/pantheon/pull/795) 
- Minor refactorings of IntegrationTest infrastructure [\#786](https://github.com/PegaSysEng/pantheon/pull/786) 
- Rework Ibft MessageValidatorFactory [\#785](https://github.com/PegaSysEng/pantheon/pull/785) 
- Rework IbftRoundFactory [\#784](https://github.com/PegaSysEng/pantheon/pull/784) 
- Rename artefacts to artifacts within IBFT [\#782](https://github.com/PegaSysEng/pantheon/pull/782) 
- Rename TerminatedRoundArtefacts to PreparedRoundArtefacts [\#781](https://github.com/PegaSysEng/pantheon/pull/781)
- Rename Ibft MessageFactory methods [\#779](https://github.com/PegaSysEng/pantheon/pull/779) 
- Update WorldStateDownloader to only filter out known code requests [\#777](https://github.com/PegaSysEng/pantheon/pull/777) 
- Multiple name options only search for the longest one [\#776](https://github.com/PegaSysEng/pantheon/pull/776)
- Move ethTaskTimer to abstract root [\#775](https://github.com/PegaSysEng/pantheon/pull/775) 
- Parallel Block importer [\#774](https://github.com/PegaSysEng/pantheon/pull/774) 
- Wait for a peer with an estimated chain height before selecting a pivot block [\#772](https://github.com/PegaSysEng/pantheon/pull/772) 
- Randomly perform full validation when fast syncing blocks [\#770](https://github.com/PegaSysEng/pantheon/pull/770) 
- IBFT Message rework, piggybacking blocks on msgs. [\#769](https://github.com/PegaSysEng/pantheon/pull/769) 
- EthScheduler additions [\#767](https://github.com/PegaSysEng/pantheon/pull/767)
- Fixing node whitelist isPermitted check [\#766](https://github.com/PegaSysEng/pantheon/pull/766) 
- Eth/63 labels [\#764](https://github.com/PegaSysEng/pantheon/pull/764) 
- Permissioning whitelist persistence. [\#763](https://github.com/PegaSysEng/pantheon/pull/763) 
- Created message validators for NewRound and RoundChange [\#760](https://github.com/PegaSysEng/pantheon/pull/760) 
- Add tests for FastSyncChainDownloader as a whole [\#758](https://github.com/PegaSysEng/pantheon/pull/758) 
- Flatten IBFT Message API [\#757](https://github.com/PegaSysEng/pantheon/pull/757)
- Added TerminatedRoundArtefacts [\#756](https://github.com/PegaSysEng/pantheon/pull/756) 
- Fix thread names in EthScheduler to include the thread number [\#755](https://github.com/PegaSysEng/pantheon/pull/755) 
- Separate round change reception from RoundChangeCertificate [\#754](https://github.com/PegaSysEng/pantheon/pull/754) 
- JSON-RPC authentication login [\#753](https://github.com/PegaSysEng/pantheon/pull/753) 
- Spilt Ibft MessageValidator into components [\#752](https://github.com/PegaSysEng/pantheon/pull/752) 
- Ensure first checkpoint headers is always in local blockchain for FastSyncCheckpointHeaderManager [\#750](https://github.com/PegaSysEng/pantheon/pull/750) 
- Refactored permissioning components to be Optional. [\#747](https://github.com/PegaSysEng/pantheon/pull/747) 
- Integrate rocksdb-based queue into WorldStateDownloader [\#746](https://github.com/PegaSysEng/pantheon/pull/746) 
- Generify orion to enclave [\#745](https://github.com/PegaSysEng/pantheon/pull/745) (thanks to [vinistevam](https://github.com/vinistevam))
- Moved IBFT Message factory to use wrapped message types [\#744](https://github.com/PegaSysEng/pantheon/pull/744) 
- Handle timeouts when requesting checkpoint headers correctly [\#743](https://github.com/PegaSysEng/pantheon/pull/743)
- Update RoundChangeManager to use flattened message [\#742](https://github.com/PegaSysEng/pantheon/pull/742) 
- Handle validation failures when fast importing blocks [\#741](https://github.com/PegaSysEng/pantheon/pull/741) 
- Updated IbftRound and RoundState APIs to use wrapped messages [\#740](https://github.com/PegaSysEng/pantheon/pull/740)
- Exception handling [\#739](https://github.com/PegaSysEng/pantheon/pull/739)
- Upgrade dependency versions and build cleanup [\#738](https://github.com/PegaSysEng/pantheon/pull/738)
- Update IbftBlockHeigntManager to accept new message types. [\#737](https://github.com/PegaSysEng/pantheon/pull/737) 
- Error response handling for permissions APIs [\#736](https://github.com/PegaSysEng/pantheon/pull/736) 
- IPV6 bootnodes don't work [\#735](https://github.com/PegaSysEng/pantheon/pull/735) 
- Updated to use tags of pantheon build rather than another repo [\#734](https://github.com/PegaSysEng/pantheon/pull/734) 
- Log milestones at startup and other minor logging improvements [\#733](https://github.com/PegaSysEng/pantheon/pull/733) 
- Create wrapper types for Ibft Signed messages [\#731](https://github.com/PegaSysEng/pantheon/pull/731)
- Ibft to uniquely ID messages by their hash [\#730](https://github.com/PegaSysEng/pantheon/pull/730) 
- Rename ibftrevised to ibft2 [\#722](https://github.com/PegaSysEng/pantheon/pull/722)
- Limit ibft msg queues [\#704](https://github.com/PegaSysEng/pantheon/pull/704) 
- Implement privacy precompiled contract [\#696](https://github.com/PegaSysEng/pantheon/pull/696) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Integration of RecursivePeerRefreshState and PeerDiscoveryController [\#420](https://github.com/PegaSysEng/pantheon/pull/420) 

## 0.9.1

Built and compatible with with JDK8. 

## 0.9

### Breaking Changes to Command Line

Breaking changes have been made to the command line options in v0.9 to improve usability. Many v0.8 command line options no longer work. 

The [documentation](https://docs.pantheon.pegasys.tech/en/latest/) has been updated throughout to use the changed command line options and the [command line reference](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/) documents the changed options. 

| Previous Option                     | New Option                                                                                                                                                                                                                                  | Change                            |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| `--config`                          | [`--config-file`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#config-file)                                                                                                                                  | Renamed                          |
| `--datadir`                         | [`--data-path`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#data-path)                                                                                                                                      | Renamed                          |
| `--dev-mode`                        | [`--network=dev`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#network)                                                                                                                                     | Replaced by `--network` option   |
| `--genesis`                         | [`--genesis-file`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#genesis-file)                                                                                                                                | Renamed                          |
| `--goerli`                          | [`--network=goerli`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#network)                                                                                                                                  | Replaced by `--network` option   |
| `--metrics-listen=<HOST:PORT>`      | [`--metrics-host=<HOST>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#metrics-host) and [`--metrics-port=<PORT>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#metrics-port) | Split into host and port options |
| `--miner-extraData`                 | [`--miner-extra-data`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#miner-extra-data)                                                                                                                       | Renamed                          |
| `--miner-minTransactionGasPriceWei` | [`--min-gas-price`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#min-gas-price)                                                                                                                              | Renamed                          |
| `--no-discovery`                    | [`--discovery-enabled`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#discovery-enabled)                                                                                                                      | Replaced                         |
| `--node-private-key`                | [`--node-private-key-file`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#node-private-key-file)                                                                                                              | Renamed                          |
| `--ottoman`                         | N/A                                                                                                                                                                                                                                         | Removed                          |
| `--p2p-listen=<HOST:PORT>`          | [`--p2p-host=<HOST>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#p2p-host) and [`--p2p-port=<PORT>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#p2p-port) | Split into host and port options |
| `--rinkeby`                         | [`--network=rinkeby`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#network)                                                                                                                                     | Replaced by `--network` option   |
| `--ropsten`                         | [`--network=ropsten`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#network)                                                                                                                                     | Replaced by `--network` option   |
| `--rpc-enabled`                     | [` --rpc-http-enabled`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-http-enabled)| Renamed| 
| `--rpc-listen=<HOST:PORT>`          | [`--rpc-http-host=<HOST>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-http-host) and [`--rpc-http-port=<PORT>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-http-port) | Split into host and port options |
| `--rpc-api`                         | [`--rpc-http-api`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-http-api)| Renamed |
| `--rpc-cors-origins`                | [`--rpc-http-cors-origins`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-http-cors-origins) | Renamed | 
| `--ws-enabled`                      | [`--rpc-ws-enabled`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-ws-enabled)  | Renamed | 
| `--ws-api`                          | [`--rpc-ws-api`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-ws-api) | Renamed|
| `--ws-listen=<HOST:PORT>`           | [`--rpc-ws-host=<HOST>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-ws-host) and [`--rpc-ws-port=<PORT>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-ws-port) | Split into host and port options |
| `--ws-refresh-delay`                | [`--rpc-ws-refresh-delay`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-ws-refresh-delay)|Renamed| 

| Previous Subcommand                 | New Subcommand                                                                                                                                                                                                                  | Change                            |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| `pantheon import <block-file>`      | [`pantheon blocks import --from=<block-file>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#blocks)                                                                                            | Renamed                          |
| `pantheon export-pub-key <key-file>`| [`pantheon public-key export --to=<key-file>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#public-key)                                                                                                      | Renamed                          |


### Private Network Quickstart 

The Private Network Quickstart has been moved from the `pantheon` repository to the `pantheon-quickstart` 
repository. The [Private Network Quickstart tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Private-Network-Quickstart/) 
has been updated to use the moved quickstart.

### Additions and Improvements 

- `--network=goerli` supports relaunch of Görli testnet [\#717](https://github.com/PegaSysEng/pantheon/pull/717)
- TOML authentication provider [\#689](https://github.com/PegaSysEng/pantheon/pull/689) 
- Metrics Push Gateway Options [\#678](https://github.com/PegaSysEng/pantheon/pull/678)
- Additional logging details for IBFT 2.0 [\#650](https://github.com/PegaSysEng/pantheon/pull/650) 
- Permissioning config TOML file [\#643](https://github.com/PegaSysEng/pantheon/pull/643)
- Added metrics Prometheus Push Gateway Support [\#638](https://github.com/PegaSysEng/pantheon/pull/638) 
- Clique and IBFT not enabled by default in RPC APIs [\#635](https://github.com/PegaSysEng/pantheon/pull/635)
- Added `admin_addPeer` JSON-RPC API method [\#622](https://github.com/PegaSysEng/pantheon/pull/622)
- Implemented `--p2p-enabled` configuration item [\#619](https://github.com/PegaSysEng/pantheon/pull/619) 
- Command options and commands renaming [\#618](https://github.com/PegaSysEng/pantheon/pull/618) 
- Added IBFT get pending votes [\#603](https://github.com/PegaSysEng/pantheon/pull/603) 
- Implement Petersburg hardfork [\#601](https://github.com/PegaSysEng/pantheon/pull/601) 
- Added private transaction abstraction [\#592](https://github.com/PegaSysEng/pantheon/pull/592) (thanks to [iikirilov](https://github.com/iikirilov))
- Added privacy command line commands [\#584](https://github.com/PegaSysEng/pantheon/pull/584) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Documentation updates include:
  - Updated [Private Network Quickstart tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Private-Network-Quickstart/) 
    to use quickstart in `pantheon-quickstart` repository and indicate that the quickstart is not supported on Windows.
  - Added IBFT 2.0 [content](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/) and [JSON RPC API methods](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#ibft-20-methods). 
  - Added [consensus protocols content](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/Comparing-PoA/).
  - Added content on [events and logs](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Events-and-Logs/), and [using filters](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Accessing-Logs-Using-JSON-RPC/). 
  - Added content on integrating with [Prometheus Push Gateway](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Debugging/#running-prometheus-with-pantheon-in-push-mode)
  
### Technical Improvements 

- Download receipts during fast sync and import without processing transactions [\#701](https://github.com/PegaSysEng/pantheon/pull/701) 
- Removed CLI options for `--nodes-whitelist` and `--accounts-whitelist` [\#694](https://github.com/PegaSysEng/pantheon/pull/694) 
- Delegate `getRootCause` through to Guava's implementation [\#692](https://github.com/PegaSysEng/pantheon/pull/692) 
- Benchmark update [\#691](https://github.com/PegaSysEng/pantheon/pull/691)
- Implement chain download for fast sync [\#690](https://github.com/PegaSysEng/pantheon/pull/690) 
- Allow missing accounts to create zero-cost transactions [\#685](https://github.com/PegaSysEng/pantheon/pull/685) 
- Node private key location should be fixed under docker [\#684](https://github.com/PegaSysEng/pantheon/pull/684) 
- Parallel Processing File Import Performance [\#683](https://github.com/PegaSysEng/pantheon/pull/683) 
- Integrate actual `WorldStateDownloader` with the fast sync work flow [\#682](https://github.com/PegaSysEng/pantheon/pull/682) 
- Removed `--max-trailing-peers` option [\#680](https://github.com/PegaSysEng/pantheon/pull/680)
- Enabled warning on CLI dependent options [\#679](https://github.com/PegaSysEng/pantheon/pull/679)
- Update WorldStateDownloader run\(\) interface to accept header [\#677](https://github.com/PegaSysEng/pantheon/pull/677)
- Fixed Difficulty calculator [\#663](https://github.com/PegaSysEng/pantheon/pull/663)
- `discovery-enabled` option refactoring [\#661](https://github.com/PegaSysEng/pantheon/pull/661)
- Update orion default port approach [\#660](https://github.com/PegaSysEng/pantheon/pull/660)
- Extract out generic parts of Downloader [\#659](https://github.com/PegaSysEng/pantheon/pull/659)
- Start world downloader [\#658](https://github.com/PegaSysEng/pantheon/pull/658)
- Create a simple `WorldStateDownloader` [\#657](https://github.com/PegaSysEng/pantheon/pull/657) 
- Added handling for when p2p is disabled [\#655](https://github.com/PegaSysEng/pantheon/pull/655)
- Enabled command line configuration for privacy precompiled contract address [\#653](https://github.com/PegaSysEng/pantheon/pull/653) (thanks to [Puneetha17](https://github.com/Puneetha17))
- IBFT transmitted packets are logged by gossiper [\#652](https://github.com/PegaSysEng/pantheon/pull/652)
- `admin_addPeer` acceptance test [\#651](https://github.com/PegaSysEng/pantheon/pull/651) 
- Added `p2pEnabled` configuration to `ProcessPantheonNodeRunner` [\#649](https://github.com/PegaSysEng/pantheon/pull/649) 
- Added description to automatic benchmarks [\#646](https://github.com/PegaSysEng/pantheon/pull/646) 
- Added `network` option [\#645](https://github.com/PegaSysEng/pantheon/pull/645) 
- Remove OrionConfiguration [\#644](https://github.com/PegaSysEng/pantheon/pull/644) (thanks to [Puneetha17](https://github.com/Puneetha17))
- IBFT Json Acceptance tests [\#634](https://github.com/PegaSysEng/pantheon/pull/634)
- Upgraded build image to one that contains libsodium [\#632](https://github.com/PegaSysEng/pantheon/pull/632) 
- Command line fixes [\#630](https://github.com/PegaSysEng/pantheon/pull/630) 
- Consider peer count insufficient until minimum peers for fast sync are connected [\#629](https://github.com/PegaSysEng/pantheon/pull/629) 
- Build tweaks [\#628](https://github.com/PegaSysEng/pantheon/pull/628)
- IBFT ensure non-validator does not partake in consensus [\#627](https://github.com/PegaSysEng/pantheon/pull/627)
- Added ability in acceptance tests to set up a node with `--no-discovery` [\#624](https://github.com/PegaSysEng/pantheon/pull/624)
- Gossip integration test [\#623](https://github.com/PegaSysEng/pantheon/pull/623)
- Removed quickstart code and CI pipeline [\#616](https://github.com/PegaSysEng/pantheon/pull/616) 
- IBFT Integration Tests - Spurious Behaviour [\#615](https://github.com/PegaSysEng/pantheon/pull/615) 
- Refactoring for more readable IBFT IT [\#614](https://github.com/PegaSysEng/pantheon/pull/614) 
- Start of fast sync downloader [\#613](https://github.com/PegaSysEng/pantheon/pull/613) 
- Split `IbftProcessor` into looping and event processing [\#612](https://github.com/PegaSysEng/pantheon/pull/612) 
- IBFT Int Test - changed `TestContextFactory` to a builder [\#611](https://github.com/PegaSysEng/pantheon/pull/611) 
- Discard prior round change msgs [\#610](https://github.com/PegaSysEng/pantheon/pull/610) 
- `IbftGetValidatorsByBlockHash` added to json factory [\#607](https://github.com/PegaSysEng/pantheon/pull/607) 
- IBFT Validator RPCs to return list of strings [\#606](https://github.com/PegaSysEng/pantheon/pull/606) 
- Update Benchmark [\#605](https://github.com/PegaSysEng/pantheon/pull/605) 
- Remove db package and move classes to more appropriate locations [\#599](https://github.com/PegaSysEng/pantheon/pull/599) 
- Added `GetReceiptsFromPeerTask` [\#598](https://github.com/PegaSysEng/pantheon/pull/598) 
- Added `GetNodeDataFromPeerTask` [\#597](https://github.com/PegaSysEng/pantheon/pull/597) 
- Fixed deprecation warnings [\#596](https://github.com/PegaSysEng/pantheon/pull/596) 
- IBFT Integration Tests - Future Height [\#591](https://github.com/PegaSysEng/pantheon/pull/591) 
- Added `getNodeData` to `EthPeer` to enable requesting node data [\#589](https://github.com/PegaSysEng/pantheon/pull/589) 
- `Blockcreator` to use `parentblock` specified at constuction [\#588](https://github.com/PegaSysEng/pantheon/pull/588) 
- Support responding to `GetNodeData` requests [\#587](https://github.com/PegaSysEng/pantheon/pull/587) 
- IBFT validates block on proposal reception [\#583](https://github.com/PegaSysEng/pantheon/pull/583) 
- Rework `NewRoundValidator` tests [\#582](https://github.com/PegaSysEng/pantheon/pull/582) 
- IBFT split extra data validation rule into components [\#581](https://github.com/PegaSysEng/pantheon/pull/581) 
- Allow attached rules to be flagged `light` [\#580](https://github.com/PegaSysEng/pantheon/pull/580) 
- Split Block Validation from Importing [\#579](https://github.com/PegaSysEng/pantheon/pull/579) 
- Refactor `RoundChangeManager` creation [\#578](https://github.com/PegaSysEng/pantheon/pull/578) 
- Add `-SNAPSHOT` postfix to version [\#577](https://github.com/PegaSysEng/pantheon/pull/577) 
- IBFT - prevent proposed block being imported twice [\#576](https://github.com/PegaSysEng/pantheon/pull/576) 
- Version upgrades [\#571](https://github.com/PegaSysEng/pantheon/pull/571) 
- Tests that CLI options are disabled under docker [\#566](https://github.com/PegaSysEng/pantheon/pull/566) 
- Renamed IBFT networking classes [\#555](https://github.com/PegaSysEng/pantheon/pull/555) 
- Removed dead code from the consensus package [\#554](https://github.com/PegaSysEng/pantheon/pull/554) 
- Prepared private transaction support [\#538](https://github.com/PegaSysEng/pantheon/pull/538) (thanks to [iikirilov](https://github.com/iikirilov))

## 0.8.5

Indefinitely delays the roll-out of Constantinople on Ethereum Mainnet due to a [potential security issue](https://blog.ethereum.org/2019/01/15/security-alert-ethereum-constantinople-postponement/) detected.

## Additions and Improvements
- Remove Constantinople fork block [\#574](https://github.com/PegaSysEng/pantheon/pull/574)

## Technical Improvements
- Rename IBFT message packages [\#568](https://github.com/PegaSysEng/pantheon/pull/568)


## 0.8.4

### Docker Image

If you have been running a node using the v0.8.3 Docker image, the node was not saving data to the 
specified [data directory](https://docs.pantheon.pegasys.tech/en/latest/Getting-Started/Run-Docker-Image/#data-directory),
or referring to the custom [configuration file](https://docs.pantheon.pegasys.tech/en/latest/Getting-Started/Run-Docker-Image/#custom-configuration-file)
or [genesis file](https://docs.pantheon.pegasys.tech/en/latest/Getting-Started/Run-Docker-Image/#custom-genesis-file). 

To recover the node key and data directory from the Docker container:
`docker cp <container>:/opt/pantheon/key <destination_file>`
`docker cp <container>:/opt/pantheon/database <destination_directory>` 

Where `container` is the name or ID of the Docker container containing the Pantheon node. 

The container can be running or stopped when you copy the key and data directory. If your node was 
fully synchronized to MainNet, the data directory will be ~2TB.  

When restarting your node with the v0.8.4 Docker image:

* Save the node key in the [`key` file](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Node-Keys/#node-private-key) in the data 
    directory or specify the location using the [`--node-private-key` option](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Node-Keys/#specifying-a-custom-node-private-key-file).  
* Specify the `<destination_directory` as a [volume for the data directory](https://docs.pantheon.pegasys.tech/en/latest/Getting-Started/Run-Docker-Image/#data-directory). 

### Bug Fixes
- Fixing default resource locations inside docker [\#529](https://github.com/PegaSysEng/pantheon/pull/529)
- NewRoundMessageValidator ignores Round Number when comparing blocks [\#523](https://github.com/PegaSysEng/pantheon/pull/523) 
- Fix Array Configurable command line options [\#514](https://github.com/PegaSysEng/pantheon/pull/514) 

## Additions and Improvements
- RocksDB Metrics [\#531](https://github.com/PegaSysEng/pantheon/pull/531) 
- Added `ibft_getValidatorsByBlockHash` JSON RPC [\#519](https://github.com/PegaSysEng/pantheon/pull/519) 
- Expose metrics to Prometheus [\#506](https://github.com/PegaSysEng/pantheon/pull/506)
- Added `ibft_getValidatorsByBlockNumber` [\#499](https://github.com/PegaSysEng/pantheon/pull/499) 
- Added `Roadmap.md` file. [\#494](https://github.com/PegaSysEng/pantheon/pull/494) 
- Added JSON RPC `eth hashrate` method. [\#488](https://github.com/PegaSysEng/pantheon/pull/488) 
- Account whitelist API [\#487](https://github.com/PegaSysEng/pantheon/pull/487) 
- Added nodes whitelist JSON-RPC APIs [\#476](https://github.com/PegaSysEng/pantheon/pull/476) 
- Added account whitelisting [\#460](https://github.com/PegaSysEng/pantheon/pull/460) 
- Added configurable refresh delay for SyncingSubscriptionService on start up [\#383](https://github.com/PegaSysEng/pantheon/pull/383) 
- Added the Command Line Style Guide  [\#530](https://github.com/PegaSysEng/pantheon/pull/530)
- Documentation updates include:
  * Migrated to new [documentation site](https://docs.pantheon.pegasys.tech/en/latest/)  
  * Added [configuration file content](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Using-Configuration-File/)
  * Added [tutorial to create private network](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Private-Network/)
  * Added content on [enabling non-default APIs](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/)
  
## Technical Improvements

-  Updated `--bootnodes` command option to take zero arguments [\#548](https://github.com/PegaSysEng/pantheon/pull/548) 
- IBFT Integration Testing - Local Node is proposer [\#527](https://github.com/PegaSysEng/pantheon/pull/527) 
- Remove vertx from discovery tests [\#539](https://github.com/PegaSysEng/pantheon/pull/539)
- IBFT Integration testing - Round Change [\#537](https://github.com/PegaSysEng/pantheon/pull/537) 
- NewRoundMessageValidator creates RoundChangeValidator with correct value [\#518](https://github.com/PegaSysEng/pantheon/pull/518) 
- Remove time dependency from BlockTimer tests [\#513](https://github.com/PegaSysEng/pantheon/pull/513) 
- Gradle 5.1 [\#512](https://github.com/PegaSysEng/pantheon/pull/512) 
- Metrics measurement adjustment [\#511](https://github.com/PegaSysEng/pantheon/pull/511) 
- Metrics export for import command. [\#509](https://github.com/PegaSysEng/pantheon/pull/509) 
- IBFT Integration test framework [\#502](https://github.com/PegaSysEng/pantheon/pull/502) 
- IBFT message gossiping [\#501](https://github.com/PegaSysEng/pantheon/pull/501) 
- Remove non-transactional mutation from KeyValueStore [\#500](https://github.com/PegaSysEng/pantheon/pull/500) 
- Ensured that the blockchain queries class handles optionals better. [\#486](https://github.com/PegaSysEng/pantheon/pull/486) 
- IBFT mining acceptance test [\#483](https://github.com/PegaSysEng/pantheon/pull/483) 
- Set base directory name to be lowercase in building.md [\#474](https://github.com/PegaSysEng/pantheon/pull/474) (Thanks to [Matthalp](https://github.com/Matthalp))
- Moved admin\_peers to Admin API group [\#473](https://github.com/PegaSysEng/pantheon/pull/473) 
- Nodes whitelist acceptance test [\#472](https://github.com/PegaSysEng/pantheon/pull/472) 
- Rework RoundChangeManagerTest to not reuse validators [\#469](https://github.com/PegaSysEng/pantheon/pull/469) 
- Ignore node files to support truffle. [\#467](https://github.com/PegaSysEng/pantheon/pull/467) 
- IBFT pantheon controller [\#461](https://github.com/PegaSysEng/pantheon/pull/461) 
- IBFT Round to update internal state on reception of NewRound Message [\#451](https://github.com/PegaSysEng/pantheon/pull/451) 
- Update RoundChangeManager correctly create its message validator [\#450](https://github.com/PegaSysEng/pantheon/pull/450) 
- Use seconds for block timer time unit [\#445](https://github.com/PegaSysEng/pantheon/pull/445) 
- IBFT controller and future msgs handling [\#431](https://github.com/PegaSysEng/pantheon/pull/431) 
- Allow IBFT Round to be created using PreparedCert [\#429](https://github.com/PegaSysEng/pantheon/pull/429) 
- Added MessageValidatorFactory [\#425](https://github.com/PegaSysEng/pantheon/pull/425) 
- Inround payload [\#423](https://github.com/PegaSysEng/pantheon/pull/423) 
- Updated IbftConfig Fields [\#422](https://github.com/PegaSysEng/pantheon/pull/422) 
- Repair IbftBlockCreator and add tests [\#421](https://github.com/PegaSysEng/pantheon/pull/421) 
- Make Pantheon behave as a submodule [\#419](https://github.com/PegaSysEng/pantheon/pull/419) 
- Ibft Height Manager [\#418](https://github.com/PegaSysEng/pantheon/pull/418) 
- Ensure bootnodes are a subset of node whitelist [\#414](https://github.com/PegaSysEng/pantheon/pull/414) 
- IBFT Consensus Round Classes [\#405](https://github.com/PegaSysEng/pantheon/pull/405) 
- IBFT message payload tests [\#404](https://github.com/PegaSysEng/pantheon/pull/404) 
- Validate enodeurl syntax from command line [\#403](https://github.com/PegaSysEng/pantheon/pull/403) 
- Update errorprone [\#401](https://github.com/PegaSysEng/pantheon/pull/401) 
- IBFT round change manager [\#393](https://github.com/PegaSysEng/pantheon/pull/393) 
- IBFT RoundState [\#392](https://github.com/PegaSysEng/pantheon/pull/392) 
- Move Block data generator test helper to test support package [\#391](https://github.com/PegaSysEng/pantheon/pull/391) 
- IBFT message tests [\#367](https://github.com/PegaSysEng/pantheon/pull/367) 

## 0.8.3 

### Breaking Change to JSON RPC-API 

From v0.8.3, incoming HTTP requests are only accepted from hostnames specified using the `--host-whitelist` command-line option. If not specified, the default value for `--host-whitelist` is `localhost`. 

If using the URL `http://127.0.0.1` to make JSON-RPC calls, use `--host-whitelist` to specify the hostname `127.0.0.1` or update the hostname to `localhost`. 

If your application publishes RPC ports, specify the hostnames when starting Pantheon. For example:  

```json
pantheon --host-whitelist=example.com
```
 
Specify `*` or `all` for `--host-whitelist` to effectively disable host protection and replicate pre-v0.8.3 behavior. This is not recommended for production code. 

### Bug Fixes 

- Repair Clique Proposer Selection [\#339](https://github.com/PegaSysEng/pantheon/pull/339) 
- High TX volume swamps block processing [\#337](https://github.com/PegaSysEng/pantheon/pull/337) 
- Check if the connectFuture has completed successfully [\#293](https://github.com/PegaSysEng/pantheon/pull/293) 
- Switch back to Xerial Snappy Library [\#284](https://github.com/PegaSysEng/pantheon/pull/284) 
- ShortHex of 0 should be '0x0', not '0x' [\#272](https://github.com/PegaSysEng/pantheon/pull/272) 
- Fix pantheon CLI default values infinite loop [\#266](https://github.com/PegaSysEng/pantheon/pull/266) 

### Additions and Improvements 

- Added `--nodes-whitelist` parameter to CLI and NodeWhitelistController [\#346](https://github.com/PegaSysEng/pantheon/pull/346) 
- Discovery wiring for `--node-whitelist` [\#365](https://github.com/PegaSysEng/pantheon/pull/365) 
- Plumb in three more metrics [\#344](https://github.com/PegaSysEng/pantheon/pull/344) 
- `ProposerSelection` to support multiple IBFT implementations [\#307](https://github.com/PegaSysEng/pantheon/pull/307) 
- Configuration to support IBFT original and revised [\#306](https://github.com/PegaSysEng/pantheon/pull/306) 
- Added host whitelist for JSON-RPC. [**Breaking Change**](#breaking-change-to-json-rpc-api) [\#295](https://github.com/PegaSysEng/pantheon/pull/295) 
- Reduce `Block creation processed cancelled` log message to debug [\#294](https://github.com/PegaSysEng/pantheon/pull/294) 
- Implement iterative peer search [\#268](https://github.com/PegaSysEng/pantheon/pull/268) 
- Added RLP enc/dec for PrePrepare, Commit and NewRound messages [\#200](https://github.com/PegaSysEng/pantheon/pull/200) 
- IBFT block mining [\#169](https://github.com/PegaSysEng/pantheon/pull/169) 
- Added `--goerli` CLI option [\#370](https://github.com/PegaSysEng/pantheon/pull/370) (Thanks to [@Nashatyrev](https://github.com/Nashatyrev))
- Begin capturing metrics to better understand Pantheon's behaviour [\#326](https://github.com/PegaSysEng/pantheon/pull/326)
- Documentation updates include:
   * Added Coding Conventions [\#342](https://github.com/PegaSysEng/pantheon/pull/342)
   * Reorganised [Installation documentation](https://github.com/PegaSysEng/pantheon/wiki/Installation) and added [Chocolatey installation](https://github.com/PegaSysEng/pantheon/wiki/Install-Binaries#windows-with-chocolatey) for Windows 
   * Reorganised [JSON-RPC API documentation](https://github.com/PegaSysEng/pantheon/wiki/JSON-RPC-API)
   * Updated [RPC Pub/Sub API documentation](https://github.com/PegaSysEng/pantheon/wiki/RPC-PubSub)
   
### Technical Improvements 

- Extracted non-Docker CLI parameters to picoCLI mixin. [\#323](https://github.com/PegaSysEng/pantheon/pull/323) 
- IBFT preprepare to validate round matches block [\#329](https://github.com/PegaSysEng/pantheon/pull/329) 
- Fix acceptance test [\#324](https://github.com/PegaSysEng/pantheon/pull/324) 
- Added the `IbftFinalState` [\#385](https://github.com/PegaSysEng/pantheon/pull/385) 
- Constantinople Fork Block [\#382](https://github.com/PegaSysEng/pantheon/pull/382) 
- Fix `pantheon.cli.PantheonCommandTest` test on Windows [\#380](https://github.com/PegaSysEng/pantheon/pull/380) 
- JDK smoke testing is being configured differently now [\#374](https://github.com/PegaSysEng/pantheon/pull/374) 
- Re-enable clique AT [\#373](https://github.com/PegaSysEng/pantheon/pull/373) 
- Ignoring acceptance test [\#372](https://github.com/PegaSysEng/pantheon/pull/372) 
- Changes to support Gradle 5.0 [\#371](https://github.com/PegaSysEng/pantheon/pull/371) 
- Clique: Prevent out of turn blocks interrupt in-turn mining [\#364](https://github.com/PegaSysEng/pantheon/pull/364) 
- Time all tasks [\#361](https://github.com/PegaSysEng/pantheon/pull/361) 
- Rework `VoteTallyCache` to better represent purpose [\#360](https://github.com/PegaSysEng/pantheon/pull/360) 
- Add an `UNKNOWN` `DisconnectReason` [\#359](https://github.com/PegaSysEng/pantheon/pull/359) 
- New round validation [\#353](https://github.com/PegaSysEng/pantheon/pull/353) 
- Update get validators for block hash test to start from block 1 [\#352](https://github.com/PegaSysEng/pantheon/pull/352) 
- Idiomatic Builder Pattern [\#345](https://github.com/PegaSysEng/pantheon/pull/345) 
- Revert `Repair Clique Proposer Selection` \#339 - Breaks Görli testnet [\#343](https://github.com/PegaSysEng/pantheon/pull/343) 
- No fixed ports in tests [\#340](https://github.com/PegaSysEng/pantheon/pull/340) 
- Update clique acceptance test genesis file to use correct clique property names [\#338](https://github.com/PegaSysEng/pantheon/pull/338) 
- Supporting list of addresses in logs subscription [\#336](https://github.com/PegaSysEng/pantheon/pull/336) 
- Render handler exception to `System.err` instead of `.out` [\#334](https://github.com/PegaSysEng/pantheon/pull/334) 
- Renamed IBFT message classes [\#333](https://github.com/PegaSysEng/pantheon/pull/333) 
- Add additional RLP tests [\#332](https://github.com/PegaSysEng/pantheon/pull/332) 
- Downgrading spotless to 3.13.0 to fix threading issues [\#325](https://github.com/PegaSysEng/pantheon/pull/325) 
- `eth_getTransactionReceipt` acceptance test [\#322](https://github.com/PegaSysEng/pantheon/pull/322) 
- Upgrade vertx to 3.5.4 [\#316](https://github.com/PegaSysEng/pantheon/pull/316) 
- Round change validation [\#315](https://github.com/PegaSysEng/pantheon/pull/315) 
- Basic IBFT message validators [\#314](https://github.com/PegaSysEng/pantheon/pull/314) 
- Minor repairs to clique block scheduling [\#308](https://github.com/PegaSysEng/pantheon/pull/308) 
- Dependencies Version upgrade [\#303](https://github.com/PegaSysEng/pantheon/pull/303)
- Build multiple JVM [\#301](https://github.com/PegaSysEng/pantheon/pull/301) 
- Smart contract acceptance test [\#296](https://github.com/PegaSysEng/pantheon/pull/296) 
- Fixing WebSocket error response [\#292](https://github.com/PegaSysEng/pantheon/pull/292) 
- Reword error messages following exceptions during mining [\#291](https://github.com/PegaSysEng/pantheon/pull/291) 
- Clique acceptance tests [\#290](https://github.com/PegaSysEng/pantheon/pull/290) 
- Delegate creation of additional JSON-RPC methods to the PantheonController [\#289](https://github.com/PegaSysEng/pantheon/pull/289) 
- Remove unnecessary `RlpInput` and `RlpOutput` classes [\#287](https://github.com/PegaSysEng/pantheon/pull/287) 
- Remove `RlpUtils` [\#285](https://github.com/PegaSysEng/pantheon/pull/285) 
- Enabling previously ignored acceptance tests [\#282](https://github.com/PegaSysEng/pantheon/pull/282) 
- IPv6 peers [\#281](https://github.com/PegaSysEng/pantheon/pull/281) 
- IPv6 Bootnode [\#280](https://github.com/PegaSysEng/pantheon/pull/280) 
- Acceptance test for `getTransactionReceipt` JSON-RPC method [\#278](https://github.com/PegaSysEng/pantheon/pull/278)
- Inject `StorageProvider` into `PantheonController` instances [\#259](https://github.com/PegaSysEng/pantheon/pull/259)

## 0.8.2
 
### Removed
 - Removed `import-blockchain` command because nothing exports to the required format yet (PR [\#223](https://github.com/PegaSysEng/pantheon/pull/223))

### Bug Fixes
 - `io.netty.util.internal.OutOfDirectMemoryError` errors by removing reference counting from network messages.
 - Log spam: endless loop in `nioEventLoopGroup` ([#248](https://github.com/PegaSysEng/pantheon/issues/248) thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#261](https://github.com/PegaSysEng/pantheon/pull/261))
 - Rinkeby import can stall with too many fragments ([#228](https://github.com/PegaSysEng/pantheon/issues/228) thanks to [@steffenkux](https://github.com/steffenkux) and [@5chdn](https://github.com/5chdn) for reporting) (PR [#255](https://github.com/PegaSysEng/pantheon/pull/255))
 - Clique incorrectly used the chain ID instead of the network ID in ETH status messages (PR [#209](https://github.com/PegaSysEng/pantheon/pull/209))
 - Gradle deprecation warnings (PR [#246](https://github.com/PegaSysEng/pantheon/pull/246) with thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Consensus issue on Ropsten:
    - Treat output length as a maximum length for CALL operations (PR [#236](https://github.com/PegaSysEng/pantheon/pull/236))
    - ECRec precompile should return empty instead of 32 zero bytes when the input is invalid (PR [#227](https://github.com/PegaSysEng/pantheon/pull/227))
 - File name too long error while building from source ([#215](https://github.com/PegaSysEng/pantheon/issues/215) thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#221](https://github.com/PegaSysEng/pantheon/pull/221))
 - Loop syntax in `runPantheonPrivateNetwork.sh` (PR [#237](https://github.com/PegaSysEng/pantheon/pull/237) thanks to [@matt9ucci](https://github.com/matt9ucci))
 - Fix `CompressionException: Snappy decompression failed` errors ([#251](https://github.com/PegaSysEng/pantheon/issues/251) thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#274](https://github.com/PegaSysEng/pantheon/pull/274))

### Additions and Improvements
 - Added `--ropsten` command line argument to make syncing to Ropsten easier ([#186](https://github.com/PegaSysEng/pantheon/issues/186)) (PR [#197](https://github.com/PegaSysEng/pantheon/pull/197) with thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Enabled constantinople in `--dev-mode` (PR [#256](https://github.com/PegaSysEng/pantheon/pull/256))
 - Supported Constantinople with Clique ([#245](https://github.com/PegaSysEng/pantheon/issues/245) thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#250](https://github.com/PegaSysEng/pantheon/pull/250), PR [#247](https://github.com/PegaSysEng/pantheon/pull/247))
 - Implemented `eth_chainId` JSON-RPC method ([#206](https://github.com/PegaSysEng/pantheon/issues/206)) (PR [#219](https://github.com/PegaSysEng/pantheon/pull/219))
 - Updated client version to be ethstats friendly (PR [#258](https://github.com/PegaSysEng/pantheon/pull/258))
 - Added `--node-private-key` option to allow nodekey file to be specified separately to data directory ([#233](https://github.com/PegaSysEng/pantheon/issues/233) thanks to [@peterbroadhurst](https://github.com/peterbroadhurst) for requesting)  (PR [#234](https://github.com/PegaSysEng/pantheon/pull/234))
 - Added `--banned-nodeids` option to prevent connection to specific nodes (PR [#254](https://github.com/PegaSysEng/pantheon/pull/254))
 - Send client quitting disconnect message to peers on shutdown ([#184](https://github.com/PegaSysEng/pantheon/issues/184)) (PR [#253](https://github.com/PegaSysEng/pantheon/pull/253))
 - Improved error message for port conflict error (PR [#232](https://github.com/PegaSysEng/pantheon/pull/232))
 - Improved documentation by adding the following pages:
    * [Getting Started](https://github.com/PegaSysEng/pantheon/wiki/Getting-Started)
    * [Network ID and Chain ID](https://github.com/PegaSysEng/pantheon/wiki/NetworkID-And-ChainID)
    * [Node Keys](https://github.com/PegaSysEng/pantheon/wiki/Node-Keys) 
    * [Networking](https://github.com/PegaSysEng/pantheon/wiki/Networking) 
    * [Accounts for Testing](https://github.com/PegaSysEng/pantheon/wiki/Accounts-for-Testing) 
    * [Logging](https://github.com/PegaSysEng/pantheon/wiki/Logging) 
    * [Proof of Authority](https://github.com/PegaSysEng/pantheon/wiki/Proof-of-Authority) 
    * [Passing JVM Options](https://github.com/PegaSysEng/pantheon/wiki/Passing-JVM-Options) 


 ### Technical Improvements
 - Upgraded Ethereum reference tests to 6.0 beta 2. (thanks to [@jvirtanen](https://github.com/jvirtanen) for the initial upgrade to beta 1)
 - Set Java compiler default encoding to UTF-8 (PR [#238](https://github.com/PegaSysEng/pantheon/pull/238) thanks to [@matt9ucci](https://github.com/matt9ucci))
 - Removed duplicate code defining default JSON-RPC APIs (PR [#218](https://github.com/PegaSysEng/pantheon/pull/218) thanks to [@matt9ucci](https://github.com/matt9ucci))
 - Improved code for parsing config (PRs [#208](https://github.com/PegaSysEng/pantheon/pull/208), [#209](https://github.com/PegaSysEng/pantheon/pull/209))
 - Use `java.time.Clock` in favour of a custom Clock interface (PR [#220](https://github.com/PegaSysEng/pantheon/pull/220))
 - Improve modularity of storage systems (PR [#211](https://github.com/PegaSysEng/pantheon/pull/211), [#207](https://github.com/PegaSysEng/pantheon/pull/207))
 - Treat JavaDoc warnings as errors (PR [#171](https://github.com/PegaSysEng/pantheon/pull/171))
 - Add benchmark for `BlockHashOperation `as a template for benchmarking other EVM operations (PR [#203](https://github.com/PegaSysEng/pantheon/pull/203))
 - Added unit tests for `EthBlockNumber` ([#189](https://github.com/PegaSysEng/pantheon/issues/189)) (PR [#195](https://github.com/PegaSysEng/pantheon/pull/195) thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Code style improvements (PR [#196](https://github.com/PegaSysEng/pantheon/pull/196) thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Added unit tests for `Web3ClientVersion` ([#191](https://github.com/PegaSysEng/pantheon/issues/191)) (PR [#194](https://github.com/PegaSysEng/pantheon/pull/194) with thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Removed RLPUtils from `RawBlockIterator` (PR [#179](https://github.com/PegaSysEng/pantheon/pull/179))
 - Replace the JNI based snappy library with a pure-Java version (PR [#257](https://github.com/PegaSysEng/pantheon/pull/257))
