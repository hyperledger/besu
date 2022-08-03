# Changelog

## 22.7.0

### Additions and Improvements
- Deprecation warning for Ropsten, Rinkeby, Kiln [#4173](https://github.com/hyperledger/besu/pull/4173)

### Bug Fixes 

- Fixes previous known issue [#3890](https://github.com/hyperledger/besu/issues/3890)from RC3 requiring a restart post-merge to continue correct transaction handling.
- Stop producing stack traces when a get headers response only contains the range start header [#4189](https://github.com/hyperledger/besu/pull/4189)
- Upgrade Spotless to 6.8.0 [#4195](https://github.com/hyperledger/besu/pull/4195)
- Upgrade Gradle to 7.5 [#4196](https://github.com/hyperledger/besu/pull/4196)

## 22.7.0-RC3

### Known/Outstanding issues:
- Besu requires a restart post-merge to re-enable remote transaction processing [#3890](https://github.com/hyperledger/besu/issues/3890)

### Additions and Improvements
- Engine API: Change expiration time for JWT tokens to 60s [#4168](https://github.com/hyperledger/besu/pull/4168)
- Sepolia mergeNetSplit block [#4158](https://github.com/hyperledger/besu/pull/4158)
- Goerli TTD [#4160](https://github.com/hyperledger/besu/pull/4160) 
- Several logging improvements 

### Bug Fixes
- Allow to set any value for baseFeePerGas in the genesis file [#4177](https://github.com/hyperledger/besu/pull/4177)
- Fix for stack overflow when searching for TTD block [#4169](https://github.com/hyperledger/besu/pull/4169)
- Fix for chain stuck issue [#4175](https://github.com/hyperledger/besu/pull/4175)

### Download links
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.7.0-RC3/besu-22.7.0-RC3.tar.gz / sha256: `6a1ee89c82db9fa782d34733d8a8c726670378bcb71befe013da48d7928490a6`
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.7.0-RC3/besu-22.7.0-RC3.zip / sha256: `5de22445ab2a270cf33e1850cd28f1946442b7104738f0d1ac253a009c53414e`

## 22.7.0-RC2

### Additions and Improvements
- Add a block to the bad blocks if it did not descend from the terminal block [#4080](https://github.com/hyperledger/besu/pull/4080)
- Backward sync exception improvements [#4092](https://github.com/hyperledger/besu/pull/4092)
- Remove block header checks during backward sync, since they will be always performed during block import phase [#4098](https://github.com/hyperledger/besu/pull/4098)
- Optimize the backward sync retry strategy [#4095](https://github.com/hyperledger/besu/pull/4095)
- Add support for jemalloc library to better handle rocksdb memory consumption [#4126](https://github.com/hyperledger/besu/pull/4126)
- RocksDB configuration changes to improve performance. [#4132](https://github.com/hyperledger/besu/pull/4132)

### Bug Fixes
- Changed max message size in the p2p layer to 16.7MB from 10MB to improve peering performance [#4120](https://github.com/hyperledger/besu/pull/4120)
- Fixes for parent stateroot mismatch when using Bonsai storage mode (please report if you encounter this bug on this version) [#4094](https://github.com/hyperledger/besu/pull/4094)
- Above Bonsai related fixes have addressed situations where the event log was not indexed properly [#3921](https://github.com/hyperledger/besu/pull/3921)
- Fixes related to backward sync and reorgs [#4097](https://github.com/hyperledger/besu/pull/4097)
- Checkpoint sync with more merge friendly checkpoint blocks [#4085](https://github.com/hyperledger/besu/pull/4085)
- Fixes around RocksDB performance and memory usage [#4128](https://github.com/hyperledger/besu/pull/4128)
- Fix for RPC performance parallelization to improve RPC performance under heavy load [#3959](https://github.com/hyperledger/besu/pull/3959)
- Fix for post-Merge peering after PoW is removed in our logic for weighting peers [#4116](https://github.com/hyperledger/besu/pull/4116)
- Various logging changes to improve UX- Return the correct latest valid hash in case of bad block when calling engine methods [#4056](https://github.com/hyperledger/besu/pull/4056)
- Add a PoS block header rule to check that the current block is more recent than its parent [#4066](https://github.com/hyperledger/besu/pull/4066)
- Fixed a trie log layer issue on bonsai during reorg [#4069](https://github.com/hyperledger/besu/pull/4069)
- Fix transition protocol schedule to return the pre Merge schedule when reorg pre TTD [#4078](https://github.com/hyperledger/besu/pull/4078)
- Remove hash to sync from the queue only if the sync step succeeds [#4105](https://github.com/hyperledger/besu/pull/4105)
- The build process runs successfully even though the system language is not English [#4102](https://github.com/hyperledger/besu/pull/4102)
- Avoid starting or stopping the BlockPropagationManager more than once [#4122](https://github.com/hyperledger/besu/pull/4122)

### Download links
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.7.0-RC2/besu-22.7.0-RC2.tar.gz / sha256: `befe15b893820c9c6451a74fd87b41f555ff28561494b3bebadd5da5c7ce25d3`
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.7.0-RC2/besu-22.7.0-RC2.zip / sha256: `d56c340f5982b882fbecca2697ca72a5bbefe0e978d2d4504211f012e2242a81`

## 22.7.0-RC1

### Additions and Improvements
- Do not require a minimum block height when downloading headers or blocks [#3911](https://github.com/hyperledger/besu/pull/3911)
- When on PoS the head can be only be updated by ForkchoiceUpdate [#3994](https://github.com/hyperledger/besu/pull/3994)
- Version information available in metrics [#3997](https://github.com/hyperledger/besu/pull/3997)
- Add TTD and DNS to Sepolia config [#4024](https://github.com/hyperledger/besu/pull/4024)
- Return `type` with value `0x0` when serializing legacy transactions [#4027](https://github.com/hyperledger/besu/pull/4027)
- Ignore `ForkchoiceUpdate` if `newHead` is an ancestor of the chain head [#4055](https://github.com/hyperledger/besu/pull/4055)

### Bug Fixes
- Fixed a snapsync issue that can sometimes block the healing step [#3920](https://github.com/hyperledger/besu/pull/3920)
- Support free gas networks in the London fee market [#4003](https://github.com/hyperledger/besu/pull/4003)
- Limit the size of outgoing eth subprotocol messages.  [#4034](https://github.com/hyperledger/besu/pull/4034)
- Fixed a state root mismatch issue on bonsai that may appear occasionally [#4041](https://github.com/hyperledger/besu/pull/4041)

### Download links
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.7.0-RC1/besu-22.7.0-RC1.tar.gz / sha256: `60ad8b53402beb62c24ad791799d9cfe444623a58f6f6cf1d0728459cb641e63`
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.7.0-RC1/besu-22.7.0-RC1.zip / sha256: `7acfb3a73382bf70f6337e83cb7e9e472b4e5a9da88c5ed2fbd9e82fcf2046dc`

## 22.4.3

### Additions and Improvements
- \[EXPERIMENTAL\] Add checkpoint sync `--sync-mode="X_CHECKPOINT"` [#3849](https://github.com/hyperledger/besu/pull/3849)
- Support `finalized` and `safe` as tags for the block parameter in RPC APIs [#3950](https://github.com/hyperledger/besu/pull/3950)
- Added verification of payload attributes in ForkchoiceUpdated [#3837](https://github.com/hyperledger/besu/pull/3837)
- Add support for Gray Glacier hardfork [#3961](https://github.com/hyperledger/besu/issues/3961)

### Bug Fixes
- alias engine-rpc-port parameter with the former rpc param name [#3958](https://github.com/hyperledger/besu/pull/3958)

## 22.4.2

### Additions and Improvements
- Engine API Update: Replace deprecated INVALID_TERMINAL_BLOCK with INVALID last valid hash 0x0 [#3882](https://github.com/hyperledger/besu/pull/3882)
- Deprecate experimental merge flag and engine-rpc-enabled flag [#3875](https://github.com/hyperledger/besu/pull/3875)
- Update besu-native dependencies to 0.5.0 for linux arm64 support
- Update ropsten TTD to 100000000000000000000000

### Bug Fixes
- Stop backward sync if genesis block has been reached [#3869](https://github.com/hyperledger/besu/pull/3869)
- Allow to backward sync to request headers back to last finalized block if present or genesis [#3888](https://github.com/hyperledger/besu/pull/3888)

### Download link
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.4.2/besu-22.4.2.zip / sha256: `e8e9eb7e3f544ecefeec863712fb8d3f6a569c9d70825a4ed2581c596db8fd45`
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.4.2/besu-22.4.2.tar.gz / sha256: `9db0c37440cb56bcf671b8de13e0ecb6235171a497bdad91020b8c4a9dac2a27`

## 22.4.1

### Additions and Improvements
- GraphQL - allow null log topics in queries which match any topic [#3662](https://github.com/hyperledger/besu/pull/3662)
- multi-arch docker builds for amd64 and arm64 [#2954](https://github.com/hyperledger/besu/pull/2954)
- Filter Netty native lib errors likewise the pure Java implementation [#3807](https://github.com/hyperledger/besu/pull/3807)
- Add ropsten terminal total difficulty config [#3871](https://github.com/hyperledger/besu/pull/3871)

### Bug Fixes
- Stop the BlockPropagationManager when it receives the TTD reached event [#3809](https://github.com/hyperledger/besu/pull/3809)
- Correct getMixHashOrPrevRandao to return the value present in the block header [#3839](https://github.com/hyperledger/besu/pull/3839)

## 22.4.0

### Breaking Changes
- Version 22.4.x will be the last series to support Java 11. Version 22.7.0 will require Java 17 to build and run.
- In the Besu EVM Library all references to SHA3 have been renamed to the more accurate name Keccak256, including class names and comment. [#3749](https://github.com/hyperledger/besu/pull/3749)
- Removed the Gas object and replaced it with a primitive long [#3674](https://github.com/hyperledger/besu/pull/3674)
- Column family added for backward sync [#3638](https://github.com/hyperledger/besu/pull/3638)
  - Note that this added column family makes this a one-way upgrade. That is, once you upgrade your db to this version, you cannot roll back to a previous version of Besu.

### Bug Fixes
- Fix nullpointer on snapsync [#3773](https://github.com/hyperledger/besu/pull/3773)
- Introduce RocksDbSegmentIdentifier to avoid changing the storage plugin [#3755](https://github.com/hyperledger/besu/pull/3755)

## Download Links
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.4.0/besu-22.4.0.zip / SHA256 d89e102a1941e70be31c176a6dd65cd5f3d69c4c
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.4.0/besu-22.4.0.tar.gz / SHA256 868e38749dd40debe028624f8267f1fce7587010

## 22.4.0-RC2

### Breaking Changes
- In the Besu EVM Library all references to SHA3 have been renamed to the more accurate name Kecack256, including class names and comment. [#3749](https://github.com/hyperledger/besu/pull/3749)

### Additions and Improvements
- Onchain node permissioning
  - Log the enodeURL that was previously only throwing an IllegalStateException during the isPermitted check [#3697](https://github.com/hyperledger/besu/pull/3697),
  - Fail startup if node permissioning smart contract version does not match [#3765](https://github.com/hyperledger/besu/pull/3765)
- \[EXPERIMENTAL\] Add snapsync `--sync-mode="X_SNAP"` (only as client) [#3710](https://github.com/hyperledger/besu/pull/3710)
- Adapt Fast sync, and Snap sync, to use finalized block, from consensus layer, as pivot after the Merge [#3506](https://github.com/hyperledger/besu/issues/3506)
- Add IPC JSON-RPC interface (BSD/MacOS and Linux only) [#3695](https://github.com/hyperledger/besu/pull/3695)
- Column family added for backward sync [#3638](https://github.com/hyperledger/besu/pull/3638)
  - Note that this added column family makes this a one-way upgrade. That is, once you upgrade your db to this version, you cannot roll back to a previous version of Besu.

## Download Links
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.4.0-RC2/besu-22.4.0-RC2.zip /  SHA256 5fa7f927c6717ebf503291c058815cd0c5fcfab13245d3b6beb66eb20cf7ac24
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.4.0-RC2/besu-22.4.0-RC2.tar.gz / SHA256 1c4ecd17552cf5ebf120fc35dad753f45cb951ea0f817381feb2477ec0fff9c9

## 22.4.0-RC1

### Additions and Improvements
- Unit tests are now executed with JUnit5 [#3620](https://github.com/hyperledger/besu/pull/3620)
- Removed the Gas object and replaced it with a primitive long [#3674]

### Bug Fixes
- Flexible Privacy Precompile handles null payload ID [#3664](https://github.com/hyperledger/besu/pull/3664)
- Subcommand blocks import throws exception [#3646](https://github.com/hyperledger/besu/pull/3646)

## Download Links
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.4.0-RC1/besu-22.4.0-RC1.zip / SHA256 0779082acc20a98eb810eb08778e0c0e1431046c07bc89019a2761fd1baa4c25
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.4.0-RC1/besu-22.4.0-RC1.tar.gz / SHA256 15d8b0e335f962f95da46864109db9f28ed4f7bc351995b2b8db477c12b94860

## 22.1.3

### Breaking Changes
- Remove the experimental flag for bonsai tries CLI options `--data-storage-format` and `--bonsai-maximum-back-layers-to-load` [#3578](https://github.com/hyperledger/besu/pull/3578)
- Column family added for backward sync [#3532](https://github.com/hyperledger/besu/pull/3532)
  - Note that this added column family makes this a one-way upgrade. That is, once you upgrade your db to this version, you cannot roll back to a previous version of Besu.

### Deprecations
- `--tx-pool-hashes-max-size` is now deprecated and has no more effect, and it will be removed in a future release.

### Additions and Improvements
- Tune transaction synchronization parameter to adapt to mainnet traffic [#3610](https://github.com/hyperledger/besu/pull/3610)
- Improve eth/66 support [#3616](https://github.com/hyperledger/besu/pull/3616)
- Avoid reprocessing remote transactions already seen [#3626](https://github.com/hyperledger/besu/pull/3626)
- Upgraded jackson-databind dependency version [#3647](https://github.com/hyperledger/besu/pull/3647)

## Download Links
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.1.3/besu-22.1.3.zip /  SHA256 9dafb80f2ec9ce8d732fd9e9894ca2455dd02418971c89cd6ccee94c53354d5d
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.1.3/besu-22.1.3.tar.gz / SHA256 f9f8d37353aa4b5d12e87c08dd86328c1cffc591c6fc9e076c0f85a1d4663dfe

## 22.1.2

### Additions and Improvements
- Execution layer (The Merge):
  - Execution specific RPC endpoint [#3378](https://github.com/hyperledger/besu/issues/3378)
  - Adds JWT authentication to Engine APIs
  - Supports kiln V2.1 spec
- Tracing APIs
  - new API methods: trace_rawTransaction, trace_get, trace_callMany
  - added revertReason to trace APIs including: trace_transaction, trace_get, trace_call, trace_callMany, and trace_rawTransaction
- Allow mining beneficiary to transition at specific blocks for ibft2 and qbft consensus mechanisms.  [#3115](https://github.com/hyperledger/besu/issues/3115)
- Return richer information from the PrecompiledContract interface. [\#3546](https://github.com/hyperledger/besu/pull/3546)

### Bug Fixes
- Reject locally-sourced transactions below the minimum gas price when not mining. [#3397](https://github.com/hyperledger/besu/pull/3397)
- Fixed bug with contract address supplied to `debug_accountAt` [#3518](https://github.com/hyperledger/besu/pull/3518)

## Download Links
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.1.2/besu-22.1.2.zip /  SHA256 1b26e3f8982c3a9dbabc72171f83f1cfe89eef84ead45b184ee9101f411c1251
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.1.2/besu-22.1.2.tar.gz / SHA256 1eca9abddf351eaaf4e6eaa1b9536b8b4fd7d30a81d39f9d44ffeb198627ee7a

## 22.1.1

### Additions and Improvements
- Allow optional RPC methods that bypass authentication [#3382](https://github.com/hyperledger/besu/pull/3382)
- Execution layer (The Merge):
  - Extend block creation and mining to support The Merge [#3412](https://github.com/hyperledger/besu/pull/3412)
  - Backward sync [#3410](https://github.com/hyperledger/besu/pull/3410)
  - Extend validateAndProcessBlock to return an error message in case of failure, so it can be returned to the caller of ExecutePayload API [#3411](https://github.com/hyperledger/besu/pull/3411)
  - Persist latest finalized block [#2913](https://github.com/hyperledger/besu/issues/2913)
  - Add PostMergeContext, and stop syncing after the switch to PoS [#3453](https://github.com/hyperledger/besu/pull/3453)
  - Add header validation rules needed to validate The Merge blocks [#3454](https://github.com/hyperledger/besu/pull/3454)
  - Add core components: controller builder, protocol scheduler, coordinator, block creator and processor. [#3461](https://github.com/hyperledger/besu/pull/3461)
  - Execution specific RPC endpoint [#2914](https://github.com/hyperledger/besu/issues/2914), [#3350](https://github.com/hyperledger/besu/pull/3350)
- QBFT consensus algorithm is production ready

## Download Links
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.1.1/besu-22.1.1.zip /  SHA256 cfff79e19e5f9a184d0b62886990698b77d019a0745ea63b5f9373870518173e
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.1.1/besu-22.1.1.tar.gz / SHA256 51cc9d35215f977ac7338e5c611c60f225fd6a8c1c26f188e661624a039e83f3

## 22.1.0

### Breaking Changes
- Plugin API: BlockHeader.getBaseFee() method now returns an optional Wei instead of an optional Long [#3065](https://github.com/hyperledger/besu/issues/3065)
- Removed deprecated hash variable `protected volatile Hash hash;` which was used for private transactions [#3110](https://github.com/hyperledger/besu/pull/3110)

### Additions and Improvements
- Add support for additional JWT authentication algorithms [#3017](https://github.com/hyperledger/besu/pull/3017)
- Represent baseFee as Wei instead of long accordingly to the spec [#2785](https://github.com/hyperledger/besu/issues/2785)
- Implements [EIP-4399](https://eips.ethereum.org/EIPS/eip-4399) to repurpose DIFFICULTY opcode after the merge as a source of entropy from the Beacon chain. [#3081](https://github.com/hyperledger/besu/issues/3081)
- Re-order external services (e.g JsonRpcHttpService) to start before blocks start processing [#3118](https://github.com/hyperledger/besu/pull/3118)
- Stream JSON RPC responses to avoid creating big JSON strings in memory [#3076](https://github.com/hyperledger/besu/pull/3076)
- Ethereum Classic Mystique Hard Fork [#3256](https://github.com/hyperledger/besu/pull/3256)
- Genesis file parameter `blockperiodseconds` is validated as a positive integer on startup to prevent unexpected runtime behaviour [#3186](https://github.com/hyperledger/besu/pull/3186)
- Add option to require replay protection for locally submitted transactions [\#1975](https://github.com/hyperledger/besu/issues/1975)
- Update to block header validation for IBFT and QBFT to support London fork EIP-1559 [#3251](https://github.com/hyperledger/besu/pull/3251)
- Move into SLF4J as logging facade [#3285](https://github.com/hyperledger/besu/pull/3285)
- Changing the order in which we traverse the word state tree during fast sync. This should improve fast sync during subsequent pivot changes.[#3202](https://github.com/hyperledger/besu/pull/3202)
- Updated besu-native to version 0.4.3 [#3331](https://github.com/hyperledger/besu/pull/3331)
- Refactor synchronizer to asynchronously retrieve blocks from peers, and to change peer when retrying to get a block. [#3326](https://github.com/hyperledger/besu/pull/3326)
- Disable RocksDB TTL compactions [#3356](https://github.com/hyperledger/besu/pull/3356)
- add a websocket frame size configuration CLI parameter [3386][https://github.com/hyperledger/besu/pull/3386]
- Add `--ec-curve` parameter to export/export-address public-key subcommands [#3333](https://github.com/hyperledger/besu/pull/3333)

### Bug Fixes
- Change the base docker image from Debian Buster to Ubuntu 20.04 [#3171](https://github.com/hyperledger/besu/issues/3171) fixes [#3045](https://github.com/hyperledger/besu/issues/3045)
- Make 'to' field optional in eth_call method according to the spec [#3177](https://github.com/hyperledger/besu/pull/3177)
- Update to log4j 2.17.1. Resolves potential vulnerability only exploitable when using custom log4j configurations that are writable by untrusted users.
- Fix regression on cors-origin star value
- Fix for ethFeeHistory accepting hex values for blockCount
- Fix a sync issue, when the chain downloader incorrectly shutdown when a task in the pipeline is cancelled. [#3319](https://github.com/hyperledger/besu/pull/3319)
- add a websocket frame size configuration CLI parameter [3368][https://github.com/hyperledger/besu/pull/3379]
- Prevent node from peering to itself [#3342](https://github.com/hyperledger/besu/pull/3342)
- Fix an `IndexOutOfBoundsException` exception when getting block from peers. [#3304](https://github.com/hyperledger/besu/issues/3304)
- Handle legacy eth64 without throwing null pointer exceptions [#3343](https://github.com/hyperledger/besu/pull/3343)

### Download Links
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.1.0/besu-22.1.0.tar.gz \ SHA256 232bd7f274691ca14c26289fdc289d3fcdf69426dd96e2fa1601f4d079645c2f
- https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/22.1.0/besu-22.1.0.zip \ SHA256 1b701ff5b647b64aff3d73d6f1fe3fdf73f14adbe31504011eff1660ab56ad2b

## 21.10.9

### Bug Fixes
- Fix regression on cors-origin star value
- Fix for ethFeeHistory accepting hex values for blockCount

 **Full Changelog**: https://github.com/hyperledger/besu/compare/21.10.8...21.10.9

[besu-21.10.9.tar.gz](https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.10.9/besu-21.10.9.tar.gz) a4b85ba72ee73017303e4b2f0fdde84a87d376c2c17fdcebfa4e34680f52fc71
[besu-21.10.9.zip](https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.10.9/besu-21.10.9.zip) c3ba3f07340fa80064ba7c06f2c0ec081184e000f9a925d132084352d0665ef9

## 21.10.8

### Additions and Improvements
- Ethereum Classic Mystique Hard Fork [#3256](https://github.com/hyperledger/besu/pull/3256)

### Download Links
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.10.8/besu-21.10.8.tar.gz \ SHA256 d325e2e36bc38a707a9eebf92068f5021606a8c6b6464bb4b4d59008ef8014fc
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.10.8/besu-21.10.8.zip \ SHA256 a91da1e82fb378e16437327bba56dd299aafdb0614ba528167a1dae85440c5af

## 21.10.7

### Bug Fixes
- Update dependencies (including vert.x, kubernetes client-java, okhttp, commons-codec)

### Additions and Improvements
- Add support for additional JWT authentication algorithms [#3017](https://github.com/hyperledger/besu/pull/3017)
- Remove Orion ATs

### Download Links
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.10.7/besu-21.10.7.tar.gz \ SHA256 94cee804fcaea366c9575380ef0e30ed04bf2fc7451190a94887f14c07f301ff
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.10.7/besu-21.10.7.zip \ SHA256 faf1ebfb20aa6171aa6ea98d7653339272567c318711d11e350471b5bba62c00

## 21.10.6

### Bug Fixes
- Update log4j to 2.17.1

### Download Links
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.10.6/besu-21.10.6.tar.gz \ SHA256 ef579490031dd4eb3704b4041e352cfb2e7e787fcff7506b69ef88843d4e1220
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.10.6/besu-21.10.6.zip \ SHA256 0fdda65bc993905daa14824840724d0b74e3f16f771f5726f5307f6d9575a719

## 21.10.5

### Bug Fixes
- Update log4j to 2.17.0

### Download Links
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.10.5/besu-21.10.5.tar.gz \ SHA256 0d1b6ed8f3e1325ad0d4acabad63c192385e6dcbefe40dc6b647e8ad106445a8
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.10.5/besu-21.10.5.zip \ SHA256 a1689a8a65c4c6f633b686983a6a1653e7ac86e742ad2ec6351176482d6e0c57

## 21.10.4

### Bug Fixes
- Update log4j to 2.16.0.
- Change the base docker image from Debian Buster to Ubuntu 20.04 [#3171](https://github.com/hyperledger/besu/issues/3171) fixes [#3045](https://github.com/hyperledger/besu/issues/3045)

### Download links
This release is not recommended for production use.

## 21.10.3

### Additions and Improvements
- Updated log4j to 2.15.0 and disabled JNDI message format lookups to improve security.
- Represent baseFee as Wei instead of long accordingly to the spec [#2785](https://github.com/hyperledger/besu/issues/2785)
- Adding support of the NO_COLOR environment variable as described in the [NO_COLOR](https://no-color.org/) standard [#3085](https://github.com/hyperledger/besu/pull/3085)
- Add `privx_findFlexiblePrivacyGroup` RPC Method, `privx_findOnchainPrivacyGroup` will be removed in a future release [#3075](https://github.com/hyperledger/besu/pull/3075)
- The invalid value is now shown when `--bootnodes` cannot parse an item to make it easier to identify which option is invalid.
- Adding two new options to be able to specify desired TLS protocol version and Java cipher suites [#3105](https://github.com/hyperledger/besu/pull/3105)
- Implements [EIP-4399](https://eips.ethereum.org/EIPS/eip-4399) to repurpose DIFFICULTY opcode after the merge as a source of entropy from the Beacon chain. [#3081](https://github.com/hyperledger/besu/issues/3081)

### Bug Fixes
- Change the base docker image from Debian Buster to Ubuntu 20.04 [#3171](https://github.com/hyperledger/besu/issues/3171) fixes [#3045](https://github.com/hyperledger/besu/issues/3045)

### Download Link
This release is not recommended for production use.

## 21.10.2

### Additions and Improvements
- Add discovery options to genesis file [#2944](https://github.com/hyperledger/besu/pull/2944)
- Add validate-config subcommand to perform basic syntax validation of TOML config [#2994](https://github.com/hyperledger/besu/pull/2994)
- Updated Sepolia Nodes [#3034](https://github.com/hyperledger/besu/pull/3034) [#3035](https://github.com/hyperledger/besu/pull/3035)

### Bug Fixes
- Reduce shift calculations to shifts that may have an actual result. [#3039](https://github.com/hyperledger/besu/pull/3039)
- DNS Discovery daemon wasn't started [#3033](https://github.com/hyperledger/besu/pull/3033)

### Download Link
This release is not recommended for production use.

## 21.10.1

### Additions and Improvements
- Add CLI autocomplete scripts. [#2854](https://github.com/hyperledger/besu/pull/2854)
- Add support for PKCS11 keystore on PKI Block Creation. [#2865](https://github.com/hyperledger/besu/pull/2865)
- Optimize EVM Memory for MLOAD Operations [#2917](https://github.com/hyperledger/besu/pull/2917)
- Upgrade CircleCI OpenJDK docker image to version 11.0.12. [#2928](https://github.com/hyperledger/besu/pull/2928)
- Update JDK 11 to latest version in Besu Docker images. [#2925](https://github.com/hyperledger/besu/pull/2925)
- Add Sepolia proof-of-work testnet configurations [#2920](https://github.com/hyperledger/besu/pull/2920)
- Allow block period to be configured for IBFT2 and QBFT using transitions [#2902](https://github.com/hyperledger/besu/pull/2902)
- Add support for binary messages (0x02) for websocket. [#2980](https://github.com/hyperledger/besu/pull/2980)

### Bug Fixes
- Do not change the sender balance, but set gas fee to zero, when simulating a transaction without enforcing balance checks. [#2454](https://github.com/hyperledger/besu/pull/2454)
- Ensure genesis block has the default base fee if london is at block 0 [#2920](https://github.com/hyperledger/besu/pull/2920)
- Fixes the exit condition for loading a BonsaiPersistedWorldState for a sibling block of the last one persisted [#2967](https://github.com/hyperledger/besu/pull/2967)

### Early Access Features
- Enable plugins to expose custom JSON-RPC / WebSocket methods [#1317](https://github.com/hyperledger/besu/issues/1317)

### Download Link
This release is not recommended for production use.

## 21.10.0

### Additions and Improvements
- The EVM has been factored out into a standalone module, suitable for inclusion as a library. [#2790](https://github.com/hyperledger/besu/pull/2790)
- Low level performance improvements changes to cut worst-case EVM performance in half. [#2796](https://github.com/hyperledger/besu/pull/2796)
- Migrate `ExceptionalHaltReason` from an enum to an interface to allow downstream users of the EVM to add new exceptional halt reasons. [#2810](https://github.com/hyperledger/besu/pull/2810)
- reduces need for JUMPDEST analysis via caching [#2607](https://github.com/hyperledger/besu/pull/2821)
- Add support for custom private key file for public-key export and public-key export-address commands [#2801](https://github.com/hyperledger/besu/pull/2801)
- Add CLI autocomplete scripts. [#2854](https://github.com/hyperledger/besu/pull/2854)
- Added support for PKCS11 keystore on PKI Block Creation. [#2865](https://github.com/hyperledger/besu/pull/2865)
- add support for ArrowGlacier hardfork [#2943](https://github.com/hyperledger/besu/issues/2943)

### Bug Fixes
- Allow BESU_CONFIG_FILE environment to specify TOML file [#2455](https://github.com/hyperledger/besu/issues/2455)
- Fix bug with private contracts not able to call public contracts that call public contracts [#2816](https://github.com/hyperledger/besu/pull/2816)
- Fixes the exit condition for loading a BonsaiPersistedWorldState for a sibling block of the last one persisted [#2967](https://github.com/hyperledger/besu/pull/2967)
- Fixes bonsai getMutable regression affecting fast-sync [#2934](https://github.com/hyperledger/besu/pull/2934)
- Regression in RC1 involving LogOperation and frame memory overwrites [#2908](https://github.com/hyperledger/besu/pull/2908)
- Allow `eth_call` and `eth_estimateGas` to accept contract address as sender. [#2891](https://github.com/hyperledger/besu/pull/2891)

### Early Access Features
- Enable plugins to expose custom JSON-RPC / WebSocket methods [#1317](https://github.com/hyperledger/besu/issues/1317)

### Download Link
This release is not recommended for production use. \
SHA256: 71374454753c2ee595f4f34dc6913f731818d50150accbc98088aace313c6935

## 21.10.0-RC4

### Additions and Improvements

### Bug Fixes
- Fixes the exit condition for loading a BonsaiPersistedWorldState for a sibling block of the last one persisted [#2967](https://github.com/hyperledger/besu/pull/2967)
- Fixes bonsai getMutable regression affecting fast-sync [#2934](https://github.com/hyperledger/besu/pull/2934)

### Early Access Features
### Download Link
This release is not recommended for production use. \
SHA256: b16e15764b8bc06c5c3f9f19bc8b99fa48e7894aa5a6ccdad65da49bbf564793

## 21.10.0-RC3

### Bug Fixes
- Regression in RC1 involving LogOperation and frame memory overwrites [#2908](https://github.com/hyperledger/besu/pull/2908)
- Allow `eth_call` and `eth_estimateGas` to accept contract address as sender. [#2891](https://github.com/hyperledger/besu/pull/2891)
- Fix Concurrency issues in Ethpeers. [#2896](https://github.com/hyperledger/besu/pull/2896)

### Download
This release is not recommended for production use. \
SHA256: 3d4857589336717bf5e4e5ef711b9a7f3bc46b49e1cf5b3b6574a00ccc6eda94

## 21.10.0-RC1/RC2
### Additions and Improvements
- The EVM has been factored out into a standalone module, suitable for inclusion as a library. [#2790](https://github.com/hyperledger/besu/pull/2790)
- Low level performance improvements changes to cut worst-case EVM performance in half. [#2796](https://github.com/hyperledger/besu/pull/2796)
- Migrate `ExceptionalHaltReason` from an enum to an interface to allow downstream users of the EVM to add new exceptional halt reasons. [#2810](https://github.com/hyperledger/besu/pull/2810)
- reduces need for JUMPDEST analysis via caching [#2607](https://github.com/hyperledger/besu/pull/2821)
- Add support for custom private key file for public-key export and public-key export-address commands [#2801](https://github.com/hyperledger/besu/pull/2801)

### Bug Fixes
- Allow BESU_CONFIG_FILE environment to specify TOML file [#2455](https://github.com/hyperledger/besu/issues/2455)
- Fix bug with private contracts not able to call public contracts that call public contracts [#2816](https://github.com/hyperledger/besu/pull/2816)

### Early Access Features

### Download
This release is not recommended for production use. \
SHA256: 536612e5e4d7a5e7a582f729f01ba591ba68cc389e8379fea3571ed85322ff51


## 21.7.4
### Additions and Improvements
- Upgrade Gradle to 7.2, which supports building with Java 17 [#2761](https://github.com/hyperledger/besu/pull/2376)

### Bug Fixes
- Set an idle timeout for metrics connections, to clean up ports when no longer used [\#2748](https://github.com/hyperledger/besu/pull/2748)
- Onchain privacy groups can be unlocked after being locked without having to add a participant [\#2693](https://github.com/hyperledger/besu/pull/2693)
- Update Gas Schedule for Ethereum Classic [#2746](https://github.com/hyperledger/besu/pull/2746)

### Early Access Features
- \[EXPERIMENTAL\] Added support for QBFT with PKI-backed Block Creation. [#2647](https://github.com/hyperledger/besu/issues/2647)
- \[EXPERIMENTAL\] Added support for QBFT to use retrieve validators from a smart contract [#2574](https://github.com/hyperledger/besu/pull/2574)

### Download Link
https://hyperledger.jfrog.io/native/besu-binaries/besu/21.7.4/besu-21.7.4.zip \
SHA256: 778d3c42851db11fec9171f77b22662f2baeb9b2ce913d7cfaaf1042ec19b7f9

## 21.7.3
### Additions and Improvements
- Migration to Apache Tuweni 2.0 [\#2376](https://github.com/hyperledger/besu/pull/2376)
- \[EXPERIMENTAL\] Added support for DevP2P-over-TLS [#2536](https://github.com/hyperledger/besu/pull/2536)
- `eth_getWork`, `eth_submitWork` support over the Stratum port [#2581](https://github.com/hyperledger/besu/pull/2581)
- Stratum metrics [#2583](https://github.com/hyperledger/besu/pull/2583)
- Support for mining ommers [#2576](https://github.com/hyperledger/besu/pull/2576)
- Updated onchain permissioning to validate permissions on transaction submission [\#2595](https://github.com/hyperledger/besu/pull/2595)
- Removed deprecated CLI option `--privacy-precompiled-address` [#2605](https://github.com/hyperledger/besu/pull/2605)
- Removed code supporting EIP-1702. [#2657](https://github.com/hyperledger/besu/pull/2657)
- A native library was added for the alternative signature algorithm secp256r1, which will be used by default [#2630](https://github.com/hyperledger/besu/pull/2630)
- The command line option --Xsecp-native-enabled was added as an alias for --Xsecp256k1-native-enabled [#2630](https://github.com/hyperledger/besu/pull/2630)
- Added Labelled gauges for metrics [#2646](https://github.com/hyperledger/besu/pull/2646)
- support for `eth/66` networking protocol [#2365](https://github.com/hyperledger/besu/pull/2365)
- update RPC methods for post london 1559 transaction [#2535](https://github.com/hyperledger/besu/pull/2535)
- \[EXPERIMENTAL\] Added support for using DNS host name in place of IP address in onchain node permissioning rules [#2667](https://github.com/hyperledger/besu/pull/2667)
- Implement EIP-3607 Reject transactions from senders with deployed code. [#2676](https://github.com/hyperledger/besu/pull/2676)
- Ignore all unknown fields when supplied to eth_estimateGas or eth_call. [\#2690](https://github.com/hyperledger/besu/pull/2690)

### Bug Fixes
- Consider effective price and effective priority fee in transaction replacement rules [\#2529](https://github.com/hyperledger/besu/issues/2529)
- GetTransactionCount should return the latest transaction count if it is greater than the transaction pool [\#2633](https://github.com/hyperledger/besu/pull/2633)

### Early Access Features

## 21.7.2

### Additions and Improvements
This release contains improvements and bugfixes for optimum compatibility with other London client versions.

## Bug Fixes
- hotfix for private transaction identification for mainnet transactions [#2609](https://github.com/hyperledger/besu/pull/2609)

## Download Link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.7.2/besu-21.7.2.zip \
db47fd9ba33b36436ed6798d2474f7621c733353fd04f49d6defffd12e3b6e14


## 21.7.1

### Additions and Improvements
- `priv_call` now uses NO_TRACING OperationTracer implementation which improves memory usage [\#2482](https://github.com/hyperledger/besu/pull/2482)
- Ping and Pong messages now support ENR encoding as scalars or bytes [\#2512](https://github.com/hyperledger/besu/pull/2512)

### Download Link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.7.1/besu-21.7.1.zip \
sha256sum 83fc44e39a710a95d8b6cbbbf04010dea76122bafcc633a993cd15304905a402

## 21.7.0

### Additions and Improvements
This release contains the activation blocks for London across all supported testnets. They are:
  * Ropsten 10_499_401 (24 Jun 2021)
  * Goerli 5_062_605 (30 Jun 2021)
  * Rinkeby 8_897_988 (7 Jul 2021)
  * Mainnet 12_965_000 (4 Aug 2021)
- eip-1559 changes: accept transactions which have maxFeePerGas below current baseFee [\#2374](https://github.com/hyperledger/besu/pull/2374)
- Introduced transitions for IBFT2 block rewards [\#1977](https://github.com/hyperledger/besu/pull/1977)
- Change Ethstats's status from experimental feature to stable. [\#2405](https://github.com/hyperledger/besu/pull/2405)
- Fixed disabling of native libraries for secp256k1 and altBn128. [\#2163](https://github.com/hyperledger/besu/pull/2163)
- eth_feeHistory API for wallet providers [\#2466](https://github.com/hyperledger/besu/pull/2466)

### Bug Fixes
- Ibft2 could create invalid RoundChange messages in some circumstances containing duplicate prepares [\#2449](https://github.com/hyperledger/besu/pull/2449)
- Updated `eth_sendRawTransaction` to return an error when maxPriorityFeePerGas exceeds maxFeePerGas [\#2424](https://github.com/hyperledger/besu/pull/2424)
- Fixed NoSuchElementException with EIP1559 transaction receipts when using eth_getTransactionReceipt [\#2477](https://github.com/hyperledger/besu/pull/2477)

### Early Access Features
- QBFT is a Byzantine Fault Tolerant consensus algorithm, building on the capabilities of IBFT and IBFT 2.0. It aims to provide performance improvements in cases of excess round change, and provides interoperability with other EEA compliant clients, such as GoQuorum.
  - Note: QBFT currently only supports new networks. Existing networks using IBFT2.0 cannot migrate to QBFT. This will become available in a future release.
  - Note: QBFT is an early access feature pending community feedback. Please make use of QBFT in new development networks and reach out in case of issues or concerns
- GoQuorum-compatible privacy. This mode uses Tessera and is interoperable with GoQuorum.
  - Note: GoQuorum-compatible privacy is an early access feature pending community feedback.

### Download Link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.7.0/besu-21.7.0.zip
sha256sum 389465fdcc2cc5e5007a02dc2b8a2c43d577198867316bc5cc4392803ed71034

## 21.7.0-RC2

### Additions and Improvements
- eth_feeHistory API for wallet providers [\#2466](https://github.com/hyperledger/besu/pull/2466)
### Bug Fixes
- Ibft2 could create invalid RoundChange messages in some circumstances containing duplicate prepares [\#2449](https://github.com/hyperledger/besu/pull/2449)

## Download Link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.7.0-RC2/besu-21.7.0-RC2.zip
sha256sum 7bc97c359386cad84d449f786dc0a8ed8728616b6704ce473c63f1d94af3a9ef


## 21.7.0-RC1

### Additions and Improvements
- eip-1559 changes: accept transactions which have maxFeePerGas below current baseFee [\#2374](https://github.com/hyperledger/besu/pull/2374)
- Introduced transitions for IBFT2 block rewards [\#1977](https://github.com/hyperledger/besu/pull/1977)
- Change Ethstats's status from experimental feature to stable. [\#2405](https://github.com/hyperledger/besu/pull/2405)
- Fixed disabling of native libraries for secp256k1 and altBn128. [\#2163](https://github.com/hyperledger/besu/pull/2163)


### Bug Fixes

- Updated `eth_sendRawTransaction` to return an error when maxPriorityFeePerGas exceeds maxFeePerGas [\#2424](https://github.com/hyperledger/besu/pull/2424)

### Early Access Features
This release contains the activation blocks for London across all supported testnets. They are:
  * Ropsten 10_499_401 (24 Jun 2021)
  * Goerli 5_062_605 (30 Jun 2021)
  * Rinkeby 8_897_988 (7 Jul 2021)

## Download Link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.7.0-RC1/besu-21.7.0-RC1.zip
sha256sum fc959646af65a0e267fc4d695e0af7e87331d774e6e8e890f5cc391549ed175a

## 21.1.7

## Privacy users - Orion Project Deprecation
Tessera is now the recommended Private Transaction Manager for Hyperledger Besu.

Now that all primary Orion functionality has been merged into Tessera, Orion is being deprecated.
We encourage all users with active projects to use the provided migration instructions,
documented [here](https://docs.orion.consensys.net/en/latest/Tutorials/Migrating-from-Orion-to-Tessera/).

We will continue to support Orion users until 30th November 2021. If you have any questions or
concerns, please reach out to the ConsenSys protocol engineering team in the
[#orion channel on Discord](https://discord.gg/hYpHRjK) or by [email](mailto:quorum@consensys.net).


### Additions and Improvements
* Upgrade OpenTelemetry to 1.2.0. [\#2313](https://github.com/hyperledger/besu/pull/2313)

* Ethereum Classic Magneto Hard Fork [\#2315](https://github.com/hyperledger/besu/pull/2315)

* Added support for the upcoming CALAVERAS ephemeral testnet and removed the configuration for the deprecated BAIKAL ephemeral testnet. [\#2343](https://github.com/hyperledger/besu/pull/2343)

### Bug Fixes
* Fix invalid transfer values with the tracing API specifically for CALL operation [\#2319](https://github.com/hyperledger/besu/pull/2319)

### Early Access Features

#### Previously identified known issues

- Fixed issue in discv5 where nonce was incorrectly reused. [\#2075](https://github.com/hyperledger/besu/pull/2075)
- Fixed issues in debug_standardTraceBadBlockToFile and debug_standardTraceBlockToFile. [\#2120](https://github.com/hyperledger/besu/pull/2120)
- Fixed invalid error code in several JSON RPC methods when the requested block is not in the range. [\#2138](https://github.com/hyperledger/besu/pull/2138)

## Download Link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.1.7/besu-21.1.7.zip

sha256: f415c9b67d26819caeb9940324b2b1b9ce6e872c9181052739438545e84e2531


## 21.1.6

### Additions and Improvements

* Added support for the upcoming BAIKAL ephemeral testnet and removed the configuration for the deprecated YOLOv3 ephemeral testnet. [\#2237](https://github.com/hyperledger/besu/pull/2237)
* Implemented [EIP-3541](https://eips.ethereum.org/EIPS/eip-3541): Reject new contracts starting with the 0xEF byte [\#2243](https://github.com/hyperledger/besu/pull/2243)
* Implemented [EIP-3529](https://eips.ethereum.org/EIPS/eip-3529): Reduction in refunds [\#2238](https://github.com/hyperledger/besu/pull/2238)
* Implemented [EIP-3554](https://eips.ethereum.org/EIPS/eip-3554): Difficulty Bomb Delay [\#2289](https://github.com/hyperledger/besu/pull/2289)
* \[EXPERIMENTAL\] Added support for secp256r1 keys. [#2008](https://github.com/hyperledger/besu/pull/2008)

### Bug Fixes

- Added ACCESS_LIST transactions to the list of transactions using legacy gas pricing for 1559 [\#2239](https://github.com/hyperledger/besu/pull/2239)
- Reduced logging level of public key decoding failure of malformed packets. [\#2143](https://github.com/hyperledger/besu/pull/2143)
- Add 1559 parameters to json-rpc responses.  [\#2222](https://github.com/hyperledger/besu/pull/2222)

### Early Access Features

#### Previously identified known issues

- Fixed issue in discv5 where nonce was incorrectly reused. [\#2075](https://github.com/hyperledger/besu/pull/2075)
- Fixed issues in debug_standardTraceBadBlockToFile and debug_standardTraceBlockToFile. [\#2120](https://github.com/hyperledger/besu/pull/2120)
- Fixed invalid error code in several JSON RPC methods when the requested block is not in the range. [\#2138](https://github.com/hyperledger/besu/pull/2138)

## Download Link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.1.6/besu-21.1.6.zip

sha256: 3952c69a32bb390ec84ccf4c2c3eb600ea3696af9a05914985d10e1632ef8488

## 21.1.5

### Additions and Improvements

- Ignore `nonce` when supplied to eth_estimateGas or eth_call. [\#2133](https://github.com/hyperledger/besu/pull/2133)
- Ignore `privateFor` for tx estimation. [\#2160](https://github.com/hyperledger/besu/pull/2160)

### Bug Fixes

- Fixed `NullPointerException` when crossing network upgrade blocks when peer discovery is disabled. [\#2140](https://github.com/hyperledger/besu/pull/2140)

### Early Access Features

#### Previously identified known issues

- Fixed issue in discv5 where nonce was incorrectly reused. [\#2075](https://github.com/hyperledger/besu/pull/2075)
- Fixed issues in debug_standardTraceBadBlockToFile and debug_standardTraceBlockToFile. [\#2120](https://github.com/hyperledger/besu/pull/2120)

## Download Link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.1.5/besu-21.1.5.zip

sha256: edd78fcc772cfa97d11d8ee7b5766e6fac4b31b582f940838a292f2aeb204777

## 21.1.4

### Additions and Improvements

- Adds `--discovery-dns-url` CLI command [\#2088](https://github.com/hyperledger/besu/pull/2088)

### Bug Fixes

- Fixed issue in discv5 where nonce was incorrectly reused. [\#2075](https://github.com/hyperledger/besu/pull/2075)
- Fixed issues in debug_standardTraceBadBlockToFile and debug_standardTraceBlockToFile. [\#2120](https://github.com/hyperledger/besu/pull/2120)

### Early Access Features

#### Previously identified known issues

- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

## Download Link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.1.4/besu-21.1.4.zip
58ae55b492680d92aeccfbed477e8b9c25ccc1a97cca71895e27448d754a7d8b

## 21.1.3

### Additions and Improvements
* Increase node diversity when downloading blocks [\#2033](https://github.com/hyperledger/besu/pull/2033)

### Bug Fixes
* Ethereum Node Records are now dynamically recalculated when we pass network upgrade blocks. This allows for better peering through transitions without needing to restart the node. [\#1998](https://github.com/hyperledger/besu/pull/1998)


### Early Access Features

#### Previously identified known issues

- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

### Download link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.1.3/besu-21.1.3.zip
38893cae225e5c53036d06adbeccc30aeb86ef08c543fb742941a8c618485c8a

## 21.1.2

### Berlin Network Upgrade

### Important note: the 21.1.1 release contains an outdated version of the Berlin network upgrade. If you are using Besu on public Ethereum networks, you must upgrade to 21.1.2.

This release contains the activation blocks for Berlin across all supported testnets and the Ethereum mainnet. They are:
  * Ropsten 9_812_189 (10 Mar 2021)
  * Goerli 4_460_644 (17 Mar 2021)
  * Rinkeby 8_290_928 (24 Mar 2021)
  * Ethereum 12_244_000 (14 Apr 2021)


### Additions and Improvements
- Added option to set a limit for JSON-RPC connections
  * HTTP connections `--rpc-http-max-active-connections` [\#1996](https://github.com/hyperledger/besu/pull/1996)
  * WS connections `--rpc-ws-max-active-connections` [\#2006](https://github.com/hyperledger/besu/pull/2006)
- Added ASTOR testnet ETC support [\#2017](https://github.com/hyperledger/besu/pull/2017)
### Bug Fixes
* Don't Register BLS12 precompiles for Berlin [\#2015](https://github.com/hyperledger/besu/pull/2015)

#### Previously identified known issues

- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

### Download link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/21.1.2/besu-21.1.2.zip
02f4b6622756b77fed814d8c1bbf986c6178d8f5adb9d61076e061124c3d12aa

## 21.1.1

### Berlin Network Upgrade

### Important note: this release contains an outdated version of the Berlin network upgrade. If you are using Besu on public Ethereum networks, you must upgrade to 21.1.2.

This release contains the activation blocks for Berlin across all supported testnets and the Ethereum mainnet. They are:
  * Ropsten 9_812_189 (10 Mar 2021)
  * Goerli 4_460_644 (17 Mar 2021)
  * Rinkeby 8_290_928 (24 Mar 2021)
  * Ethereum 12_244_000 (14 Apr 2021)

### Additions and Improvements
* Removed EIP-2315 from the Berlin network upgrade [\#1983](https://github.com/hyperledger/besu/pull/1983)
* Added `besu_transaction_pool_transactions` to the reported metrics, counting the mempool size [\#1869](https://github.com/hyperledger/besu/pull/1869)
* Distributions and maven artifacts have been moved off of bintray [\#1886](https://github.com/hyperledger/besu/pull/1886)
* admin_peers json RPC response now includes the remote nodes enode URL
* add support for keccak mining and a ecip1049_dev network [\#1882](https://github.com/hyperledger/besu/pull/1882)
### Bug Fixes
* Fixed incorrect `groupId` in published maven pom files.
* Fixed GraphQL response for missing account, return empty account instead [\#1946](https://github.com/hyperledger/besu/issues/1946)

### Early Access Features

#### Previously identified known issues

- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

### Download link
sha256: `c22a80a54e9fed864734b9fbd69a0a46840fd27ca5211648a3eaf8a955417218 `


## 21.1.0

### Important note: this release contains an outdated version of the Berlin network upgrade, which was changed on March 5, 2021 ([link](https://github.com/ethereum/pm/issues/263#issuecomment-791473406)). If you are using Besu on public Ethereum networks, you must upgrade to 21.1.2.

## 21.1.0 Features

Features added between 20.10.0 to 21.1.0 include:
* Berlin Network Upgrade: this release contains the activation blocks for Berlin across all supported testnets and the Ethereum mainnet. They are:
  * Ropsten 9_812_189 (10 Mar 2021)
  * Goerli 4_460_644 (17 Mar 2021)
  * Rinkeby 8_290_928 (24 Mar 2021)
  * Ethereum 12_244_000 (14 Apr 2021)
* Besu Launcher: Besu now has support for the [Quorum Mainnet Launcher](https://github.com/ConsenSys/quorum-mainnet-launcher) which makes it easy for users to configure and launch Besu on the Ethereum mainnet.
* Bonsai Tries: A new database format which reduces storage requirements and improves performance for access to recent state. _Note: only full sync is currently supported._
* Miner Data JSON-RPC: The `eth_getMinerDataByBlockHash` and `eth_getMinerDataByBlockNumber` endpoints return miner rewards and coinbase address for a given block.
* EIP-1898 support: [The EIP](https://eips.ethereum.org/EIPS/eip-1898) adds `blockHash` to JSON-RPC methods which accept a default block parameter.

### Early Access Features
* Bonsai Tries: A new database format which reduces storage requirements and improves performance for access to recent state. _Note: only full sync is currently supported._
* QBFT: A new consensus algorithm to support interoperability with other Enterprise Ethereum Alliance compatible clients.

### 21.1.0 Breaking Changes
* `--skip-pow-validation-enabled` is now an error with `block import --format JSON`. This is because the JSON format doesn't include the nonce so the proof of work must be calculated.
* `eth_call` will not return a JSON-RPC result if the call fails, but will return an error instead. If it was for a revert the revert reason will be included.
* `eth_call` will not fail for account balance issues by default. An parameter `"strict": true` can be added to the call parameters (with `to` and `from`) to enforce balance checks.

### Additions and Improvements
* Added `besu_transaction_pool_transactions` to the reported metrics, counting the mempool size [\#1869](https://github.com/hyperledger/besu/pull/1869)
* Added activation blocks for Berlin Network Upgrade [\#1929](https://github.com/hyperledger/besu/pull/1929)

### Bug Fixes
* Fixed representation of access list for access list transactions in JSON-RPC results.

#### Previously identified known issues

- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

### Download link
sha256: `e4c8fe4007e3e5f7f2528cbf1eeb5457caf06536c974a6ff4305035ff5724476`

## 21.1.0-RC2
### Additions and Improvements
* Support for the Berlin Network Upgrade, although the block number must be set manually with `--override-genesis-config=berlinBlock=<blocknumber>`. This is because the block numbers haven't been determined yet. The next release will include the number in the genesis file so it will support Berlin with no intervention. [\#1898](https://github.com/hyperledger/besu/pull/1898)

## 21.1.0-RC1

### 21.1.0 Breaking Changes
* `--skip-pow-validation-enabled` is now an error with `block import --format JSON`. This is because the JSON format doesn't include the nonce so the proof of work must be calculated.
* `eth_call` will not return a JSON-RPC result if the call fails, but will return an error instead. If it was for a revert the revert reason will be included.
* `eth_call` will not fail for account balance issues by default. An parameter `"strict": true` can be added to the call parameters (with `to` and `from`) to enforce balance checks.

### Additions and Improvements
* Removed unused flags in default genesis configs [\#1812](https://github.com/hyperledger/besu/pull/1812)
* `--skip-pow-validation-enabled` is now an error with `block import --format JSON`. This is because the JSON format doesn't include the nonce so the proof of work must be calculated. [\#1815](https://github.com/hyperledger/besu/pull/1815)
* Added a new CLI option `--Xlauncher` to start a mainnet launcher. It will help to configure Besu easily.
* Return the revert reason from `eth_call` JSON-RPC api calls when the contract causes a revert. [\#1829](https://github.com/hyperledger/besu/pull/1829)
* Added `chainId`, `publicKey`, and `raw` to JSON-RPC api calls returning detailed transaction results. [\#1835](https://github.com/hyperledger/besu/pull/1835)

### Bug Fixes
* Ethereum classic heights will no longer be reported in mainnet metrics. Issue [\#1751](https://github.com/hyperledger/besu/pull/1751) Fix [\#1820](https://github.com/hyperledger/besu/pull/1820)
* Don't enforce balance checks in `eth_call` unless explicitly requested. Issue [\#502](https://github.com/hyperledger/besu/pull/502) Fix [\#1834](https://github.com/hyperledger/besu/pull/1834)

### Early Access Features

#### Previously identified known issues

- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)


### Download link

Link removed because this release contains an outdated version of the Berlin network upgrade, which was changed on March 5, 2021 ([link](https://github.com/ethereum/pm/issues/263#issuecomment-791473406)). If you are using Besu on public Ethereum networks, you must upgrade to 21.1.1. sha256 hash left for reference.

sha256: `b0fe3942052b8fd43fc3025a298a6c701f9edae2e100f0c563a1c5a4ceef71f1`

## 20.10.4

### Additions and Improvements
* Implemented [EIP-778](https://eips.ethereum.org/EIPS/eip-778): Ethereum Node Records (ENR) [\#1680](https://github.com/hyperledger/besu/pull/1680)
* Implemented [EIP-868](https://eips.ethereum.org/EIPS/eip-868): Node Discovery v4 ENR Extension [\#1721](https://github.com/hyperledger/besu/pull/1721)
* Added revert reason to eth_estimateGas RPC call. [\#1730](https://github.com/hyperledger/besu/pull/1730)
* Added command line option --static-nodes-file. [#1644](https://github.com/hyperledger/besu/pull/1644)
* Implemented [EIP-1898](https://eips.ethereum.org/EIPS/eip-1898): Add `blockHash` to JSON-RPC methods which accept a default block parameter [\#1757](https://github.com/hyperledger/besu/pull/1757)

### Bug Fixes
* Accept locally-sourced transactions below the minimum gas price. [#1480](https://github.com/hyperledger/besu/issues/1480) [#1743](https://github.com/hyperledger/besu/pull/1743)

#### Previously identified known issues

- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

### Download link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/20.10.4/besu-20.10.4.zip
sha256: f15cd5243b809659bba1706c1745aecafc012d3fc44a91419522da925493537c

## 20.10.3

### Additions and Improvements
* Added `memory` as an option to `--key-value-storage`.  This ephemeral storage is intended for sync testing and debugging.  [\#1617](https://github.com/hyperledger/besu/pull/1617)
* Fixed gasPrice parameter not always respected when passed to `eth_estimateGas` endpoint [\#1636](https://github.com/hyperledger/besu/pull/1636)
* Enabled eth65 by default [\#1682](https://github.com/hyperledger/besu/pull/1682)
* Warn that bootnodes will be ignored if specified with discovery disabled [\#1717](https://github.com/hyperledger/besu/pull/1717)

### Bug Fixes
* Accept to use default port values if not in use. [#1673](https://github.com/hyperledger/besu/pull/1673)
* Block Validation Errors should be at least INFO level not DEBUG or TRACE.  Bug [\#1568](https://github.com/hyperledger/besu/pull/1568) PR [\#1706](https://github.com/hyperledger/besu/pull/1706)
* Fixed invalid and wrong trace data, especially when calling a precompiled contract [#1710](https://github.com/hyperledger/besu/pull/1710)

#### Previously identified known issues

- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

### Download link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/20.10.3/besu-20.10.3.zip
sha256: `b5f46d945754dedcbbb1e5dd96bf2bfd13272ff09c6a66c0150b979a578f4389`

## 20.10.2

### Additions and Improvements
* Added support for batched requests in WebSockets. [#1583](https://github.com/hyperledger/besu/pull/1583)
* Added protocols section to `admin_peers` to provide info about peer health. [\#1582](https://github.com/hyperledger/besu/pull/1582)
* Added CLI option `--goquorum-compatibility-enabled` to enable GoQuorum compatibility mode. [#1598](https://github.com/hyperledger/besu/pull/1598). Note that this mode is incompatible with Mainnet.

### Bug Fixes

* Ibft2 will discard any received messages targeting a chain height <= current head - this resolves some corner cases in system correctness directly following block import. [#1575](https://github.com/hyperledger/besu/pull/1575)
* EvmTool now throws `UnsupportedForkException` when there is an unknown fork and is YOLOv2 compatible [\#1584](https://github.com/hyperledger/besu/pull/1584)
* `eth_newFilter` now supports `blockHash` parameter as per the spec [\#1548](https://github.com/hyperledger/besu/issues/1540). (`blockhash` is also still supported.)
* Fixed an issue that caused loss of peers and desynchronization when eth65 was enabled [\#1601](https://github.com/hyperledger/besu/pull/1601)

#### Previously identified known issues

- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

### Download Link

https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/20.10.2/besu-20.10.2.zip
sha256: `710aed228dcbe9b8103aef39e4431b0c63e73c3a708ce88bcd1ecfa1722ad307`

## 20.10.1

### Additions and Improvements
* `--random-peer-priority-enabled` flag added. Allows for incoming connections to be prioritized randomly. This will prevent (typically small, stable) networks from forming impenetrable peer cliques. [#1440](https://github.com/hyperledger/besu/pull/1440)
* `miner_changeTargetGasLimit` RPC added. If a target gas limit is set, allows the node operator to change it at runtime.
* Hide deprecated `--host-whitelist` option. [\#1444](https://github.com/hyperledger/besu/pull/1444)
* Prioritize high gas prices during mining. Previously we ordered only by the order in which the transactions were received. This will increase expected profit when mining. [\#1449](https://github.com/hyperledger/besu/pull/1449)
* Added support for the updated smart contract-based [node permissioning EEA interface](https://entethalliance.github.io/client-spec/spec.html#dfn-connectionallowed). [\#1435](https://github.com/hyperledger/besu/pull/1435) and [\#1496](https://github.com/hyperledger/besu/pull/1496)
* Added EvmTool binary to the distribution.  EvmTool is a CLI that can execute EVM bytecode and execute ethereum state tests. [\#1465](https://github.com/hyperledger/besu/pull/1465)
* Updated the libraries for secp256k1 and AltBN series precompiles. These updates provide significant performance improvements to those areas. [\#1499](https://github.com/hyperledger/besu/pull/1499)
* Provide MegaGas/second measurements in the log when doing a full block import, such as the catch up phase of a fast sync. [\#1512](https://github.com/hyperledger/besu/pull/1512)
* Added new endpoints to get miner data, `eth_getMinerDataByBlockHash` and `eth_getMinerDataByBlockNumber`. [\#1538](https://github.com/hyperledger/besu/pull/1538)
* Added direct support for OpenTelemetry metrics [\#1492](https://github.com/hyperledger/besu/pull/1492)
* Added support for `qip714block` config parameter in genesis file, paving the way towards permissioning interoperability between Besu and GoQuorum. [\#1545](https://github.com/hyperledger/besu/pull/1545)
* Added new CLI option `--compatibility-eth64-forkid-enabled`. [\#1542](https://github.com/hyperledger/besu/pull/1542)

### Bug Fixes

* Fix a bug on `eth_estimateGas` which returned `Internal error` instead of `Execution reverted` in case of reverted transaction. [\#1478](https://github.com/hyperledger/besu/pull/1478)
* Fixed a bug where Local Account Permissioning was being incorrectly enforced on block import/validation. [\#1510](https://github.com/hyperledger/besu/pull/1510)
* Fixed invalid enode URL when discovery is disabled  [\#1521](https://github.com/hyperledger/besu/pull/1521)
* Removed duplicate files from zip and tar.gz distributions. [\#1566](https://github.com/hyperledger/besu/pull/1566)
* Add a more rational value to eth_gasPrice, based on a configurable percentile of prior block's transactions (default: median of last 100 blocks).  [\#1563](https://github.com/hyperledger/besu/pull/1563)

## Deprecated

### --privacy-precompiled-address (Scheduled for removal in _Next_ Release)
Deprecated in 1.5.1
- CLI option `--privacy-precompiled-address` option removed. This address is now derived, based	on `--privacy-onchain-groups-enabled`. [\#1222](https://github.com/hyperledger/besu/pull/1222)

### Besu Sample Network repository

The [Besu Sample Networks repository](https://github.com/ConsenSys/besu-sample-networks) has been replaced by the [Quorum Developer Quickstart](https://besu.hyperledger.org/en/latest/Tutorials/Developer-Quickstart).

#### Previously identified known issues

- [Eth/65 loses peers](KNOWN_ISSUES.md#eth65-loses-peers)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

### Download Link

https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/20.10.1/besu-20.10.1.zip
sha256: `ac4fae310957c176564396f73c0f03c60c41129d43d078560d0dab533a69fd2a`

## 20.10.0

## Release format

Hyperledger Besu is moving its versioning scheme to [CalVer](https://calver.org/) starting with the 20.10.0 (formerly 1.6.0) release. More information about the specific version of CalVer Besu is using can be found on the [wiki](https://wiki.hyperledger.org/display/BESU/Using+CalVer+for+Besu+Releases).

## 20.10 Breaking Changes

When upgrading to 20.10, ensure you've taken into account the following breaking changes.

### JSON-RPC HTTP Error Codes For Valid Calls ([\#1426](https://github.com/hyperledger/besu/pull/1426))

Prior versions of Besu would set the HTTP Status 400 Bad Request for JSON-RPC requests that completed in an error, regardless of the kind of error.  These responses could include a complete JSON-RPC response with an error field.

In Besu version 20.10, properly formatted requests that have valid parameters (count and content) will return a HTTP Status 200 OK, with an error field if an error occurred. For example, requesting an account that does not exist in the chain, or a block by hash that Besu does not have, will now return HTTP 200 OK responses. Unparsable requests, improperly formatted requests, or requests with invalid parameters will continue to return HTTP 400 Bad Request.

Users of Web3J should note that many calls will now return a result with the error field containing the message whereas before a call would throw an exception with the error message as the exception message.

## 20.10.0 Additions and Improvements

* Added support for ECIP-1099 / Classic Thanos Fork: Calibrate Epoch Duration. [\#1421](https://github.com/hyperledger/besu/pull/1421) [\#1441](https://github.com/hyperledger/besu/pull/1441) [\#1462](https://github.com/hyperledger/besu/pull/1462)
* Added the Open Telemetry Java agent to report traces to a remote backend. Added an example to showcase the trace reporting capabilities.
* Added EvmTool binary to the distribution.  EvmTool is a CLI that can execute EVM bytecode and execute ethereum state tests. Documentation for it is available [here](https://besu.hyperledger.org/en/stable/HowTo/Troubleshoot/Use-EVM-Tool/). [\#1465](https://github.com/hyperledger/besu/pull/1465)
* Added support for the upcoming YOLOv2 ephemeral testnet and removed the flag for the deprecated YOLOv1 ephemeral testnet. [#1386](https://github.com/hyperledger/besu/pull/1386)
* Added `debug_standardTraceBlockToFile` JSON-RPC API. This API accepts a block hash and will replay the block. It returns a list of files containing the result of the trace (one file per transaction). [\#1392](https://github.com/hyperledger/besu/pull/1392)
* Added `debug_standardTraceBadBlockToFile` JSON-RPC API. This API is similar to `debug_standardTraceBlockToFile`, but can be used to obtain info about a block which has been rejected as invalid. [\#1403](https://github.com/hyperledger/besu/pull/1403)
* Added support for EIP-2929 to YOLOv2. [#1387](https://github.com/hyperledger/besu/pull/1387)
* Added `--start-block` and `--end-block` to the `blocks import` subcommand [\#1399](https://github.com/hyperledger/besu/pull/1399)
* Added support for multi-tenancy when using the early access feature of [onchain privacy group management](https://besu.hyperledger.org/en/stable/Concepts/Privacy/Onchain-PrivacyGroups/)
* \[Reverted\] Fixed memory leak in eth/65 subprotocol behavior. It is now enabled by default. [\#1420](https://github.com/hyperledger/besu/pull/1420), [#1348](https://github.com/hyperledger/besu/pull/1348), [#1321](https://github.com/hyperledger/besu/pull/1321)

### Bug Fixes

* Log block import rejection reasons at "INFO" level.  Bug [#1412](https://github.com/hyperledger/besu/issues/1412)
* Fixed NPE when executing `eth_estimateGas` with privacy enabled.  Bug [#1404](https://github.com/hyperledger/besu/issues/1404)

#### Previously identified known issues

- [Eth/65 loses peers](KNOWN_ISSUES.md#eth65-loses-peers)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

## Deprecated and Scheduled for removal in _Next_ Release

### --privacy-precompiled-address
Deprecated in 1.5.1
- CLI option `--privacy-precompiled-address` option removed. This address is now derived, based
on `--privacy-onchain-groups-enabled`. [\#1222](https://github.com/hyperledger/besu/pull/1222)

### Download link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/20.10.0/besu-20.10.0.zip

sha256sum: `2b50a375aae64b838a2cd9d43747006492cae573f1be11745b7f643646fd5a01`

## 1.5.5

### Additions and Improvements
* The new version of the [web3js-eea library (v0.10)](https://github.com/PegaSysEng/web3js-eea) supports the onchain privacy group management changes made in Besu v1.5.3.

### Bug Fixes
* Added `debug_getBadBlocks` JSON-RPC API to analyze and detect consensus flaws. Even if a block is rejected it will be returned by this method [\#1378](https://github.com/hyperledger/besu/pull/1378)
* Fix logs queries missing results against chain head [\#1351](https://github.com/hyperledger/besu/pull/1351) and [\#1381](https://github.com/hyperledger/besu/pull/1381)

#### Previously identified known issues

- [Eth/65 loses peers](KNOWN_ISSUES.md#eth65-loses-peers)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)
- [Changes not saved to database correctly causing inconsistent private states](KNOWN_ISSUES.md#Changes-not-saved-to-database-correctly-causing-inconsistent-private-states)

### Download link

https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/1.5.5/besu-1.5.5.zip

sha256sum: `e67b0a899dc4421054eaa9a8112cb89e1e5f6a56f0d8aa1b0c5111c53dfad2ad`


## 1.5.4

### Additions and Improvements

* Added `priv_debugGetStateRoot` JSON-RPC API to retrieve the state root of a specified privacy group. [\#1326](https://github.com/hyperledger/besu/pull/1326)
* Added reorg logging and `--reorg-logging-threshold` to configure the same. Besu now logs any reorgs where the old or new chain head is more than the threshold away from their common ancestors. The default is 6.
* Added `debug_batchSendRawTransaction` JSON-RPC API to submit multiple signed transactions with a single call. [\#1350](https://github.com/hyperledger/besu/pull/1350)

### Bug Fixes

* The metrics HTTP server no longer rejects requests containing `Accept` header that doesn't precisely match the prometheus text format [\#1345](https://github.com/hyperledger/besu/pull/1345)
* JSON-RPC method `net_version` should return network ID instead of chain ID [\#1355](https://github.com/hyperledger/besu/pull/1355)

#### Previously identified known issues

- [Logs queries missing results against chain head](KNOWN_ISSUES.md#Logs-queries-missing-results-against-chain-head)
- [Eth/65 loses peers](KNOWN_ISSUES.md#eth65-loses-peers)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)
- [Changes not saved to database correctly causing inconsistent private states](KNOWN_ISSUES.md#Changes-not-saved-to-database-correctly-causing-inconsistent-private-states)

### Download link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/1.5.4/besu-1.5.4.zip

sha256sum: `1f4df8e1c5e3b5b3abf6289ccfe70f302aa7c29a652b2eb713ffbdc507670420`

## 1.5.3

### Additions and Improvements

* The EvmTool now processes State Tests from the Ethereum Reference Tests. [\#1311](https://github.com/hyperledger/besu/pull/1311)
* Early access DNS support added via the `--Xdns-enabled` and `--Xdns-update-enabled` CLI options. [\#1247](https://github.com/hyperledger/besu/pull/1247)
* Add genesis config option `ecip1017EraRounds` for Ethereum Classic chains. [\#1329](https://github.com/hyperledger/besu/pull/1329)

### Bug Fixes

* K8S Permissioning to use of Service IP's rather than pod IP's which can fail [\#1190](https://github.com/hyperledger/besu/issues/1190)

#### Previously identified known issues

- [Logs queries missing results against chain head](KNOWN_ISSUES.md#Logs-queries-missing-results-against-chain-head)
- [Eth/65 loses peers](KNOWN_ISSUES.md#eth65-loses-peers)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)
- [Changes not saved to database correctly causing inconsistent private states](KNOWN_ISSUES.md#Changes-not-saved-to-database-correctly-causing-inconsistent-private-states)

### Breaking Change to Onchain Privacy Group Management

This [early access feature](https://besu.hyperledger.org/en/stable/Concepts/Privacy/Onchain-PrivacyGroups/) was changed in a way that makes onchain privacy groups created with previous versions no longer usable.

To enhance control over permissions on the privacy group management contract:

* The enclave key was removed as the first parameter for `addParticipant` and `removeParticipant`.
* The owner of the privacy group management contract is the signer of the private transaction that creates
  the privacy group. In the default onchain privacy group management contract implementation, only the
  owner can add and remove participants, and upgrade the management contract.

The onchain privacy support in the current version of the web3js-eea library (v0.9) will not be compatible with Besu v1.5.3.  We are actively working on an upgrade to webj3-eea that will support these changes.

### Download link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/1.5.3/besu-1.5.3.zip

sha256sum: `735cd511e1dae1590f2829d9535cb383aa8c526f059b3451859e5fcfccc48985`

## 1.5.2

### Additions and Improvements

* Experimental offline backup and restore has been added via the `operator x-backup-state` and `operator x-restore-state` CLI commands.  Data formats will be fluid for as long as the `x-` prefix is present in the CLI so it is advised not to rely on these backups for disaster recovery. [\#1235](https://github.com/hyperledger/besu/pull/1235)
* Experimental ethstats support added via the `Xethstats` and `Xethstats-contact` CLI commands. [\#1239](https://github.com/hyperledger/besu/pull/1239)
* Peers added via the JSON-RPC `admin_addPeer` and `admin_removePeer` will be shared or no longer shared via discovery respectively.  Previously they were not shared. [\#1177](https://github.com/hyperledger/besu/pull/1177) contributed by [br0tchain](https://github.com/br0tchain).
* New Docker Images (see below). [\#1277](https://github.com/hyperledger/besu/pull/1277)
* Reworked static peer discovery handling. [\#1292](https://github.com/hyperledger/besu/pull/1292)

### New Java VMs in Docker Image

* New docker images are being generated to use the latest version of OpenJDK (currently 14.0.1) with the tag suffix of `-openjdk-latest`, for example `1.5.2-openjdk-latest`.
* New docker images are being generated to use [GraalVM](https://www.graalvm.org/) with the tag suffix of `-graalvm`, for example `1.5.2-graalvm`.
* The existing images based on Java 11 are also being tagged with the suffix `-openjdk-11`, for example `1.5.2-openjdk-11`, as well as `1.5.2`.

The intent is that the major Java VM version or Java VM type shipped with the default docker images (`latest`, `1.5.x`, etc.) may be changed during future quarterly releases but will remain consistent within quarterly releases.

### Bug Fixes
- Offchain permissioning - fixed bug where sync status check prevented peering if static nodes configured. [\#1252](https://github.com/hyperledger/besu/issues/1252)

- GraphQL queries of `miner` in IBFT networks will no longer return an error.  PR [\#1282](https://github.com/hyperledger/besu/pull/1282) issue [\#1272](https://github.com/hyperledger/besu/issues/1272).

#### Previously identified known issues

- [Logs queries missing results against chain head](KNOWN_ISSUES.md#Logs-queries-missing-results-against-chain-head)
- [Eth/65 loses peers](KNOWN_ISSUES.md#eth65-loses-peers)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)
- [Permissioning issues on Kubernetes](KNOWN_ISSUES.md#Kubernetes-permissioning-uses-Service-IPs-rather-than-pod-IPs-which-can-fail)
- [Restarts caused by insufficient memory can cause inconsistent private state](KNOWN_ISSUES.md#Restart-caused-by-insufficient-memory-can-cause-inconsistent-private-state)

### New and Old Maintainer

- [David Mechler](https://github.com/hyperledger/besu/commits?author=davemec) has been added as a [new maintainer](https://github.com/hyperledger/besu/pull/1267).
- [Edward Evans](https://github.com/hyperledger/besu/commits?author=EdJoJob) voluntarily moved to [emeritus status](https://github.com/hyperledger/besu/pull/1270).

### Download link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/1.5.2/besu-1.5.2.zip

sha256sum: `629f44e230a635b09f8d82f2196d70d31193233718118a46412f11c50772dc85`

## 1.5.1

### Deprecated
- CLI option `--privacy-precompiled-address` option is deprecated. This address is now derived, based
on `--privacy-onchain-groups-enabled`. [\#1222](https://github.com/hyperledger/besu/pull/1222)

### Additions and Improvements

* In an IBFT2 network, a fixed block reward value and recipient address can be defined in genesis file [\#1132](https://github.com/hyperledger/besu/pull/1132)
* JSON-RPC HTTP API Authorization: exit early when checking user permissions. [\#1144](https://github.com/hyperledger/besu/pull/1144)
* HTTP/2 is enabled for JSON-RPC HTTP API over TLS. [\#1145](https://github.com/hyperledger/besu/pull/1145)
* Color output in consoles. It can be disabled with `--color-enabled=false` [\#1257](https://github.com/hyperledger/besu/pull/1257)
* Add compatibility with ClusterIP services for the Kubernetes Nat Manager  [\#1156](https://github.com/hyperledger/besu/pull/1156)
* In an IBFT2 network; a fixed block reward value and recipient address can be defined in genesis file [\#1132](https://github.com/hyperledger/besu/pull/1132)
* Add fee cap for transactions submitted via RPC. [\#1137](https://github.com/hyperledger/besu/pull/1137)

### Bug fixes

* When the default sync mode was changed to fast sync for named networks, there was one caveat we didn't address. The `dev` network should've been full sync by default. This has now been fixed. [\#1257](https://github.com/hyperledger/besu/pull/1257)
* Fix synchronization timeout issue when the blocks were too large [\#1149](https://github.com/hyperledger/besu/pull/1149)
* Fix missing results from eth_getLogs request. [\#1154](https://github.com/hyperledger/besu/pull/1154)
* Fix issue allowing Besu to be used for DDoS amplification. [\#1146](https://github.com/hyperledger/besu/pull/1146)

### Known Issues

Known issues are open issues categorized as [Very High or High impact](https://wiki.hyperledger.org/display/BESU/Defect+Prioritisation+Policy).

#### Previously identified known issues

- [Scope of logs query causing Besu to hang](KNOWN_ISSUES.md#scope-of-logs-query-causing-besu-to-hang)
- [Eth/65 loses peers](KNOWN_ISSUES.md#eth65-loses-peers)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)
- [Permissioning issues on Kubernetes](KNOWN_ISSUES.md#Kubernetes-permissioning-uses-Service-IPs-rather-than-pod-IPs-which-can-fail)
- [Restarts caused by insufficient memory can cause inconsistent private state](KNOWN_ISSUES.md#Restart-caused-by-insufficient-memory-can-cause-inconsistent-private-state)

### Download link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/1.5.1/besu-1.5.1.zip

sha256sum: `c17f49b6b8686822417184952487fc135772f0be03514085926a6984fd955b88`

## 1.5 Breaking changes

When upgrading to 1.5, ensure you've taken into account the following breaking changes.

### Docker users with volume mounts

To maintain best security practices, we're changing the `user:group` on the Docker container to `besu`.

What this means for you:

* If you are running Besu as a binary, there is no impact.
* If you are running Besu as a Docker container *and* have a volume mount for data,  ensure that the
permissions on the directory allow other users and groups to r/w. Ideally this should be set to
`besu:besu` as the owner.

Note that the `besu` user only exists within the container not outside it. The same user ID may match
a different user outside the image.

If you’re mounting local folders, it is best to set the user via the Docker `—user` argument. Use the
UID because the username may not exist inside the docker container. Ensure the directory being mounted
is owned by that user.

### Remove Manual NAT method

The NAT manager `MANUAL` method has been removed.
If you have been using the `MANUAL` method, use the `NONE` method instead. The behavior of the
`NONE` method is the same as the previously supported `MANUAL` methods.

### Privacy users

Besu minor version upgrades require upgrading Orion to the latest minor version. That is, for
Besu <> Orion node pairs, when upgrading Besu to v1.5, it is required that Orion is upgraded to
v1.6. Older versions of Orion will no longer work with Besu v1.5.

## 1.5 Features

Features added between from 1.4 to 1.5 include:
* Mining Support
  Besu supports `eth_hashrate` and `eth_submitHashrate` to obtain the hashrate when we mine with a GPU mining worker.
* Tracing
  The [Tracing API](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#trace-methods) is no longer an Early Access feature and now has full support for `trace_replayBlockTransactions`, `trace_Block` and `trace_transaction`.
* Plugin API Block Events
  `BlockAdded` and `BlockReorg` are now exposed via the [Plugin API](https://javadoc.io/doc/org.hyperledger.besu/plugin-api/latest/org/hyperledger/besu/plugin/services/BesuEvents.html).
* [Filters](https://besu.hyperledger.org/en/stable/HowTo/Interact/Filters/Accessing-Logs-Using-JSON-RPC/) and
  [subscriptions](https://besu.hyperledger.org/en/stable/HowTo/Interact/APIs/RPC-PubSub/) for private contracts.
* [SecurityModule Plugin API](https://javadoc.io/doc/org.hyperledger.besu/plugin-api/latest/org/hyperledger/besu/plugin/services/SecurityModuleService.html)
  This allows use of a different [security module](https://besu.hyperledger.org/en/stable/Reference/CLI/CLI-Syntax/#security-module)
  as a plugin to provide cryptographic function that can be used by NodeKey (such as sign, ECDHKeyAgreement etc.).
* [Onchain privacy groups](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Onchain-PrivacyGroups/)
  with add and remove members. This is an early access feature. Early access features are not recommended
  for production networks and may have unstable interfaces.

## 1.5 Additions and Improvements

* Public Networks Default to Fast Sync: The default sync mode for named permissionless networks, such as the Ethereum mainnet and testnets, is now `FAST`.
  * The default is unchanged for private networks. That is, the sync mode defaults to `FULL` for private networks.
  * Use the [`--sync-mode` command line option](https://besu.hyperledger.org/Reference/CLI/CLI-Syntax/#sync-mode) to change the sync mode. [\#384](https://github.com/hyperledger/besu/pull/384)
* Proper Mining Support: Added full support for `eth_hashrate` and `eth_submitHashrate`. It is now possible to have the hashrate when we mine with a GPU mining worker [\#1063](https://github.com/hyperledger/besu/pull/1063)
* Performance Improvements: The addition of native libraries ([\#775](https://github.com/hyperledger/besu/pull/775)) and changes to data structures in the EVM ([\#1089](https://github.com/hyperledger/besu/pull/1089)) have improved Besu sync and EVM execution times.
* Tracing API Improvements: The [Tracing API](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#trace-methods) is no longer an Early Access feature and now has full support for `trace_replayBlockTransactions`, `trace_Block` and `trace_transaction`.
* New Plugin API Block Events: `BlockAdded` and `BlockReorg` are now exposed via the Plugin API [\#637](https://github.com/hyperledger/besu/pull/637).
* Added experimental CLI option `--Xnat-kube-pod-name` to specify the name of the loadbalancer used by the Kubernetes nat manager [\#1078](https://github.com/hyperledger/besu/pull/1078)
- Local permissioning TOML config now supports additional keys (`nodes-allowlist` and `accounts-allowlist`).
Support for `nodes-whitelist` and `accounts-whitelist` will be removed in a future release.
- Add missing `mixHash` field for `eth_getBlockBy*` JSON RPC endpoints. [\#1098](https://github.com/hyperledger/besu/pull/1098)
* Besu now has a strict check on private transactions to ensure the privateFrom in the transaction
matches the sender Orion key that has distributed the payload. Besu 1.5+ requires Orion 1.6+ to work.
[#357](https://github.com/PegaSysEng/orion/issues/357)

### Bug fixes

No bug fixes with [user impact in this release](https://wiki.hyperledger.org/display/BESU/Changelog).

### Known Issues

Known issues are open issues categorized as [Very High or High impact](https://wiki.hyperledger.org/display/BESU/Defect+Prioritisation+Policy).

#### New known issues

- K8S permissioning uses of Service IPs rather than pod IPs which can fail. [\#1190](https://github.com/hyperledger/besu/pull/1190)
Workaround - Do not use permissioning on K8S.

- Restart caused by insufficient memory can cause inconsistent private state. [\#1110](https://github.com/hyperledger/besu/pull/1110)
Workaround - Ensure you allocate enough memory for the Java Runtime Environment that the node does not run out of memory.

#### Previously identified known issues

- [Scope of logs query causing Besu to hang](KNOWN_ISSUES.md#scope-of-logs-query-causing-besu-to-hang)
- [Eth/65 loses peers](KNOWN_ISSUES.md#eth65-loses-peers)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

### Download link
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/1.5.0/besu-1.5.0.zip

sha256sum: `56929d6a71cc681688351041c919e9630ab6df7de37dd0c4ae9e19a4f44460b2`

**For download links of releases prior to 1.5.0, please visit https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/**

## 1.4.6

### Additions and Improvements

- Print node address on startup. [\#938](https://github.com/hyperledger/besu/pull/938)
- Transaction pool: price bump replacement mechanism configurable through CLI. [\#928](https://github.com/hyperledger/besu/pull/928) [\#930](https://github.com/hyperledger/besu/pull/930)

### Bug Fixes

- Added timeout to queries. [\#986](https://github.com/hyperledger/besu/pull/986)
- Fixed issue where networks using onchain permissioning could stall when the bootnodes were not validators. [\#969](https://github.com/hyperledger/besu/pull/969)
- Update getForks method to ignore ClassicForkBlock chain parameter to fix issue with ETC syncing. [\#1014](https://github.com/hyperledger/besu/pull/1014)

### Known Issues

Known issues are open issues categorized as [Very High or High impact](https://wiki.hyperledger.org/display/BESU/Defect+Prioritisation+Policy).

#### Previously identified known issues

- [Scope of logs query causing Besu to hang](KNOWN_ISSUES.md#scope-of-logs-query-causing-besu-to-hang)
- [Eth/65 loses peers](KNOWN_ISSUES.md#eth65-loses-peers)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

## 1.4.5

### Additions and Improvements

- Implemented WebSocket logs subscription for private contracts (`priv_subscribe`/`priv_unsubscribe`) [\#762](https://github.com/hyperledger/besu/pull/762)
- Introduced SecurityModule plugin API. This allows use of a different security module as a plugin to
  provide cryptographic function that can be used by NodeKey (such as sign, ECDHKeyAgreement etc.). KeyPairSecurityModule
  is registered and used by default. The CLI option `--security-module=<name> (defaults to localfile)` can be used
  to identify the security module plugin name to use instead. [\#713](https://github.com/hyperledger/besu/pull/713)
- Several testing related changes to improve compatibility with [Hive](https://hivetests.ethdevops.io/) and Retesteth.
  [\#806](https://github.com/hyperledger/besu/pull/806) and [#845](https://github.com/hyperledger/besu/pull/845)
- Native libraries for secp256k1 and Altbn128 encryption are enabled by default.  To disable these libraries use
  `--Xsecp256k1-native-enabled=false` and `--Xaltbn128-native-enabled=false`. [\#775](https://github.com/hyperledger/besu/pull/775)

### Bug Fixes

- Fixed `eth_estimateGas` JSON RPC so it no longer returns gas estimates that are too low. [\#842](https://github.com/hyperledger/besu/pull/842)
- Full help not displayed unless explicitly requested. [\#437](https://github.com/hyperledger/besu/pull/437)
- Compatibility with undocumented Geth `eth_subscribe` fields. [\#654](https://github.com/hyperledger/besu/pull/654)
- Current block number included as part of `eth_getWork` response. [\#849](https://github.com/hyperledger/besu/pull/849)

### Known Issues

Known issues are open issues categorized as [Very High or High impact](https://wiki.hyperledger.org/display/BESU/Defect+Prioritisation+Policy).

#### New known issues

* Scope of logs query causing Besu to crash. [\#944](https://github.com/hyperledger/besu/pull/944)

Workaround - Limit the number of blocks queried by each `eth_getLogs` call.

#### Previously identified known issues

- [`Intrinsic gas exceeds gas limit` returned when calling `delete mapping[addr]` or `mapping[addr] = 0`](KNOWN_ISSUES.md#intrinsic-gas-exceeds-gas-limit)
- [Eth/65 not backwards compatible](KNOWN_ISSUES.md#eth65-not-backwards-compatible)
- [Error full syncing with pruning](KNOWN_ISSUES.md#error-full-syncing-with-pruning)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Bootnodes must be validators when using onchain permissioning](KNOWN_ISSUES.md#bootnodes-must-be-validators-when-using-onchain-permissioning)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

## 1.4.4

### Additions and Improvements

- Implemented [`priv_getLogs`](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#priv_getlogs). [\#686](https://github.com/hyperledger/besu/pull/686)
- Implemented private contract log filters including JSON-RPC methods to interact with private filters. [\#735](https://github.com/hyperledger/besu/pull/735)
- Implemented EIP-2315: Simple Subroutines for the EVM [\#717](https://github.com/hyperledger/besu/pull/717)
- Implemented Splunk logging. [\#725](https://github.com/hyperledger/besu/pull/725)
- Implemented optional native library encryption. [\#675](https://github.com/hyperledger/besu/pull/675).  To enable add `--Xsecp256k1-native-enabled` (for transaciton signatures) and/or `--Xaltbn128-native-enabled` (for altbn128 precomiled contracts) as command line options.

### Bug Fixes

- Flag added to toggle `eth/65` off by default. `eth/65` will remain toggled off by default until
a fix is completed for the [eth/65 known issue](KNOWN_ISSUES.md). [\#741](https://github.com/hyperledger/besu/pull/741)
- Resolve crashing NAT detectors on GKE. [\#731](https://github.com/hyperledger/besu/pull/731) fixes [\#507](https://github.com/hyperledger/besu/issues/507).
[Besu-Kubernetes Readme](https://github.com/PegaSysEng/besu-kubernetes/blob/master/README.md#network-topology-and-high-availability-requirements)
updated to reflect changes.
- Deal with quick service start failures [\#714](https://github.com/hyperledger/besu/pull/714) fixes [\#662](https://github.com/hyperledger/besu/issues/662)

### Known Issues

Known issues are open issues categorized as [Very High or High impact](https://wiki.hyperledger.org/display/BESU/Defect+Prioritisation+Policy).

#### New known issues

- `Intrinsic gas exceeds gas limit` returned when calling `delete mapping[addr]` or `mapping[addr] = 0` [\#696](https://github.com/hyperledger/besu/issues/696)

Calling delete and set to 0 Solidity mapping in Solidity fail.

#### Previously identified known issues

- [Eth/65 not backwards compatible](KNOWN_ISSUES.md#eth65-not-backwards-compatible)
- [Error full syncing with pruning](KNOWN_ISSUES.md#error-full-syncing-with-pruning)
- [Fast sync when running Besu on cloud providers](KNOWN_ISSUES.md#fast-sync-when-running-besu-on-cloud-providers)
- [Bootnodes must be validators when using onchain permissioning](KNOWN_ISSUES.md#bootnodes-must-be-validators-when-using-onchain-permissioning)
- [Privacy users with private transactions created using v1.3.4 or earlier](KNOWN_ISSUES.md#privacy-users-with-private-transactions-created-using-v134-or-earlier)

## 1.4.3

### Issues identified with 1.4.3 release

The `eth/65` change is not [backwards compatible](https://github.com/hyperledger/besu/issues/723).
This has the following impact:
* In a private network, nodes using the 1.4.3 client cannot interact with nodes using 1.4.2 or earlier
clients.
* On mainnet, synchronizing eventually stalls.

Workaround -> revert to v1.4.2.

A [fix](https://github.com/hyperledger/besu/pull/732) is currently [being tested](https://github.com/hyperledger/besu/pull/733).

### Critical Issue for Privacy Users

A critical issue for privacy users with private transactions created using Hyperledger Besu v1.3.4
or earlier has been identified. If you have a network with private transaction created using v1.3.4
or earlier, please read the following and take the appropriate steps:
https://wiki.hyperledger.org/display/BESU/Critical+Issue+for+Privacy+Users

### Additions and Improvements

- Added `eth/65` support. [\#608](https://github.com/hyperledger/besu/pull/608)
- Added block added and block reorg events. Added revert reason to block added transactions. [\#637](https://github.com/hyperledger/besu/pull/637)

### Deprecated

- Private Transaction `hash` field and `getHash()` method have been deprecated. They will be removed
in 1.5.0 release. [\#639](https://github.com/hyperledger/besu/pull/639)

### Known Issues

#### Fast sync when running Besu on cloud providers

A known [RocksDB issue](https://github.com/facebook/rocksdb/issues/6435) causes fast sync to fail
when running Besu on certain cloud providers. The following error is displayed repeatedly:

```
...
EthScheduler-Services-1 (importBlock) | ERROR | PipelineChainDownloader | Chain download failed. Restarting after short delay.
java.util.concurrent.CompletionException: org.hyperledger.besu.plugin.services.exception.StorageException: org.rocksdb.RocksDBException: block checksum mismatch:
....
```

This behaviour has been seen on AWS and Digital Ocean.

Workaround -> On AWS, a full restart of the AWS VM is required to restart the fast sync.

Fast sync is not currently supported on Digital Ocean. We are investigating options to
[add support for fast sync on Digital Ocean](https://github.com/hyperledger/besu/issues/591).

#### Error full syncing with pruning

- Error syncing with mainnet on Besu 1.3.7 node - MerkleTrieException [\#580](https://github.com/hyperledger/besu/issues/580)
The associated error is `Unable to load trie node value for hash` and is caused by the combination of
full sync and pruning.

Workarounds:
1. Explicitly disable pruning using `--pruning-enabled=false` when using fast sync.
2. If the `MerkleTrieException` occurs, delete the database and resync.

A fix for this issue is being actively worked on.

#### Fast sync reverting to full sync

In some cases of FastSyncException, fast sync reverts back to a full sync before having reached the
pivot block. [\#683](https://github.com/hyperledger/besu/issues/683)

Workaround -> To re-attempt fast syncing rather than continue full syncing, stop Besu, delete your
database, and start again.

#### Bootnodes must be validators when using onchain permissioning

- Onchain permissioning nodes can't peer when using a non-validator bootnode [\#528](https://github.com/hyperledger/besu/issues/528)

Workaround -> When using onchain permissioning, ensure bootnodes are also validators.


## 1.4.2

### Additions and Improvements

- Added `trace_block` JSON RPC API [\#449](https://github.com/hyperledger/besu/pull/449)
- Added `pulledStates` and `knownStates` to the EthQL `syncing` query and `eth_syncing` JSON-RPC api [\#565](https://github.com/hyperledger/besu/pull/565)

### Bug Fixes

- Fixed file parsing behaviour for privacy enclave keystore password file [\#554](https://github.com/hyperledger/besu/pull/554) (thanks to [magooster](https://github.com/magooster))
- Fixed known issue with being unable to re-add members to onchain privacy groups [\#471](https://github.com/hyperledger/besu/pull/471)

### Updated Early Access Features

* [Onchain privacy groups](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Onchain-PrivacyGroups/) with add and remove members. Known issue resolved (see above).
* [TRACE API](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#trace-methods) now includes `trace_block`, `trace_replayBlockTransactions`, and `trace_transaction`.
Fixed some issues on the trace replay block transactions API [\#522](https://github.com/hyperledger/besu/pull/522).

### Known Issues

#### Fast sync defaulting to full sync

-  When fast sync cannot find enough valid peers rapidly enough, Besu defaults to full sync.

Workarounds:
1. To re-attempt fast syncing rather than continue full syncing, stop Besu, delete your database,
and start again.
2. When fast syncing, explicitly disable pruning using `--pruning-enabled=false` to reduce the likelihood
of encountering the pruning bug.

A fix to remove the default to full sync is [in progress](https://github.com/hyperledger/besu/pull/427)
is being actively worked on.

#### Error full syncing with pruning

- Error syncing with mainnet on Besu 1.3.7 node - MerkleTrieException [\#BESU-160](https://jira.hyperledger.org/browse/BESU-160)
The associated error is `Unable to load trie node value for hash` and is caused by the combination of
full sync and pruning.

Workarounds:
1. Explicitly disable pruning using `--pruning-enabled=false` when using fast sync.
2. If the `MerkleTrieException` occurs, delete the database and resync.

A fix for this issue is being actively worked on.

#### Bootnodes must be validators when using onchain permissioning

- Onchain permissioning nodes can't peer when using a non-validator bootnode [\#BESU-181](https://jira.hyperledger.org/browse/BESU-181)

Workaround -> When using onchain permissioning, ensure bootnodes are also validators.

## 1.4.1

### Additions and Improvements

- Added priv_getCode [\#250](https://github.com/hyperledger/besu/pull/408). Gets the bytecode associated with a private address.
- Added `trace_transaction` JSON RPC API [\#441](https://github.com/hyperledger/besu/pull/441)
- Removed -X unstable prefix for pruning options (`--pruning-blocks-retained`, `--pruning-block-confirmations`) [\#440](https://github.com/hyperledger/besu/pull/440)
- Implemented [ECIP-1088](https://ecips.ethereumclassic.org/ECIPs/ecip-1088): Phoenix EVM and Protocol upgrades. [\#434](https://github.com/hyperledger/besu/pull/434)

### Bug Fixes

- [BESU-25](https://jira.hyperledger.org/browse/BESU-25) Use v5 Devp2p when pinging [\#392](https://github.com/hyperledger/besu/pull/392)
- Fixed a bug to manage concurrent access to cache files [\#438](https://github.com/hyperledger/besu/pull/438)
- Fixed configuration file bug: `pruning-blocks-retained` now accepts an integer in the config [\#440](https://github.com/hyperledger/besu/pull/440)
- Specifying RPC credentials file should not force RPC Authentication to be enabled [\#454](https://github.com/hyperledger/besu/pull/454)
- Enhanced estimateGas messages [\#436](https://github.com/hyperledger/besu/pull/436). When a estimateGas request fails a validation check, an improved error message is returned in the response.

### Early Access Features

Early access features are available features that are not recommended for production networks and may
have unstable interfaces.

* [Onchain privacy groups](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Onchain-PrivacyGroups/) with add and remove members.
  Not being able to to re-add a member to an onchain privacy group is a [known issue](https://github.com/hyperledger/besu/issues/455)
  with the add and remove functionality.

### Known Issues

#### Fast sync defaulting to full sync

-  When fast sync cannot find enough valid peers rapidly enough, Besu defaults to full sync.

Workarounds:
1. To re-attempt fast syncing rather than continue full syncing, stop Besu, delete your database,
and start again.
2. When fast syncing, explicitly disable pruning using `--pruning-enabled=false` to reduce the likelihood
of encountering the pruning bug.

A fix to remove the default to full sync is [in progress](https://github.com/hyperledger/besu/pull/427)
and is planned for inclusion in v1.4.1.

#### Error full syncing with pruning

- Error syncing with mainnet on Besu 1.3.7 node - MerkleTrieException [\#BESU-160](https://jira.hyperledger.org/browse/BESU-160)
The associated error is `Unable to load trie node value for hash` and is caused by the combination of
full sync and pruning.

Workarounds:
1. Explicitly disable pruning using `--pruning-enabled=false` when using fast sync.
2. If the `MerkleTrieException` occurs, delete the database and resync.

Investigation of this issue is in progress and a fix is targeted for v1.4.1.

#### Bootnodes must be validators when using onchain permissioning

- Onchain permissioning nodes can't peer when using a non-validator bootnode [\#BESU-181](https://jira.hyperledger.org/browse/BESU-181)

Workaround -> When using onchain permissioning, ensure bootnodes are also validators.

## 1.4.0

### Private State Migration

Hyperledger Besu v1.4 implements a new data structure for private state storage that is not backwards compatible.
A migration will be performed when starting v1.4 for the first time to reprocess existing private transactions
and re-create the private state data in the v1.4 format.

If you have existing private transactions, see [migration details](docs/Private-Txns-Migration.md).

### Additions and Improvements

* [TLS support](https://besu.hyperledger.org/en/latest/Concepts/TLS/) to secure client and server communication.

* [Multi-tenancy](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Multi-Tenancy/) to enable multiple participants to use the same Besu and Orion node.

* [Plugin APIs](https://besu.hyperledger.org/en/latest/Concepts/Plugins/) to enable building of Java plugins to extend Hyperledger Besu.

* Support for additional [NAT methods](https://besu.hyperledger.org/en/latest/HowTo/Find-and-Connect/Specifying-NAT/).

* Added [`priv_call`](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#priv_call) which invokes
a private contract function locally and does not change the private state.

* Besu has moved from an internal Bytes library to the [Apache Tuweni](https://tuweni.apache.org/) Bytes library.
This includes using the library in the Plugins API interfaces. [#295](https://github.com/hyperledger/besu/pull/295) and [#215](https://github.com/hyperledger/besu/pull/215)

### Early Access Features

Early access features are available features that are not recommended for production networks and may
have unstable interfaces.

* [Reorg compatible privacy](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Privacy-Overview/#reorg-compatible-privacy)
to enable private transactions on networks using consensus mechanisms that fork.

* [Tracing API](https://besu.hyperledger.org/en/latest/Concepts/Transactions/Trace-Types) to obtain detailed information about transaction processing.

### Bug Fixes

See RC and Beta sections below.

### Known Issues

#### Fast sync defaulting to full sync

-  When fast sync cannot find enough valid peers rapidly enough, Besu defaults to full sync.

Workarounds:
1. To re-attempt fast syncing rather than continue full syncing, stop Besu, delete your database,
and start again.
2. When fast syncing, explicitly disable pruning using `--pruning-enabled=false` to reduce the likelihood
of encountering the pruning bug.

A fix to remove the default to full sync is [in progress](https://github.com/hyperledger/besu/pull/427)
and is planned for inclusion in v1.4.1.

#### Error full syncing with pruning

- Error syncing with mainnet on Besu 1.3.7 node - MerkleTrieException [\#BESU-160](https://jira.hyperledger.org/browse/BESU-160)
The associated error is `Unable to load trie node value for hash` and is caused by the combination of
full sync and pruning.

Workarounds:
1. Explicitly disable pruning using `--pruning-enabled=false` when using fast sync.
2. If the `MerkleTrieException` occurs, delete the database and resync.

Investigation of this issue is in progress and a fix is targeted for v1.4.1.

#### Bootnodes must be validators when using onchain permissioning

- Onchain permissioning nodes can't peer when using a non-validator bootnode [\#BESU-181](https://jira.hyperledger.org/browse/BESU-181)

Workaround -> When using onchain permissioning, ensure bootnodes are also validators.


## 1.4.0 RC-2

### Private State Migration
Hyperledger Besu v1.4 implements a new data structure for private state storage that is not backwards compatible.
A migration will be performed when starting v1.4 for the first time to reprocess existing private transactions
and re-create the private state data in the v1.4 format.
If you have existing private transactions, see [migration details](docs/Private-Txns-Migration.md).

## 1.4.0 RC-1

### Additions and Improvements

- New`trace_replayBlockTransactions` JSON-RPC API

This can be enabled using the `--rpc-http-api TRACE` CLI flag.  There are some philosophical differences between Besu and other implementations that are outlined in [trace_rpc_apis](docs/trace_rpc_apis.md).

- Ability to automatically detect Docker NAT settings from inside the conainter.

The default NAT method (AUTO) can detect this so no user intervention is required to enable this.

- Added [Multi-tenancy](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Multi-Tenancy/) support which allows multiple participants to use the same Besu node for private transactions.

- Added TLS support for communication with privacy enclave

### Bug Fixes

- Private transactions are now validated before sent to the enclave [\#356](https://github.com/hyperledger/besu/pull/356)

### Known Bugs

- Error syncing with mainnet on Besu 1.3.7 node - MerkleTrieException [\#BESU-160](https://jira.hyperledger.org/browse/BESU-160)

Workaround -> Don't enable pruning when syncing to mainnet.

- Onchain permissioning nodes can't peer when using a non-validator bootnode [\#BESU-181](https://jira.hyperledger.org/browse/BESU-181)

Workaround -> When using onchain permissioning, ensure bootnodes are also validators.

## 1.4 Beta 3

### Additions and Improvements

- CLI option to enable TLS client auth for JSON-RPC HTTP [\#340](https://github.com/hyperledger/besu/pull/340)

Added CLI options to enable TLS client authentication and trusting client certificates:
~~~
--rpc-http-tls-client-auth-enabled - Enable TLS client authentication for the JSON-RPC HTTP service (default: false)
--rpc-http-tls-known-clients-file - Path to file containing client's certificate common name and fingerprint for client authentication.
--rpc-http-tls-ca-clients-enabled - Enable to accept clients certificate signed by a valid CA for client authentication (default: false)
~~~
If client-auth is enabled, user must either enable CA signed clients OR provide a known-clients file. An error is reported
if both CA signed clients is disabled and known-clients file is not specified.

- Stable Plugins APIs [\#346](https://github.com/hyperledger/besu/pull/346)

The `BesuEvents` service and related `data` package have been marked as a stable plugin API.

### Bug Fixes

- Return missing signers from getSignerMetrics [\#343](https://github.com/hyperledger/besu/pull/)

### Experimental Features

- Experimental support for `trace_replayBlockTransactions` - multiple PRs

Added support for the `trace_replayBlockTransactions` JSON-RPC call. To enable this API add
`TRACE` to the `rpc-http-api` options (for example,  `--rpc-http-api TRACE` on the command line).

This is not a production ready API.  There are known bugs relating to traced memory from calls and
returns, and the gas calculation reported in the flat traces does not always match up with the
correct gas calculated for consensus.

## 1.4 Beta 2

### Additions and Improvements

- Enable TLS for JSON-RPC HTTP Service [\#253](https://github.com/hyperledger/besu/pull/253)

Exposes new command line parameters to enable TLS on Ethereum JSON-RPC HTTP interface to allow clients like EthSigner to connect via TLS:
`--rpc-http-tls-enabled=true`
(Optional - Only required if `--rpc-http-enabled` is set to true) Set to `true` to enable TLS. False by default.
`--rpc-http-tls-keystore-file="/path/to/cert.pfx"`
(Must be specified if TLS is enabled) Path to PKCS12 format key store which contains server's certificate and it's private key
`--rpc-http-tls-keystore-password-file="/path/to/cert.passwd"`
(Must be specified if TLS is enabled) Path to the text file containing password for unlocking key store.
`--rpc-http-tls-known-clients-file="/path/to/rpc_tls_clients.txt"`
(Optional) Path to a plain text file containing space separated client’s certificate’s common name and its sha-256 fingerprints when
they are not signed by a known CA. The presence of this file (even empty) enables TLS client authentication. That is, the client
presents the certificate to server on TLS handshake and server establishes that the client certificate is either signed by a
proper/known CA. Otherwise, server trusts client certificate by reading the sha-256 fingerprint from known clients file specified above.

The format of the file is (as an example):
`localhost DF:65:B8:02:08:5E:91:82:0F:91:F5:1C:96:56:92:C4:1A:F6:C6:27:FD:6C:FC:31:F2:BB:90:17:22:59:5B:50`

### Bug Fixes

- TotalDifficulty is a BigInteger [\#253](https://github.com/hyperledger/besu/pull/253).
  Don't try and cast total difficulty down to a long because it will overflow long in a reasonable timeframe.

## 1.4 Beta 1

### Additions and Improvements

- Besu has moved from an internal Bytes library to the [Apache Tuweni](https://tuweni.apache.org/) Bytes library.  This includes using the library in the Plugins API interfaces. [#295](https://github.com/hyperledger/besu/pull/295) and [#215](https://github.com/hyperledger/besu/pull/215)
- Besu stops processing blocks if Orion is unavailable [\#253](https://github.com/hyperledger/besu/pull/253)
- Added priv_call [\#250](https://github.com/hyperledger/besu/pull/250).  Invokes a private contract function locally and does not change the private state.
- Support for [EIP-2124](https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2124.md), which results in faster peer discovery [\#156](https://github.com/hyperledger/besu/pull/156)

## 1.3.8

### Additions and Improvements

- `admin_generateLogBloomCache` JSON-RPC API to generate a cache of the block bloombits that improves performance for log queries [\#262](https://github.com/hyperledger/besu/pull/262)

## Critical Fix in 1.3.7

1.3.7 includes a critical fix for Ethereum MainNet users and the Muir Glacier upgrade. We recommend users of Ethereum public networks
(MainNet, Ropsten, Rinkeby, and Goerli) upgrade immediately. This upgrade is also strongly recommended for users of private networks.

For more details, see [Hyperledger Besu Wiki](https://wiki.hyperledger.org/display/BESU/Mainnet+Consensus+Bug+Identified+and+Resolved+in+Hyperledger+Besu).

## Muir Glacier Compatibility

For compatibility with Ethereum Muir Glacier upgrade, use v1.3.7 or later.

## ETC Agharta Compatibility

For compatibility with ETC Agharta upgrade, use 1.3.7 or later.

### 1.3.7

### Additions and Improvements

- Hard Fork Support: Configures the Agharta activation block for the ETC MainNet configuration [\#251](https://github.com/hyperledger/besu/pull/251) (thanks to [soc1c](https://github.com/soc1c))
- `operator generate-log-bloom-cache` command line option to generate a cache of the block bloombits that improves performance for log queries  [\#245](https://github.com/hyperledger/besu/pull/245)

### Bug Fixes

- Resolves a Mainnet consensus issue [\#254](https://github.com/hyperledger/besu/pull/254)

### New Maintainer

[Edward Mack](https://github.com/hyperledger/besu/commits?author=edwardmack) added as a [new maintainer](https://github.com/hyperledger/besu/pull/219).

### 1.3.6

### Additions and Improvements

- Performance improvements:
  * Multithread Websockets to increase throughput [\#231](https://github.com/hyperledger/besu/pull/231)
  * NewBlockHeaders performance improvement [\#230](https://github.com/hyperledger/besu/pull/230)
- EIP2384 - Ice Age Adustment around Istanbul [\#211](https://github.com/hyperledger/besu/pull/211)
- Documentation updates include:
  * [Configuring mining using the Stratum protocol](https://besu.hyperledger.org/en/latest/HowTo/Configure/Configure-Mining/)
  * [ETC network command line options](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#network)
- Hard Fork Support:
   * MuirGlacier for Ethereum Mainnet and Ropsten Testnet
   * Agharta for Kotti and Mordor Testnets

### Bug Fixes

- [\#210](https://github.com/hyperledger/besu/pull/210) fixes WebSocket frames handling
  User impact: PING/PONG frames handling in Websocket services was not implemented

### 1.3.5

### Additions and Improvements

- Log Event Streaming for Plugin API [\#186](https://github.com/hyperledger/besu/pull/186)
- Allow use a external JWT public key in authenticated APIs [\#183](https://github.com/hyperledger/besu/pull/183)
- ETC Configuration, classic fork peer validator [\#176](https://github.com/hyperledger/besu/pull/176) (thanks to [edwardmack](https://github.com/edwardmack))
- Allow IBFT validators to be changed at a given block [\#173](https://github.com/hyperledger/besu/pull/173)
- Support external mining using Stratum [\#140](https://github.com/hyperledger/besu/pull/140) (thanks to [atoulme](https://github.com/atoulme))
- Add more fields to private transaction receipt [\#85](https://github.com/hyperledger/besu/pull/85) (thanks to [josh-richardson](https://github.com/josh-richardson))
- [Pruning documentation](https://besu.hyperledger.org/en/latest/Concepts/Pruning/)

### Technical Improvements

- ETC - Cleanup [\#201](https://github.com/hyperledger/besu/pull/201) (thanks to [GregTheGreek](https://github.com/GregTheGreek))
- User specific enclave public key configuration in auth file [\#196](https://github.com/hyperledger/besu/pull/196)
- Change CustomForks -\> Transitions [\#193](https://github.com/hyperledger/besu/pull/193)
- Pass identity information into RpcMethod from Http Service [\#189](https://github.com/hyperledger/besu/pull/189)
- Remove the use of JsonRpcParameters from RpcMethods [\#188](https://github.com/hyperledger/besu/pull/188)
- Repaired Metrics name collision between Privacy and RocksDB [\#187](https://github.com/hyperledger/besu/pull/187)
- Multi-Tenancy: Do not specify a public key anymore when requesting a … [\#185](https://github.com/hyperledger/besu/pull/185)
- Updates to circle building acceptance tests [\#184](https://github.com/hyperledger/besu/pull/184)
- Move Apache Tuweni dependency to official release [\#181](https://github.com/hyperledger/besu/pull/181) (thanks to [atoulme](https://github.com/atoulme))
- Update Gradle to 6.0, support Java 13 [\#180](https://github.com/hyperledger/besu/pull/180)
- ETC Atlantis fork [\#179](https://github.com/hyperledger/besu/pull/179) (thanks to [edwardmack](https://github.com/edwardmack))
- ETC Gotham Fork [\#178](https://github.com/hyperledger/besu/pull/178) (thanks to [edwardmack](https://github.com/edwardmack))
- ETC DieHard fork support [\#177](https://github.com/hyperledger/besu/pull/177) (thanks to [edwardmack](https://github.com/edwardmack))
- Remove 'parentHash', 'number' and 'gasUsed' fields from the genesis d… [\#175](https://github.com/hyperledger/besu/pull/175) (thanks to [SweeXordious](https://github.com/SweeXordious))
- Enable pruning by default for fast sync and validate conflicts with privacy [\#172](https://github.com/hyperledger/besu/pull/172)
- Update RocksDB [\#170](https://github.com/hyperledger/besu/pull/170)
- Vpdate ver to 1.3.5-snapshot [\#169](https://github.com/hyperledger/besu/pull/169)
- Added PoaQueryService method that returns local node signer… [\#163](https://github.com/hyperledger/besu/pull/163)
- Add versioning to privacy storage [\#149](https://github.com/hyperledger/besu/pull/149)
- Update reference tests [\#139](https://github.com/hyperledger/besu/pull/139)

### 1.3.4

- Reverted _Enable pruning by default for fast sync (#135)_ [\#164](https://github.com/hyperledger/besu/pull/164)

### 1.3.3

### Technical Improvements

- Add --identity flag for client identification in node browsers [\#150](https://github.com/hyperledger/besu/pull/150)
- Istanbul Mainnet Block [\#145](https://github.com/hyperledger/besu/pull/150)
- Add priv\_getEeaTransactionCount [\#110](https://github.com/hyperledger/besu/pull/110)

### Additions and Improvements

- Redesign of how JsonRpcMethods are created [\#159](https://github.com/hyperledger/besu/pull/159)
- Moving JsonRpcMethods classes into the same package, prior to refactor [\#154](https://github.com/hyperledger/besu/pull/154)
- Reflect default logging in CLI help [\#148](https://github.com/hyperledger/besu/pull/148)
- Handle zero port better in NAT [\#147](https://github.com/hyperledger/besu/pull/147)
- Rework how filter and log query parameters are created/used [\#146](https://github.com/hyperledger/besu/pull/146)
- Don't generate shutdown tasks in controller [\#141](https://github.com/hyperledger/besu/pull/141)
- Ibft queries [\#138](https://github.com/hyperledger/besu/pull/138)
- Enable pruning by default for fast sync [\#135](https://github.com/hyperledger/besu/pull/135)
- Ensure spotless runs in CI [\#132](https://github.com/hyperledger/besu/pull/132)
- Add more logging around peer disconnects [\#131](https://github.com/hyperledger/besu/pull/131)
- Repair EthGetLogs returning incorrect results [\#128](https://github.com/hyperledger/besu/pull/128)
- Use Bloombits for Logs queries [\#127](https://github.com/hyperledger/besu/pull/127)
- Improve message when extraData missing [\#121](https://github.com/hyperledger/besu/pull/121)
- Fix miner startup logic [\#104](https://github.com/hyperledger/besu/pull/104)
- Support log reordring from reorgs in `LogSubscriptionService` [\#86](https://github.com/hyperledger/besu/pull/86)

### 1.3.2

### Additions and Improvements

- besu -v to print plugin versions[\#123](https://github.com/hyperledger/besu/pull/123)

### Technical Improvements

- Update Governance and Code of Conduct verbiage [\#120](https://github.com/hyperledger/besu/pull/120)
- Fix private transaction root mismatch [\#118](https://github.com/hyperledger/besu/pull/118)
- Programatically enforce plugin CLI variable names [\#117](https://github.com/hyperledger/besu/pull/117)
- Additional unit test for selecting replaced pending transactions [\#116](https://github.com/hyperledger/besu/pull/116)
- Only set sync targets that have an estimated height value [\#115](https://github.com/hyperledger/besu/pull/115)
- Fix rlpx startup [\#114](https://github.com/hyperledger/besu/pull/114)
- Expose getPayload in Transaction plugin-api interface. [\#113](https://github.com/hyperledger/besu/pull/113)
- Dependency Version Upgrades [\#112](https://github.com/hyperledger/besu/pull/112)
- Add hash field in Transaction plugin interface. [\#111](https://github.com/hyperledger/besu/pull/111)
- Rework sync status events [\#106](https://github.com/hyperledger/besu/pull/106)

### 1.3.1

### Additions and Improvements

- Added GraphQL query/logs support [\#94](https://github.com/hyperledger/besu/pull/94)

### Technical Improvements

- Add totalDiffculty to BlockPropagated events. [\#97](https://github.com/hyperledger/besu/pull/97)
- Merge BlockchainQueries classes [\#101](https://github.com/hyperledger/besu/pull/101)
- Fixed casing of dynamic MetricCategorys [\#99](https://github.com/hyperledger/besu/pull/99)
- Fix private transactions breaking evm [\#96](https://github.com/hyperledger/besu/pull/96)
- Make SyncState variables thread-safe [\#95](https://github.com/hyperledger/besu/pull/95)
- Fix transaction tracking by sender [\#93](https://github.com/hyperledger/besu/pull/93)
- Make logic in PersistBlockTask more explicit to fix a LGTM warning [\#92](https://github.com/hyperledger/besu/pull/92)
- Removed Unused methods in the transaction simulator. [\#91](https://github.com/hyperledger/besu/pull/91)
- Fix ThreadBesuNodeRunner BesuConfiguration setup [\#90](https://github.com/hyperledger/besu/pull/90)
- JsonRpc method disabled error condition rewrite and unit test [\#80](https://github.com/hyperledger/besu/pull/80)
- Round trip testing of state trie account values [\#31](https://github.com/hyperledger/besu/pull/31)

### 1.3

### Breaking Change

- Disallow comments in Genesis JSON file. [\#49](https://github.com/hyperledger/besu/pull/49)

### Additions and Improvements

- Add `--required-block` command line option to deal with chain splits [\#79](https://github.com/hyperledger/besu/pull/79)
- Store db metadata file in the root data directory. [\#46](https://github.com/hyperledger/besu/pull/46)
- Add `--target-gas-limit` command line option. [\#24](https://github.com/hyperledger/besu/pull/24)(thanks to new contributor [cfelde](https://github.com/cfelde))
- Allow private contracts to access public state. [\#9](https://github.com/hyperledger/besu/pull/9)
- Documentation updates include:
  - Added [sample load balancer configurations](https://besu.hyperledger.org/en/latest/HowTo/Configure/Configure-HA/Sample-Configuration/)
  - Added [`retesteth`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Subcommands/#retesteth) subcommand
  - Added [`debug_accountRange`](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#debug_accountrange) JSON-RPC API method
  - Clarified purpose of [static nodes](https://besu.hyperledger.org/en/latest/HowTo/Find-and-Connect/Managing-Peers/#static-nodes)
  - Added links [Kubernetes reference implementations](https://besu.hyperledger.org/en/latest/HowTo/Deploy/Kubernetes/)
  - Added content about [access between private and public states](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Privacy-Groups/#access-between-states)
  - Added restriction that [account permissioning cannot be used with random key signing](https://besu.hyperledger.org/en/latest/HowTo/Use-Privacy/Sign-Privacy-Marker-Transactions/).
  - Added high availability requirement for [private transaction manager](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Privacy-Overview/#availability) (ie, Orion)
  - Added [genesis file reference](https://besu.hyperledger.org/en/latest/Reference/Config-Items/)

### Technical Improvements

- Less verbose synching subscriptions [\#59](https://github.com/hyperledger/besu/pull/59)
- Return enclave key instead of private transaction hash [\#53](https://github.com/hyperledger/besu/pull/53)
- Fix mark sweep pruner bugs where nodes that should be kept were being swept  [\#50](https://github.com/hyperledger/besu/pull/50)
- Clean up BesuConfiguration construction [\#51](https://github.com/hyperledger/besu/pull/51)
- Private tx nonce errors return same msg as any tx [\#48](https://github.com/hyperledger/besu/pull/48)
- Fix default logging [\#47](https://github.com/hyperledger/besu/pull/47)
- Introduce virtual operation. [\#45](https://github.com/hyperledger/besu/pull/45)
- Downgrade RocksDBPlugin Logging Levels [\#44](https://github.com/hyperledger/besu/pull/44)
- Infrastructure for exposing PoA metrics for plugins. [\#37](https://github.com/hyperledger/besu/pull/37)
- Refactor privacy storage. [\#7](https://github.com/hyperledger/besu/pull/7)

## 1.2.4

### Additions and Improvements

- Add Istanbul block (5435345) for Rinkeby [\#35](https://github.com/hyperledger/besu/pull/35)
- Add Istanbul block (1561651) for Goerli [\#27](https://github.com/hyperledger/besu/pull/27)
- Add Istanbul block (6485846) for Ropsten [\#26](https://github.com/hyperledger/besu/pull/26)
- Add privDistributeRawTransaction endpoint [\#23](https://github.com/hyperledger/besu/pull/23) (thanks to [josh-richardson](https://github.com/josh-richardson))

### Technical Improvements

- Refactors pantheon private key to signing private key [\#34](https://github.com/hyperledger/besu/pull/34) (thanks to [josh-richardson](https://github.com/josh-richardson))
- Support both BESU\_ and PANTHEON\_ env var prefixes [\#32](https://github.com/hyperledger/besu/pull/32)
- Use only fully validated peers for fast sync pivot selection [\#21](https://github.com/hyperledger/besu/pull/21)
- Support Version Rollbacks for RocksDB \(\#6\) [\#19](https://github.com/hyperledger/besu/pull/19)
- Update Cava library to Tuweni Library [\#18](https://github.com/hyperledger/besu/pull/18)
- StateTrieAccountValue:Version should be written as an int, not a long [\#17](https://github.com/hyperledger/besu/pull/17)
- Handle discovery peers with updated endpoints [\#12](https://github.com/hyperledger/besu/pull/12)
- Change retesteth port [\#11](https://github.com/hyperledger/besu/pull/11)
- Renames eea\_getTransactionReceipt to priv\_getTransactionReceipt [\#10](https://github.com/hyperledger/besu/pull/10) (thanks to [josh-richardson](https://github.com/josh-richardson))
- Support Version Rollbacks for RocksDB [\#6](https://github.com/hyperledger/besu/pull/6)
- Moving AT DSL into its own module [\#3](https://github.com/hyperledger/besu/pull/3)

## 1.2.3

### Additions and Improvements
- Added an override facility for genesis configs [\#1915](https://github.com/PegaSysEng/pantheon/pull/1915)
- Finer grained logging configuration [\#1895](https://github.com/PegaSysEng/pantheon/pull/1895) (thanks to [matkt](https://github.com/matkt))

### Technical Improvements

- Add archiving of docker test reports [\#1921](https://github.com/PegaSysEng/pantheon/pull/1921)
- Events API: Transaction dropped, sync status, and renames [\#1919](https://github.com/PegaSysEng/pantheon/pull/1919)
- Remove metrics from plugin registration [\#1918](https://github.com/PegaSysEng/pantheon/pull/1918)
- Replace uses of Instant.now from within the IBFT module [\#1911](https://github.com/PegaSysEng/pantheon/pull/1911)
- Update plugins-api build script [\#1908](https://github.com/PegaSysEng/pantheon/pull/1908)
- Ignore flaky tracing tests [\#1907](https://github.com/PegaSysEng/pantheon/pull/1907)
- Ensure plugin-api module gets published at the correct maven path [\#1905](https://github.com/PegaSysEng/pantheon/pull/1905)
- Return the plugin-apis to this repo [\#1900](https://github.com/PegaSysEng/pantheon/pull/1900)
- Stop autogenerating BesuInfo.java [\#1899](https://github.com/PegaSysEng/pantheon/pull/1899)
- Extracted Metrics interfaces to plugins-api. [\#1898](https://github.com/PegaSysEng/pantheon/pull/1898)
- Fix key value storage clear so it removes all values [\#1894](https://github.com/PegaSysEng/pantheon/pull/1894)
- Ethsigner test [\#1892](https://github.com/PegaSysEng/pantheon/pull/1892) (thanks to [iikirilov](https://github.com/iikirilov))
- Return null private transaction receipt instead of error [\#1872](https://github.com/PegaSysEng/pantheon/pull/1872) (thanks to [iikirilov](https://github.com/iikirilov))
- Implement trace replay block transactions trace option [\#1886](https://github.com/PegaSysEng/pantheon/pull/1886)
- Use object parameter instead of list of parameters for priv\_createPrivacyGroup [\#1868](https://github.com/PegaSysEng/pantheon/pull/1868) (thanks to [iikirilov](https://github.com/iikirilov))
- Refactor privacy acceptance tests [\#1864](https://github.com/PegaSysEng/pantheon/pull/1864) (thanks to [iikirilov](https://github.com/iikirilov))

## 1.2.2

### Additions and Improvements
- Support large numbers for the `--network-id` option [\#1891](https://github.com/PegaSysEng/pantheon/pull/1891)
- Added eea\_getTransactionCount Json Rpc [\#1861](https://github.com/PegaSysEng/pantheon/pull/1861)
- PrivacyMarkerTransaction to be signed with a randomly generated key [\#1844](https://github.com/PegaSysEng/pantheon/pull/1844)
- Implement eth\_getproof JSON RPC API [\#1824](https://github.com/PegaSysEng/pantheon/pull/1824) (thanks to [matkt](https://github.com/matkt))
- Documentation updates include:
  - [Improved navigation](https://docs.pantheon.pegasys.tech/en/latest/)
  - [Added permissioning diagram](https://docs.pantheon.pegasys.tech/en/latest/Concepts/Permissioning/Permissioning-Overview/#onchain)
  - [Added Responsible Disclosure policy](https://docs.pantheon.pegasys.tech/en/latest/Reference/Responsible-Disclosure/)
  - [Added `blocks export` subcommand](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Subcommands/#export)

### Technical Improvements
- Update the `pantheon blocks export` command usage [\#1887](https://github.com/PegaSysEng/pantheon/pull/1887) (thanks to [matkt](https://github.com/matkt))
- Stop Returning null for 'pending' RPC calls [\#1883](https://github.com/PegaSysEng/pantheon/pull/1883)
- Blake validation errors are hard errors [\#1882](https://github.com/PegaSysEng/pantheon/pull/1882)
- Add test cases for trace\_replayBlockTransactions [\#1881](https://github.com/PegaSysEng/pantheon/pull/1881)
- Simplify json rpc spec test setup [\#1880](https://github.com/PegaSysEng/pantheon/pull/1880)
- Tweak JSON import format [\#1878](https://github.com/PegaSysEng/pantheon/pull/1878)
- Transactions listeners should use the subscriber pattern [\#1877](https://github.com/PegaSysEng/pantheon/pull/1877)
- Maven spotless [\#1876](https://github.com/PegaSysEng/pantheon/pull/1876)
- Don't cache for localbalance [\#1875](https://github.com/PegaSysEng/pantheon/pull/1875)
- EIP-1108 - Reprice alt\_bn128  [\#1874](https://github.com/PegaSysEng/pantheon/pull/1874)
- Create stub trace\_replayBlockTransactions json-rpc method  [\#1873](https://github.com/PegaSysEng/pantheon/pull/1873)
- Improve trace log [\#1870](https://github.com/PegaSysEng/pantheon/pull/1870)
- Pruning Command Line Flags [\#1869](https://github.com/PegaSysEng/pantheon/pull/1869)
- Re-enable istanbul [\#1865](https://github.com/PegaSysEng/pantheon/pull/1865)
- Fix logic to disconnect from peers on fork [\#1863](https://github.com/PegaSysEng/pantheon/pull/1863)
- Blake 2b tweaks [\#1862](https://github.com/PegaSysEng/pantheon/pull/1862)
- Sweep state roots before child nodes [\#1854](https://github.com/PegaSysEng/pantheon/pull/1854)
- Update export subcommand to export blocks in rlp format [\#1852](https://github.com/PegaSysEng/pantheon/pull/1852)
- Updating docker tests to make it easier to follow & ensure it listens on the right interface on docker [\#1851](https://github.com/PegaSysEng/pantheon/pull/1851)
- Disable Istanbul block [\#1849](https://github.com/PegaSysEng/pantheon/pull/1849)
- Add read-only blockchain factory method [\#1845](https://github.com/PegaSysEng/pantheon/pull/1845)
- Removing the release plugin in favour of the new process with branches [\#1843](https://github.com/PegaSysEng/pantheon/pull/1843)
- Update Görli bootnodes [\#1842](https://github.com/PegaSysEng/pantheon/pull/1842)
- Upgrade graphql library to version 13.0 [\#1834](https://github.com/PegaSysEng/pantheon/pull/1834)
- Database versioning and enable multi-column database [\#1830](https://github.com/PegaSysEng/pantheon/pull/1830)
- Fixes invalid JsonGetter, comment [\#1811](https://github.com/PegaSysEng/pantheon/pull/1811) (thanks to [josh-richardson](https://github.com/josh-richardson))
- Add EthSigner acceptance test [\#1655](https://github.com/PegaSysEng/pantheon/pull/1655) (thanks to [iikirilov](https://github.com/iikirilov))
- Support plugin Richdata APIs via implementation [\#1581](https://github.com/PegaSysEng/pantheon/pull/1581)

## 1.2.1

### Additions and Improvements

- Removed the release plugin in favour of the new process with branches
[#1841](https://github.com/PegaSysEng/pantheon/pull/1841)
[#1843](https://github.com/PegaSysEng/pantheon/pull/1843)
[#1848](https://github.com/PegaSysEng/pantheon/pull/1848)
[#1855](https://github.com/PegaSysEng/pantheon/pull/1855)
- Updated Görli bootnodes [#1842](https://github.com/PegaSysEng/pantheon/pull/1842)
- Removed unnecessary test dependency [#1839](https://github.com/PegaSysEng/pantheon/pull/1839)
- Added warning when comments are used in genesis file [#1838](https://github.com/PegaSysEng/pantheon/pull/1838)
- Added an experimental flag for disabling timers [#1837](https://github.com/PegaSysEng/pantheon/pull/1837)
- Fixed FlatFileTaskCollection tests [#1833](https://github.com/PegaSysEng/pantheon/pull/1833)
- Added chain json import utility [#1832](https://github.com/PegaSysEng/pantheon/pull/1832)
- Added tests to AllNodesVisitor trie traversal [#1831](https://github.com/PegaSysEng/pantheon/pull/1831)
- Updated privateFrom to be required [#1829](https://github.com/PegaSysEng/pantheon/pull/1829) (thanks to [iikirilov](https://github.com/iikirilov))
- Made explicit that streamed accounts may be missing their address [#1828](https://github.com/PegaSysEng/pantheon/pull/1828)
- Refactored normalizeKeys method [#1826](https://github.com/PegaSysEng/pantheon/pull/1826)
- Removed dead parameters [#1825](https://github.com/PegaSysEng/pantheon/pull/1825)
- Added a nicer name for Corretto [#1819](https://github.com/PegaSysEng/pantheon/pull/1819)
- Changed core JSON-RPC method to support ReTestEth
[#1815](https://github.com/PegaSysEng/pantheon/pull/1815)
[#1818](https://github.com/PegaSysEng/pantheon/pull/1818)
- Added rewind to block functionality [#1814](https://github.com/PegaSysEng/pantheon/pull/1814)
- Added support for NoReward and NoProof seal engines [#1813](https://github.com/PegaSysEng/pantheon/pull/1813)
- Added strict short hex strings for retesteth [#1812](https://github.com/PegaSysEng/pantheon/pull/1812)
- Cleaned up genesis parsing [#1809](https://github.com/PegaSysEng/pantheon/pull/1809)
- Updating Orion to v1.3.2 [#1805](https://github.com/PegaSysEng/pantheon/pull/1805)
- Updaated newHeads subscription to emit events only for canonical blocks [#1798](https://github.com/PegaSysEng/pantheon/pull/1798)
- Repricing for trie-size-dependent opcodes [#1795](https://github.com/PegaSysEng/pantheon/pull/1795)
- Revised Istanbul Versioning assignemnts [#1794](https://github.com/PegaSysEng/pantheon/pull/1794)
- Updated RevertReason to return BytesValue [#1793](https://github.com/PegaSysEng/pantheon/pull/1793)
- Updated way priv_getPrivacyPrecompileAddress source [#1786](https://github.com/PegaSysEng/pantheon/pull/1786) (thanks to [iikirilov](https://github.com/iikirilov))
- Updated Chain ID opcode to return 0 as default [#1785](https://github.com/PegaSysEng/pantheon/pull/1785)
- Allowed fixedDifficulty=1 [#1784](https://github.com/PegaSysEng/pantheon/pull/1784)
- Updated Docker image defaults host interfaces [#1782](https://github.com/PegaSysEng/pantheon/pull/1782)
- Added tracking of world state account key preimages [#1780](https://github.com/PegaSysEng/pantheon/pull/1780)
- Modified PrivGetPrivateTransaction to take public tx hash [#1778](https://github.com/PegaSysEng/pantheon/pull/1778) (thanks to [josh-richardson](https://github.com/josh-richardson))
- Removed enclave public key from parameter
[#1789](https://github.com/PegaSysEng/pantheon/pull/1789)
[#1777](https://github.com/PegaSysEng/pantheon/pull/1777) (thanks to [iikirilov](https://github.com/iikirilov))
- Added storage key preimage tracking [#1772](https://github.com/PegaSysEng/pantheon/pull/1772)
- Updated priv_getPrivacyPrecompileAddress method return [#1766](https://github.com/PegaSysEng/pantheon/pull/1766) (thanks to [iikirilov](https://github.com/iikirilov))
- Added tests for permissioning with static nodes behaviour [#1764](https://github.com/PegaSysEng/pantheon/pull/1764)
- Added integration test for contract creation with privacyGroupId [#1762](https://github.com/PegaSysEng/pantheon/pull/1762) (thanks to [josh-richardson](https://github.com/josh-richardson))
- Added report node local address as the coinbase in Clique and IBFT
[#1758](https://github.com/PegaSysEng/pantheon/pull/1758)
[#1760](https://github.com/PegaSysEng/pantheon/pull/1760)
- Fixed private tx signature validation [#1753](https://github.com/PegaSysEng/pantheon/pull/1753)
- Updated CI configuration
[#1751](https://github.com/PegaSysEng/pantheon/pull/1751)
[#1835](https://github.com/PegaSysEng/pantheon/pull/1835)
- Added CLI flag for setting WorldStateDownloader task cache size [#1749](https://github.com/PegaSysEng/pantheon/pull/1749) (thanks to [matkt](https://github.com/matkt))
- Updated vertx to 2.8.0 [#1748](https://github.com/PegaSysEng/pantheon/pull/1748)
- changed RevertReason to BytesValue [#1746](https://github.com/PegaSysEng/pantheon/pull/1746)
- Added static nodes acceptance test [#1745](https://github.com/PegaSysEng/pantheon/pull/1745)
- Added report 0 hashrate when the mining coordinator doesn't support mining
[#1744](https://github.com/PegaSysEng/pantheon/pull/1744)
[#1757](https://github.com/PegaSysEng/pantheon/pull/1757)
- Implemented EIP-2200 - Net Gas Metering Revised [#1743](https://github.com/PegaSysEng/pantheon/pull/1743)
- Added chainId validation to PrivateTransactionValidator [#1741](https://github.com/PegaSysEng/pantheon/pull/1741)
- Reduced intrinsic gas cost [#1739](https://github.com/PegaSysEng/pantheon/pull/1739)
- De-duplicated test blocks data files [#1737](https://github.com/PegaSysEng/pantheon/pull/1737)
- Renamed various EEA methods to priv methods [#1736](https://github.com/PegaSysEng/pantheon/pull/1736) (thanks to [josh-richardson](https://github.com/josh-richardson))
- Permissioning Acceptance Test [#1735](https://github.com/PegaSysEng/pantheon/pull/1735)
 [#1759](https://github.com/PegaSysEng/pantheon/pull/1759)
- Add nonce handling to GenesisState [#1728](https://github.com/PegaSysEng/pantheon/pull/1728)
- Added 100-continue to HTTP [#1727](https://github.com/PegaSysEng/pantheon/pull/1727)
- Fixed get_signerMetrics [#1725](https://github.com/PegaSysEng/pantheon/pull/1725) (thanks to [matkt](https://github.com/matkt))
- Reworked "in-sync" checks [#1720](https://github.com/PegaSysEng/pantheon/pull/1720)
- Added Accounts Permissioning Acceptance Tests [#1719](https://github.com/PegaSysEng/pantheon/pull/1719)
- Added PrivateTransactionValidator to unify logic [#1713](https://github.com/PegaSysEng/pantheon/pull/1713)
- Added JSON-RPC API to report validator block production information [#1687](https://github.com/PegaSysEng/pantheon/pull/1687) (thanks to [matkt](https://github.com/matkt))
- Added Mark Sweep Pruner [#1638](https://github.com/PegaSysEng/pantheon/pull/1638)
- Added the Blake2b F compression function as a precompile in Besu [#1614](https://github.com/PegaSysEng/pantheon/pull/1614) (thanks to [iikirilov](https://github.com/iikirilov))
- Documentation updates include:
  - Added CPU requirements [#1734](https://github.com/PegaSysEng/pantheon/pull/1734)
  - Added reference to Ansible role [#1733](https://github.com/PegaSysEng/pantheon/pull/1733)
  - Updated revert reason example [#1754](https://github.com/PegaSysEng/pantheon/pull/1754)
  - Added content on deploying for production [#1774](https://github.com/PegaSysEng/pantheon/pull/1774)
  - Updated docker docs for location of data path [#1790](https://github.com/PegaSysEng/pantheon/pull/1790)
  - Updated permissiong documentation
  [#1792](https://github.com/PegaSysEng/pantheon/pull/1792)
  [#1652](https://github.com/PegaSysEng/pantheon/pull/1652)
  - Added permissioning webinar in the resources [#1717](https://github.com/PegaSysEng/pantheon/pull/1717)
  - Add web3.js-eea reference doc [#1617](https://github.com/PegaSysEng/pantheon/pull/1617)
  - Updated privacy documentation
  [#1650](https://github.com/PegaSysEng/pantheon/pull/1650)
  [#1721](https://github.com/PegaSysEng/pantheon/pull/1721)
  [#1722](https://github.com/PegaSysEng/pantheon/pull/1722)
  [#1724](https://github.com/PegaSysEng/pantheon/pull/1724)
  [#1729](https://github.com/PegaSysEng/pantheon/pull/1729)
  [#1730](https://github.com/PegaSysEng/pantheon/pull/1730)
  [#1731](https://github.com/PegaSysEng/pantheon/pull/1731)
  [#1732](https://github.com/PegaSysEng/pantheon/pull/1732)
  [#1740](https://github.com/PegaSysEng/pantheon/pull/1740)
  [#1750](https://github.com/PegaSysEng/pantheon/pull/1750)
  [#1761](https://github.com/PegaSysEng/pantheon/pull/1761)
  [#1765](https://github.com/PegaSysEng/pantheon/pull/1765)
  [#1769](https://github.com/PegaSysEng/pantheon/pull/1769)
  [#1770](https://github.com/PegaSysEng/pantheon/pull/1770)
  [#1771](https://github.com/PegaSysEng/pantheon/pull/1771)
  [#1773](https://github.com/PegaSysEng/pantheon/pull/1773)
  [#1787](https://github.com/PegaSysEng/pantheon/pull/1787)
  [#1788](https://github.com/PegaSysEng/pantheon/pull/1788)
  [#1796](https://github.com/PegaSysEng/pantheon/pull/1796)
  [#1803](https://github.com/PegaSysEng/pantheon/pull/1803)
  [#1810](https://github.com/PegaSysEng/pantheon/pull/1810)
  [#1817](https://github.com/PegaSysEng/pantheon/pull/1817)
  - Added documentation for getSignerMetrics [#1723](https://github.com/PegaSysEng/pantheon/pull/1723) (thanks to [matkt](https://github.com/matkt))
  - Added Java 11+ as a prerequisite for installing Besu using Homebrew. [#1755](https://github.com/PegaSysEng/pantheon/pull/1755)
  - Fixed documentation formatting and typos [#1718](https://github.com/PegaSysEng/pantheon/pull/1718)
  [#1742](https://github.com/PegaSysEng/pantheon/pull/1742)
  [#1763](https://github.com/PegaSysEng/pantheon/pull/1763)
  [#1779](https://github.com/PegaSysEng/pantheon/pull/1779)
  [#1781](https://github.com/PegaSysEng/pantheon/pull/1781)
  [#1827](https://github.com/PegaSysEng/pantheon/pull/1827)
  [#1767](https://github.com/PegaSysEng/pantheon/pull/1767) (thanks to [helderjnpinto](https://github.com/helderjnpinto))
  - Moved the docs to a [new doc repos](https://github.com/PegaSysEng/doc.pantheon) [#1822](https://github.com/PegaSysEng/pantheon/pull/1822)
- Explicitly configure some maven artifactIds [#1853](https://github.com/PegaSysEng/pantheon/pull/1853)
- Update export subcommand to export blocks in rlp format [#1852](https://github.com/PegaSysEng/pantheon/pull/1852)
- Implement `eth_getproof` JSON RPC API [#1824](https://github.com/PegaSysEng/pantheon/pull/1824)
- Database versioning and enable multi-column database [#1830](https://github.com/PegaSysEng/pantheon/pull/1830)
- Disable smoke tests on windows [#1847](https://github.com/PegaSysEng/pantheon/pull/1847)
- Add read-only blockchain factory method [#1845](https://github.com/PegaSysEng/pantheon/pull/1845)

## 1.2

### Additions and Improvements

- Add UPnP Support [\#1334](https://github.com/PegaSysEng/pantheon/pull/1334) (thanks to [notlesh](https://github.com/notlesh))
- Limit the fraction of wire connections initiated by peers [\#1665](https://github.com/PegaSysEng/pantheon/pull/1665)
- EIP-1706 - Disable SSTORE with gasleft lt call stipend  [\#1706](https://github.com/PegaSysEng/pantheon/pull/1706)
- EIP-1108 - Reprice alt\_bn128 [\#1704](https://github.com/PegaSysEng/pantheon/pull/1704)
- EIP-1344 ChainID Opcode [\#1690](https://github.com/PegaSysEng/pantheon/pull/1690)
- New release docker image [\#1664](https://github.com/PegaSysEng/pantheon/pull/1664)
- Support changing log level at runtime [\#1656](https://github.com/PegaSysEng/pantheon/pull/1656) (thanks to [matkt](https://github.com/matkt))
- Implement dump command to dump a specific block from storage [\#1641](https://github.com/PegaSysEng/pantheon/pull/1641) (thanks to [matkt](https://github.com/matkt))
- Add eea\_findPrivacyGroup endpoint to Besu [\#1635](https://github.com/PegaSysEng/pantheon/pull/1635) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Updated eea send raw transaction with privacy group ID [\#1611](https://github.com/PegaSysEng/pantheon/pull/1611) (thanks to [iikirilov](https://github.com/iikirilov))
- Added Revert Reason [\#1603](https://github.com/PegaSysEng/pantheon/pull/1603)
- Documentation updates include:
  - Added [UPnP content](https://besu.hyperledger.org/en/latest/HowTo/Find-and-Connect/Using-UPnP/)
  - Added [load balancer image](https://besu.hyperledger.org/en/stable/)
  - Added [revert reason](https://besu.hyperledger.org/en/latest/HowTo/Send-Transactions/Revert-Reason/)
  - Added [admin\_changeLogLevel](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#admin_changeloglevel) JSON RPC API (thanks to [matkt](https://github.com/matkt))
  - Updated for [new Docker image](https://besu.hyperledger.org/en/stable/)
  - Added [Docker image migration content](https://besu.hyperledger.org/en/latest/HowTo/Get-Started/Migration-Docker/)
  - Added [transaction validation content](https://besu.hyperledger.org/en/latest/Concepts/Transactions/Transaction-Validation/)
  - Updated [permissioning overview](https://besu.hyperledger.org/en/stable/) for onchain account permissioning
  - Updated [quickstart](https://besu.hyperledger.org/en/latest/HowTo/Deploy/Monitoring-Performance/#monitor-node-performance-using-prometheus) to include Prometheus and Grafana
  - Added [remote connections limits options](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#remote-connections-limit-enabled)
  - Updated [web3.js-eea reference](https://docs.pantheon.pegasys.tech/en/latest/Reference/web3js-eea-Methods/) to include privacy group methods
  - Updated [onchain permissioning to include account permissioning](hhttps://besu.hyperledger.org/en/latest/Concepts/Permissioning/Onchain-Permissioning/) and [Permissioning Management Dapp](https://besu.hyperledger.org/en/latest/Tutorials/Permissioning/Getting-Started-Onchain-Permissioning/#start-the-development-server-for-the-permissioning-management-dapp)
  - Added [deployment procedure for Permissioning Management Dapp](https://besu.hyperledger.org/en/stable/)
  - Added privacy content for [EEA-compliant and Besu-extended privacy](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Privacy-Groups/)
  - Added content on [creating and managing privacy groups](https://besu.hyperledger.org/en/latest/Reference/web3js-eea-Methods/#createprivacygroup)
  - Added content on [accessing private and privacy marker transactions](https://besu.hyperledger.org/en/latest/HowTo/Use-Privacy/Access-Private-Transactions/)
  - Added content on [system requirements](https://besu.hyperledger.org/en/latest/HowTo/Get-Started/System-Requirements/)
  - Added reference to [Besu role on Galaxy to deploy using Ansible](https://besu.hyperledger.org/en/latest/HowTo/Deploy/Ansible/).

### Technical Improvements

- Remove enclave public key from parameter [\#1789](https://github.com/PegaSysEng/pantheon/pull/1789)
- Update defaults host interfaces [\#1782](https://github.com/PegaSysEng/pantheon/pull/1782)
- Modifies PrivGetPrivateTransaction to take public tx hash [\#1778](https://github.com/PegaSysEng/pantheon/pull/1778)
- Remove enclave public key from parameter [\#1777](https://github.com/PegaSysEng/pantheon/pull/1777)
- Return the ethereum address of the privacy precompile from priv_getPrivacyPrecompileAddress [\#1766](https://github.com/PegaSysEng/pantheon/pull/1766)
- Report node local address as the coinbase in Clique and IBFT [\#1760](https://github.com/PegaSysEng/pantheon/pull/1760)
- Additional integration test for contract creation with privacyGroupId [\#1762](https://github.com/PegaSysEng/pantheon/pull/1762)
- Report 0 hashrate when the mining coordinator doesn't support mining [\#1757](https://github.com/PegaSysEng/pantheon/pull/1757)
- Fix private tx signature validation [\#1753](https://github.com/PegaSysEng/pantheon/pull/1753)
- RevertReason changed to BytesValue [\#1746](https://github.com/PegaSysEng/pantheon/pull/1746)
- Renames various eea methods to priv methods [\#1736](https://github.com/PegaSysEng/pantheon/pull/1736)
- Update Orion version [\#1716](https://github.com/PegaSysEng/pantheon/pull/1716)
- Rename CLI flag for better ordering of options [\#1715](https://github.com/PegaSysEng/pantheon/pull/1715)
- Routine dependency updates [\#1712](https://github.com/PegaSysEng/pantheon/pull/1712)
- Fix spelling error in getApplicationPrefix method name [\#1711](https://github.com/PegaSysEng/pantheon/pull/1711)
- Wait and retry if best peer's chain is too short for fast sync [\#1708](https://github.com/PegaSysEng/pantheon/pull/1708)
- Eea get private transaction fix [\#1707](https://github.com/PegaSysEng/pantheon/pull/1707) (thanks to [iikirilov](https://github.com/iikirilov))
- Rework remote connection limit flag defaults [\#1705](https://github.com/PegaSysEng/pantheon/pull/1705)
- Report invalid options from config file [\#1703](https://github.com/PegaSysEng/pantheon/pull/1703)
- Add ERROR to list of CLI log level options [\#1699](https://github.com/PegaSysEng/pantheon/pull/1699)
- Enable onchain account permissioning CLI option [\#1686](https://github.com/PegaSysEng/pantheon/pull/1686)
- Exempt static nodes from all connection limits [\#1685](https://github.com/PegaSysEng/pantheon/pull/1685)
- Enclave refactoring [\#1684](https://github.com/PegaSysEng/pantheon/pull/1684)
- Add opcode and precompiled support for versioning  [\#1683](https://github.com/PegaSysEng/pantheon/pull/1683)
- Use a percentage instead of fraction for the remote connections percentage CLI option. [\#1682](https://github.com/PegaSysEng/pantheon/pull/1682)
- Added error msg for calling eth\_sendTransaction [\#1681](https://github.com/PegaSysEng/pantheon/pull/1681)
- Remove instructions for installing with Chocolatey [\#1680](https://github.com/PegaSysEng/pantheon/pull/1680)
- remove zulu-jdk8 from smoke tests [\#1679](https://github.com/PegaSysEng/pantheon/pull/1679)
- Add new MainNet bootnodes [\#1678](https://github.com/PegaSysEng/pantheon/pull/1678)
- updating smoke tests to use \>= jdk11 [\#1677](https://github.com/PegaSysEng/pantheon/pull/1677)
- Fix handling of remote connection limit [\#1676](https://github.com/PegaSysEng/pantheon/pull/1676)
- Add accountVersion to MessageFrame [\#1675](https://github.com/PegaSysEng/pantheon/pull/1675)
- Change getChildren return type [\#1674](https://github.com/PegaSysEng/pantheon/pull/1674)
- Use Log4J message template instead of String.format [\#1673](https://github.com/PegaSysEng/pantheon/pull/1673)
- Return hashrate of 0 when not mining. [\#1672](https://github.com/PegaSysEng/pantheon/pull/1672)
- Add hooks for validation  [\#1671](https://github.com/PegaSysEng/pantheon/pull/1671)
- Upgrade to pantheon-build:0.0.6-jdk11 which really does include jdk11 [\#1670](https://github.com/PegaSysEng/pantheon/pull/1670)
- Onchain permissioning startup check [\#1669](https://github.com/PegaSysEng/pantheon/pull/1669)
- Update BesuCommand to accept minTransactionGasPriceWei as an integer [\#1668](https://github.com/PegaSysEng/pantheon/pull/1668) (thanks to [matkt](https://github.com/matkt))
- Privacy group id consistent [\#1667](https://github.com/PegaSysEng/pantheon/pull/1667) (thanks to [iikirilov](https://github.com/iikirilov))
- Change eea\_getPrivateTransaction endpoint to accept hex [\#1666](https://github.com/PegaSysEng/pantheon/pull/1666) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Factorise metrics code for KeyValueStorage database [\#1663](https://github.com/PegaSysEng/pantheon/pull/1663))
- Create a metric tracking DB size [\#1662](https://github.com/PegaSysEng/pantheon/pull/1662)
- AT- Removing unused methods on KeyValueStorage [\#1661](https://github.com/PegaSysEng/pantheon/pull/1661)
- Add Prerequisites and Quick-Start [\#1660](https://github.com/PegaSysEng/pantheon/pull/1660) (thanks to [lazaridiscom](https://github.com/lazaridiscom))
- Java 11 updates [\#1658](https://github.com/PegaSysEng/pantheon/pull/1658)
- Make test generated keys deterministic w/in block generator [\#1657](https://github.com/PegaSysEng/pantheon/pull/1657)
- Rename privacyGroupId to createPrivacyGroupId [\#1654](https://github.com/PegaSysEng/pantheon/pull/1654) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Intermittent Test Failures in TransactionsMessageSenderTest [\#1653](https://github.com/PegaSysEng/pantheon/pull/1653)
- Sanity check the generated distribution files before upload [\#1648](https://github.com/PegaSysEng/pantheon/pull/1648)
- Use JDK 11 for release builds [\#1647](https://github.com/PegaSysEng/pantheon/pull/1647)
- Support multiple private marker transactions in a block  [\#1646](https://github.com/PegaSysEng/pantheon/pull/1646)
- Display World State Sync Progress in Logs [\#1645](https://github.com/PegaSysEng/pantheon/pull/1645)
- Remove the docker gradle plugin, handle building docker with shell now [\#1644](https://github.com/PegaSysEng/pantheon/pull/1644)
- Switch to using metric names from EIP-2159 [\#1634](https://github.com/PegaSysEng/pantheon/pull/1634)
- Account versioning [\#1612](https://github.com/PegaSysEng/pantheon/pull/1612)

## 1.1.4

### Additions and Improvements

- \[PAN-2832\] Support setting config options via environment variables [\#1597](https://github.com/PegaSysEng/pantheon/pull/1597)
- Print Besu version when starting [\#1593](https://github.com/PegaSysEng/pantheon/pull/1593)
- \[PAN-2746\] Add eea\_createPrivacyGroup & eea\_deletePrivacyGroup endpoint [\#1560](https://github.com/PegaSysEng/pantheon/pull/1560) (thanks to [Puneetha17](https://github.com/Puneetha17))

Documentation updates include:
- Added [readiness and liveness endpoints](https://besu.hyperledger.org/en/latest/HowTo/Interact/APIs/Using-JSON-RPC-API/#readiness-and-liveness-endpoints)
- Added [high availability content](https://besu.hyperledger.org/en/latest/HowTo/Configure/Configure-HA/High-Availability/)
- Added [web3js-eea client library](https://besu.hyperledger.org/en/latest/Tutorials/Quickstarts/Privacy-Quickstart/#clone-eeajs-libraries)
- Added content on [setting CLI options using environment variables](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#specifying-options)

### Technical Improvements

- Read config from env vars when no config file specified [\#1639](https://github.com/PegaSysEng/pantheon/pull/1639)
- Upgrade jackson-databind to 2.9.9.1 [\#1636](https://github.com/PegaSysEng/pantheon/pull/1636)
- Update Reference Tests [\#1633](https://github.com/PegaSysEng/pantheon/pull/1633)
- Ignore discport during static node permissioning check [\#1631](https://github.com/PegaSysEng/pantheon/pull/1631)
- Check connections more frequently during acceptance tests [\#1630](https://github.com/PegaSysEng/pantheon/pull/1630)
- Refactor experimental CLI options [\#1629](https://github.com/PegaSysEng/pantheon/pull/1629)
- JSON-RPC api net_services should display the actual ports [\#1628](https://github.com/PegaSysEng/pantheon/pull/1628)
- Refactor CLI [\#1627](https://github.com/PegaSysEng/pantheon/pull/1627)
- Simplify BesuCommand `run` and `parse` methods. [\#1626](https://github.com/PegaSysEng/pantheon/pull/1626)
- PAN-2860: Ignore discport during startup whitelist validation [\#1625](https://github.com/PegaSysEng/pantheon/pull/1625)
- Freeze plugin api version [\#1624](https://github.com/PegaSysEng/pantheon/pull/1624)
- Implement incoming transaction messages CLI option as an unstable command. [\#1622](https://github.com/PegaSysEng/pantheon/pull/1622)
- Update smoke tests docker images for zulu and openjdk to private ones [\#1620](https://github.com/PegaSysEng/pantheon/pull/1620)
- Remove duplication between EeaTransactionCountRpc & PrivateTransactionHandler [\#1619](https://github.com/PegaSysEng/pantheon/pull/1619)
- \[PAN-2709\] - nonce too low error [\#1618](https://github.com/PegaSysEng/pantheon/pull/1618)
- Cache TransactionValidationParams instead of creating new object for each call [\#1616](https://github.com/PegaSysEng/pantheon/pull/1616)
- \[PAN-2850\] Create a transaction pool configuration object [\#1615](https://github.com/PegaSysEng/pantheon/pull/1615)
- Add TransactionValidationParam to TxProcessor [\#1613](https://github.com/PegaSysEng/pantheon/pull/1613)
- Expose a CLI option to configure the life time of transaction messages. [\#1610](https://github.com/PegaSysEng/pantheon/pull/1610)
- Implement Prometheus metric counter for skipped expired transaction messages. [\#1609](https://github.com/PegaSysEng/pantheon/pull/1609)
- Upload jars to bintray as part of releases [\#1608](https://github.com/PegaSysEng/pantheon/pull/1608)
- Avoid publishing docker-pantheon directory to bintray during a release [\#1606](https://github.com/PegaSysEng/pantheon/pull/1606)
- \[PAN-2756\] Istanbul scaffolding [\#1605](https://github.com/PegaSysEng/pantheon/pull/1605)
- Implement a timeout in TransactionMessageProcessor [\#1604](https://github.com/PegaSysEng/pantheon/pull/1604)
- Reject transactions with gas price below the configured minimum [\#1602](https://github.com/PegaSysEng/pantheon/pull/1602)
- Always build the k8s image, only push to dockerhub for master branch [\#1601](https://github.com/PegaSysEng/pantheon/pull/1601)
- Properly validate AltBN128 pairing precompile input [\#1600](https://github.com/PegaSysEng/pantheon/pull/1600)
- \[PAN-2871\] Columnar rocksdb [\#1599](https://github.com/PegaSysEng/pantheon/pull/1599)
- Reverting change to dockerfile [\#1594](https://github.com/PegaSysEng/pantheon/pull/1594)
- Update dependency versions [\#1592](https://github.com/PegaSysEng/pantheon/pull/1592)
- \[PAN-2797\] Clean up failed connections [\#1591](https://github.com/PegaSysEng/pantheon/pull/1591)
- Cleaning up the build process for docker [\#1590](https://github.com/PegaSysEng/pantheon/pull/1590)
- \[PAN-2786\] Stop Transaction Pool Queue from Growing Unbounded [\#1586](https://github.com/PegaSysEng/pantheon/pull/1586)

## 1.1.3

### Additions and Improvements

- \[PAN-2811\] Be more lenient with discovery message deserialization. Completes our support for EIP-8 and enables Besu to work on Rinkeby again. [\#1580](https://github.com/PegaSysEng/pantheon/pull/1580)
- Added liveness and readiness probe stub endpoints [\#1553](https://github.com/PegaSysEng/pantheon/pull/1553)
- Implemented operator tool. \(blockchain network configuration for permissioned networks\) [\#1511](https://github.com/PegaSysEng/pantheon/pull/1511)
- \[PAN-2754\] Added eea\_getPrivacyPrecompileAddress [\#1579](https://github.com/PegaSysEng/pantheon/pull/1579) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Publish the chain head gas used, gas limit, transaction count and ommer metrics [\#1551](https://github.com/PegaSysEng/pantheon/pull/1551)
- Add subscribe and unsubscribe count metrics [\#1541](https://github.com/PegaSysEng/pantheon/pull/1541)
- Add pivot block metrics [\#1537](https://github.com/PegaSysEng/pantheon/pull/1537)

Documentation updates include:

- Updated [IBFT 2.0 tutorial](https://besu.hyperledger.org/en/latest/Tutorials/Private-Network/Create-IBFT-Network/) to use network configuration tool
- Added [debug\_traceBlock\* methods](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#debug_traceblock)
- Reorganised [monitoring documentation](https://besu.hyperledger.org/en/latest/HowTo/Deploy/Monitoring-Performance/)
- Added [link to sample Grafana dashboard](https://besu.hyperledger.org/en/latest/HowTo/Deploy/Monitoring-Performance/#monitor-node-performance-using-prometheus)
- Added [note about replacing transactions in transaction pool](https://besu.hyperledger.org/en/latest/Concepts/Transactions/Transaction-Pool/#replacing-transactions-with-same-nonce)
- Updated [example transaction scripts](https://besu.hyperledger.org/en/latest/HowTo/Send-Transactions/Transactions/#example-javascript-scripts)
- Updated [Alethio Ethstats and Explorer documentation](https://besu.hyperledger.org/en/latest/Concepts/AlethioOverview/)

### Technical Improvements

- PAN-2816: Hiding experimental account permissioning cli options [\#1584](https://github.com/PegaSysEng/pantheon/pull/1584)
- \[PAN-2630\] Synchronizer should disconnect the sync target peer on invalid block data [\#1578](https://github.com/PegaSysEng/pantheon/pull/1578)
- Rename MetricCategory to BesuMetricCategory [\#1574](https://github.com/PegaSysEng/pantheon/pull/1574)
- Convert MetricsConfigiguration to use a builder [\#1572](https://github.com/PegaSysEng/pantheon/pull/1572)
- PAN-2794: Including flag for onchain permissioning check on tx processor [\#1571](https://github.com/PegaSysEng/pantheon/pull/1571)
- Fix behaviour for absent account permissiong smart contract [\#1569](https://github.com/PegaSysEng/pantheon/pull/1569)
- Expand readiness check to check peer count and sync state [\#1568](https://github.com/PegaSysEng/pantheon/pull/1568)
- \[PAN-2798\] Reorganize p2p classes [\#1567](https://github.com/PegaSysEng/pantheon/pull/1567)
- PAN-2729: Account Smart Contract Permissioning ATs [\#1565](https://github.com/PegaSysEng/pantheon/pull/1565)
- Timeout build after 1 hour to prevent it hanging forever. [\#1564](https://github.com/PegaSysEng/pantheon/pull/1564)
- \[PAN-2791\] Make permissions checks for ongoing connections more granular [\#1563](https://github.com/PegaSysEng/pantheon/pull/1563)
- \[PAN-2721\] Fix TopicParameter deserialization [\#1562](https://github.com/PegaSysEng/pantheon/pull/1562)
- \[PAN-2779\] Allow signing private transaction with any key [\#1561](https://github.com/PegaSysEng/pantheon/pull/1561) (thanks to [iikirilov](https://github.com/iikirilov))
- \[PAN-2783\] Invert dependency between permissioning and p2p [\#1557](https://github.com/PegaSysEng/pantheon/pull/1557)
- Removing account filter from TransactionPool [\#1556](https://github.com/PegaSysEng/pantheon/pull/1556)
- \[PAN-1952\] - Remove ignored pending transaction event publish acceptance test [\#1552](https://github.com/PegaSysEng/pantheon/pull/1552)
- Make MetricCategories more flexible [\#1550](https://github.com/PegaSysEng/pantheon/pull/1550)
- Fix encoding for account permissioning check call [\#1549](https://github.com/PegaSysEng/pantheon/pull/1549)
- Discard known remote transactions prior to validation [\#1548](https://github.com/PegaSysEng/pantheon/pull/1548)
- \[PAN-2009\] - Fix cluster clean start after stop in Acceptance tests [\#1546](https://github.com/PegaSysEng/pantheon/pull/1546)
- FilterIdGenerator fixes [\#1544](https://github.com/PegaSysEng/pantheon/pull/1544)
- Only increment the added transaction counter if we actually added the transaction [\#1543](https://github.com/PegaSysEng/pantheon/pull/1543)
- When retrieving transactions by hash, check the pending transactions first [\#1542](https://github.com/PegaSysEng/pantheon/pull/1542)
- Fix thread safety in SubscriptionManager [\#1540](https://github.com/PegaSysEng/pantheon/pull/1540)
- \[PAN-2731\] Extract connection management from P2PNetwork [\#1538](https://github.com/PegaSysEng/pantheon/pull/1538)
- \[PAN-2010\] format filter id as quantity [\#1534](https://github.com/PegaSysEng/pantheon/pull/1534)
- PAN-2445: Onchain account permissioning [\#1507](https://github.com/PegaSysEng/pantheon/pull/1507)
- \[PAN-2672\] Return specific and useful error for enclave issues [\#1455](https://github.com/PegaSysEng/pantheon/pull/1455) (thanks to [Puneetha17](https://github.com/Puneetha17))

## 1.1.2

### Additions and Improvements

Documentation updates include:

- Added [GraphQL options](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#graphql-http-cors-origins)
- Added [troubleshooting point about illegal reflective access error](https://besu.hyperledger.org/en/latest/HowTo/Troubleshoot/Troubleshooting/#illegal-reflective-access-error-on-startup)
- Added [trusted bootnode behaviour for permissioning](https://besu.hyperledger.org/en/latest/Concepts/Permissioning/Onchain-Permissioning/#bootnodes)
- Added [how to obtain a WS authentication token](https://besu.hyperledger.org/en/latest/HowTo/Interact/APIs/Authentication/#obtaining-an-authentication-token)
- Updated [example scripts and added package.json file for creating signed transactions](https://besu.hyperledger.org/en/latest/HowTo/Send-Transactions/Transactions/)

### Technical Improvements

- Replaced Void datatype with void [\#1530](https://github.com/PegaSysEng/pantheon/pull/1530)
- Fix estimate gas RPC failing for clique when no blocks have been created [\#1528](https://github.com/PegaSysEng/pantheon/pull/1528)
- Avoid auto-boxing for gauge metrics [\#1526](https://github.com/PegaSysEng/pantheon/pull/1526)
- Add AT to ensure 0-miner Clique/IBFT are valid [\#1525](https://github.com/PegaSysEng/pantheon/pull/1525)
- AT DSL - renaming to suffix of Conditions and co-locating with Conditions [\#1524](https://github.com/PegaSysEng/pantheon/pull/1524)
- Set disconnect flag immediately when disconnecting a peer [\#1521](https://github.com/PegaSysEng/pantheon/pull/1521)
- \[PAN-2547\] Modified JSON-RPC subscription processing to avoid blocking [\#1519](https://github.com/PegaSysEng/pantheon/pull/1519)
- Dependency Version Updates [\#1517](https://github.com/PegaSysEng/pantheon/pull/1517)
- AT DSL - renaming ibft to ibft2 [\#1516](https://github.com/PegaSysEng/pantheon/pull/1516)
- \[PIE-1578\] Added local transaction permissioning metrics [\#1515](https://github.com/PegaSysEng/pantheon/pull/1515)
- \[PIE-1577\] Added node local metrics [\#1514](https://github.com/PegaSysEng/pantheon/pull/1514)
- AT DSL - Removing WaitCondition, consistently applying Condition instead [\#1513](https://github.com/PegaSysEng/pantheon/pull/1513)
- Remove usage of deprecated ConcurrentSet [\#1512](https://github.com/PegaSysEng/pantheon/pull/1512)
- Log error if clique or ibft have 0 validators in genesis [\#1509](https://github.com/PegaSysEng/pantheon/pull/1509)
- GraphQL library upgrade changes. [\#1508](https://github.com/PegaSysEng/pantheon/pull/1508)
- Add metrics to assist monitoring and alerting [\#1506](https://github.com/PegaSysEng/pantheon/pull/1506)
- Use external pantheon-plugin-api library [\#1505](https://github.com/PegaSysEng/pantheon/pull/1505)
- Tilde [\#1504](https://github.com/PegaSysEng/pantheon/pull/1504)
- Dependency version updates [\#1503](https://github.com/PegaSysEng/pantheon/pull/1503)
- Simplify text [\#1501](https://github.com/PegaSysEng/pantheon/pull/1501) (thanks to [bgravenorst](https://github.com/bgravenorst))
- \[PAN-1625\] Clique AT mining continues if validator offline [\#1500](https://github.com/PegaSysEng/pantheon/pull/1500)
- Acceptance Test DSL Node refactoring [\#1498](https://github.com/PegaSysEng/pantheon/pull/1498)
- Updated an incorrect command [\#1497](https://github.com/PegaSysEng/pantheon/pull/1497) (thanks to [bgravenorst](https://github.com/bgravenorst))
- Acceptance Test and DSL rename for IBFT2 [\#1493](https://github.com/PegaSysEng/pantheon/pull/1493)
- \[PIE-1580\] Metrics for smart contract permissioning actions [\#1492](https://github.com/PegaSysEng/pantheon/pull/1492)
- Handle RLPException when processing incoming DevP2P messages [\#1491](https://github.com/PegaSysEng/pantheon/pull/1491)
- Limit spotless checks to java classes in expected java  dirs [\#1490](https://github.com/PegaSysEng/pantheon/pull/1490)
- \[PAN-2560\] Add LocalNode class [\#1489](https://github.com/PegaSysEng/pantheon/pull/1489)
- Changed Enode length error String implementation. [\#1486](https://github.com/PegaSysEng/pantheon/pull/1486)
- PAN-2715 - return block not found reasons in error [\#1485](https://github.com/PegaSysEng/pantheon/pull/1485)
- \[PAN-2652\] Refactor Privacy acceptance test and add Privacy Ibft test [\#1483](https://github.com/PegaSysEng/pantheon/pull/1483) (thanks to [iikirilov](https://github.com/iikirilov))
- \[PAN-2603\] Onchain account permissioning support [\#1475](https://github.com/PegaSysEng/pantheon/pull/1475)
- Make CLI options names with hyphen-minus searchable and reduce index size [\#1476](https://github.com/PegaSysEng/pantheon/pull/1476)
- Added warning banner when using latest version [\#1454](https://github.com/PegaSysEng/pantheon/pull/1454)
- Add RTD config file to fix Python version issue [\#1453](https://github.com/PegaSysEng/pantheon/pull/1453)
- \[PAN-2647\] Validate Private Transaction nonce before submitting to Transaction Pool [\#1449](https://github.com/PegaSysEng/pantheon/pull/1449) (thanks to [iikirilov](https://github.com/iikirilov))
- Add placeholders system to have global variables in markdown [\#1425](https://github.com/PegaSysEng/pantheon/pull/1425)

## 1.1.1

### Additions and Improvements

- [GraphQL](https://besu.hyperledger.org/en/latest/HowTo/Interact/APIs/GraphQL/) [\#1311](https://github.com/PegaSysEng/pantheon/pull/1311) (thanks to [zyfrank](https://github.com/zyfrank))
- Added [`--tx-pool-retention-hours`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#tx-pool-retention-hours) [\#1333](https://github.com/PegaSysEng/pantheon/pull/1333)
- Added Genesis file support for specifying the maximum stack size. [\#1431](https://github.com/PegaSysEng/pantheon/pull/1431)
- Included transaction details when subscribed to Pending transactions [\#1410](https://github.com/PegaSysEng/pantheon/pull/1410)
- Documentation updates include:
  - [Added configuration items specified in the genesis file](https://besu.hyperledger.org/en/latest/Reference/Config-Items/#configuration-items)
  - [Added pending transaction details subscription](https://besu.hyperledger.org/en/latest/HowTo/Interact/APIs/RPC-PubSub/#pending-transactionss)
  - [Added Troubleshooting content](https://besu.hyperledger.org/en/latest/HowTo/Troubleshoot/Troubleshooting/)
  - [Added Privacy Quickstart](https://besu.hyperledger.org/en/latest/Tutorials/Quickstarts/Privacy-Quickstart/)
  - [Added privacy roadmap](https://github.com/hyperledger/besu/blob/master/ROADMAP.md)


### Technical Improvements

- Create MaintainedPeers class [\#1484](https://github.com/PegaSysEng/pantheon/pull/1484)
- Fix for permissioned network with single bootnode [\#1479](https://github.com/PegaSysEng/pantheon/pull/1479)
- Have ThreadBesuNodeRunner support plugin tests [\#1477](https://github.com/PegaSysEng/pantheon/pull/1477)
- Less pointless plugins errors [\#1473](https://github.com/PegaSysEng/pantheon/pull/1473)
- Rename GraphQLRPC to just GraphQL [\#1472](https://github.com/PegaSysEng/pantheon/pull/1472)
- eth\_protocolVersion is a Quantity, not an Integer [\#1470](https://github.com/PegaSysEng/pantheon/pull/1470)
- Don't require 'to' in 'blocks' queries [\#1464](https://github.com/PegaSysEng/pantheon/pull/1464)
- Events Plugin - Add initial "NewBlock" event message [\#1463](https://github.com/PegaSysEng/pantheon/pull/1463)
- Make restriction field in Private Transaction an enum [\#1462](https://github.com/PegaSysEng/pantheon/pull/1462) (thanks to [iikirilov](https://github.com/iikirilov))
- Helpful graphql error when an account doesn't exist [\#1460](https://github.com/PegaSysEng/pantheon/pull/1460)
- Acceptance Test Cleanup [\#1458](https://github.com/PegaSysEng/pantheon/pull/1458)
- Large chain id support for private transactions [\#1452](https://github.com/PegaSysEng/pantheon/pull/1452)
- Optimise TransactionPool.addRemoteTransaction [\#1448](https://github.com/PegaSysEng/pantheon/pull/1448)
- Reduce synchronization in PendingTransactions [\#1447](https://github.com/PegaSysEng/pantheon/pull/1447)
- Add simple PeerPermissions interface [\#1446](https://github.com/PegaSysEng/pantheon/pull/1446)
- Make sure ThreadBesuNodeRunner is exercised by automation [\#1442](https://github.com/PegaSysEng/pantheon/pull/1442)
- Decode devp2p packets off the event thread [\#1439](https://github.com/PegaSysEng/pantheon/pull/1439)
- Allow config files to specify no bootnodes [\#1438](https://github.com/PegaSysEng/pantheon/pull/1438)
- Capture all logs and errors in the Besu log output [\#1437](https://github.com/PegaSysEng/pantheon/pull/1437)
- Ensure failed Txns are deleted when detected during mining [\#1436](https://github.com/PegaSysEng/pantheon/pull/1436)
- Plugin Framework [\#1435](https://github.com/PegaSysEng/pantheon/pull/1435)
- Equals cleanup [\#1434](https://github.com/PegaSysEng/pantheon/pull/1434)
- Transaction smart contract permissioning controller [\#1433](https://github.com/PegaSysEng/pantheon/pull/1433)
- Renamed AccountPermissioningProver to TransactionPermissio… [\#1432](https://github.com/PegaSysEng/pantheon/pull/1432)
- Refactorings and additions to add Account based Smart Contract permissioning [\#1430](https://github.com/PegaSysEng/pantheon/pull/1430)
- Fix p2p PeerInfo handling [\#1428](https://github.com/PegaSysEng/pantheon/pull/1428)
- IbftProcessor logs when a throwable terminates mining [\#1427](https://github.com/PegaSysEng/pantheon/pull/1427)
- Renamed AccountWhitelistController [\#1424](https://github.com/PegaSysEng/pantheon/pull/1424)
- Unwrap DelegatingBytes32 and prevent Hash from wrapping other Hash instances [\#1423](https://github.com/PegaSysEng/pantheon/pull/1423)
- If nonce is invalid, do not delete during mining [\#1422](https://github.com/PegaSysEng/pantheon/pull/1422)
- Deleting unused windows jenkinsfile [\#1421](https://github.com/PegaSysEng/pantheon/pull/1421)
- Get all our smoke tests for all platforms in 1 jenkins job [\#1420](https://github.com/PegaSysEng/pantheon/pull/1420)
- Add pending object to GraphQL queries [\#1419](https://github.com/PegaSysEng/pantheon/pull/1419)
- Start listening for p2p connections after start\(\) is invoked [\#1418](https://github.com/PegaSysEng/pantheon/pull/1418)
- Improved JSON-RPC responses when EnodeURI parameter has invalid EnodeId [\#1417](https://github.com/PegaSysEng/pantheon/pull/1417)
- Use port 0 when starting a websocket server in tests [\#1416](https://github.com/PegaSysEng/pantheon/pull/1416)
- Windows jdk smoke tests [\#1413](https://github.com/PegaSysEng/pantheon/pull/1413)
- Change AT discard RPC tests to be more reliable by checking discard using proposals [\#1411](https://github.com/PegaSysEng/pantheon/pull/1411)
- Simple account permissioning [\#1409](https://github.com/PegaSysEng/pantheon/pull/1409)
- Fix clique miner to respect changes to vanity data made via JSON-RPC [\#1408](https://github.com/PegaSysEng/pantheon/pull/1408)
- Avoid recomputing the logs bloom filter when reading receipts [\#1407](https://github.com/PegaSysEng/pantheon/pull/1407)
- Remove NodePermissioningLocalConfig external references [\#1406](https://github.com/PegaSysEng/pantheon/pull/1406)
- Add constantinople fix block for Rinkeby [\#1404](https://github.com/PegaSysEng/pantheon/pull/1404)
- Update EnodeURL to support enodes with listening disabled [\#1403](https://github.com/PegaSysEng/pantheon/pull/1403)
- Integration Integration test\(s\) on p2p of 'net\_services'  [\#1402](https://github.com/PegaSysEng/pantheon/pull/1402)
- Reference tests fail on Windows [\#1401](https://github.com/PegaSysEng/pantheon/pull/1401)
- Fix non-deterministic test caused by variable size of generated transactions [\#1399](https://github.com/PegaSysEng/pantheon/pull/1399)
- Start BlockPropagationManager immediately - don't wait for full sync [\#1398](https://github.com/PegaSysEng/pantheon/pull/1398)
- Added error message for RPC method disabled [\#1396](https://github.com/PegaSysEng/pantheon/pull/1396)
- Fix intermittency in FullSyncChainDownloaderTest [\#1394](https://github.com/PegaSysEng/pantheon/pull/1394)
- Add explanatory comment about default port [\#1392](https://github.com/PegaSysEng/pantheon/pull/1392)
- Handle case where peers advertise a listening port of 0 [\#1391](https://github.com/PegaSysEng/pantheon/pull/1391)
- Cache extra data [\#1389](https://github.com/PegaSysEng/pantheon/pull/1389)
- Update Log message in IBFT Controller [\#1387](https://github.com/PegaSysEng/pantheon/pull/1387)
- Remove unnecessary field [\#1384](https://github.com/PegaSysEng/pantheon/pull/1384)
- Add getPeer method to PeerConnection [\#1383](https://github.com/PegaSysEng/pantheon/pull/1383)
- Removing smart quotes [\#1381](https://github.com/PegaSysEng/pantheon/pull/1381) (thanks to [jmcnevin](https://github.com/jmcnevin))
- Use streams and avoid iterating child nodes multiple times [\#1380](https://github.com/PegaSysEng/pantheon/pull/1380)
- Use execute instead of submit so unhandled exceptions get logged [\#1379](https://github.com/PegaSysEng/pantheon/pull/1379)
- Prefer EnodeURL over Endpoint [\#1378](https://github.com/PegaSysEng/pantheon/pull/1378)
- Add flat file based task collection [\#1377](https://github.com/PegaSysEng/pantheon/pull/1377)
- Consolidate local enode representation [\#1376](https://github.com/PegaSysEng/pantheon/pull/1376)
- Rename rocksdDbConfiguration to rocksDbConfiguration [\#1375](https://github.com/PegaSysEng/pantheon/pull/1375)
- Remove EthTaskChainDownloader and supporting code [\#1373](https://github.com/PegaSysEng/pantheon/pull/1373)
- Handle the pipeline being aborted while finalizing an async operation [\#1372](https://github.com/PegaSysEng/pantheon/pull/1372)
- Rename methods that create and return streams away from getX\(\) [\#1368](https://github.com/PegaSysEng/pantheon/pull/1368)
- eea\_getTransactionCount fails if account has not interacted with private state [\#1367](https://github.com/PegaSysEng/pantheon/pull/1367) (thanks to [iikirilov](https://github.com/iikirilov))
- Increase RocksDB settings [\#1364](https://github.com/PegaSysEng/pantheon/pull/1364) ([ajsutton](https://github.com/ajsutton))
- Don't abort in-progress master builds when a new commit is added. [\#1358](https://github.com/PegaSysEng/pantheon/pull/1358)
- Request open ended headers from sync target [\#1355](https://github.com/PegaSysEng/pantheon/pull/1355)
- Enable the pipeline chain downloader by default [\#1344](https://github.com/PegaSysEng/pantheon/pull/1344)
- Create P2PNetwork Builder [\#1343](https://github.com/PegaSysEng/pantheon/pull/1343)
- Include static nodes in permissioning logic [\#1339](https://github.com/PegaSysEng/pantheon/pull/1339)
- JsonRpcError decoding to include message [\#1336](https://github.com/PegaSysEng/pantheon/pull/1336)
- Cache current chain head info [\#1335](https://github.com/PegaSysEng/pantheon/pull/1335)
- Queue pending requests when all peers are busy [\#1331](https://github.com/PegaSysEng/pantheon/pull/1331)
- Fix failed tests on Windows [\#1332](https://github.com/PegaSysEng/pantheon/pull/1332)
- Provide error message when invalid key specified in key file [\#1328](https://github.com/PegaSysEng/pantheon/pull/1328)
- Allow whitespace in file paths loaded from resources directory [\#1329](https://github.com/PegaSysEng/pantheon/pull/1329)
- Allow whitespace in path [\#1327](https://github.com/PegaSysEng/pantheon/pull/1327)
- Require block numbers for debug\_traceBlockByNumber to be in hex [\#1326](https://github.com/PegaSysEng/pantheon/pull/1326)
- Improve logging of chain download errors in the pipeline chain downloader [\#1325](https://github.com/PegaSysEng/pantheon/pull/1325)
- Ensure eth scheduler is stopped in tests [\#1324](https://github.com/PegaSysEng/pantheon/pull/1324)
- Normalize account permissioning addresses in whitelist [\#1321](https://github.com/PegaSysEng/pantheon/pull/1321)
- Allow private contract invocations in multiple privacy groups [\#1318](https://github.com/PegaSysEng/pantheon/pull/1318) (thanks to [iikirilov](https://github.com/iikirilov))
- Fix account permissioning check case matching [\#1315](https://github.com/PegaSysEng/pantheon/pull/1315)
- Use header validation mode for ommers [\#1313](https://github.com/PegaSysEng/pantheon/pull/1313)
- Configure RocksDb max background compaction and thread count [\#1312](https://github.com/PegaSysEng/pantheon/pull/1312)
- Missing p2p info when queried live [\#1310](https://github.com/PegaSysEng/pantheon/pull/1310)
- Tx limit size send peers follow up [\#1308](https://github.com/PegaSysEng/pantheon/pull/1308)
- Remove remnants of the old dev mode [\#1307](https://github.com/PegaSysEng/pantheon/pull/1307)
- Remove duplicate init code from BesuController instances [\#1305](https://github.com/PegaSysEng/pantheon/pull/1305)
- Stop synchronizer prior to stopping the network [\#1302](https://github.com/PegaSysEng/pantheon/pull/1302)
- Evict old transactions [\#1299](https://github.com/PegaSysEng/pantheon/pull/1299)
- Send local transactions to new peers [\#1253](https://github.com/PegaSysEng/pantheon/pull/1253)

## 1.1

### Additions and Improvements

- [Privacy](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Privacy-Overview/)
- [Onchain Permissioning](https://besu.hyperledger.org/en/latest/Concepts/Permissioning/Permissioning-Overview/#onchain)
- [Fastsync](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#fast-sync-min-peers)
- Documentation updates include:
    - Added JSON-RPC methods:
      - [`txpool_pantheonStatistics`](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#txpool_besustatistics)
      - [`net_services`](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#net_services)
    - [Updated to indicate Docker image doesn't run on Windows](https://besu.hyperledger.org/en/latest/HowTo/Get-Started/Run-Docker-Image/)
    - [Added how to configure a free gas network](https://besu.hyperledger.org/en/latest/HowTo/Configure/FreeGas/)

### Technical Improvements

- priv_getTransactionCount fails if account has not interacted with private state [\#1369](https://github.com/PegaSysEng/pantheon/pull/1369)
- Updating Orion to 0.9.0 [\#1360](https://github.com/PegaSysEng/pantheon/pull/1360)
- Allow use of large chain IDs [\#1357](https://github.com/PegaSysEng/pantheon/pull/1357)
- Allow private contract invocations in multiple privacy groups [\#1340](https://github.com/PegaSysEng/pantheon/pull/1340)
- Missing p2p info when queried live [\#1338](https://github.com/PegaSysEng/pantheon/pull/1338)
- Fix expose transaction statistics [\#1337](https://github.com/PegaSysEng/pantheon/pull/1337)
- Normalize account permissioning addresses in whitelist [\#1321](https://github.com/PegaSysEng/pantheon/pull/1321)
- Update Enclave executePost method [\#1319](https://github.com/PegaSysEng/pantheon/pull/1319)
- Fix account permissioning check case matching [\#1315](https://github.com/PegaSysEng/pantheon/pull/1315)
- Removing 'all' from the help wording for host-whitelist [\#1304](https://github.com/PegaSysEng/pantheon/pull/1304)

## 1.1 RC

### Technical Improvements

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
    - Added [Permissioning Overview](https://besu.hyperledger.org/en/latest/Concepts/Permissioning/Permissioning-Overview/)
    - Added content on [Network vs Node Configuration](https://besu.hyperledger.org/en/latest/HowTo/Configure/Using-Configuration-File/)
    - Updated [RAM requirements](https://besu.hyperledger.org/en/latest/HowTo/Get-Started/System-Requirements/#ram)
    - Added [Privacy Overview](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Privacy-Overview/) and [Processing Private Transactions](https://besu.hyperledger.org/en/latest/Concepts/Privacy/Private-Transaction-Processing/)
    - Renaming of Ethstats Lite Explorer to [Ethereum Lite Explorer](https://besu.hyperledger.org/en/latest/HowTo/Deploy/Lite-Block-Explorer/#lite-block-explorer-documentation) (thanks to [tzapu](https://github.com/tzapu))
    - Added content on using [Truffle with Besu](https://besu.hyperledger.org/en/latest/HowTo/Develop-Dapps/Truffle/)
    - Added [`droppedPendingTransactions` RPC Pub/Sub subscription](https://besu.hyperledger.org/en/latest/HowTo/Interact/APIs/RPC-PubSub/#dropped-transactions)
    - Added [`eea_*` JSON-RPC API methods](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#eea-methods)
    - Added [architecture diagram](https://besu.hyperledger.org/en/latest/Concepts/ArchitectureOverview/)
    - Updated [permissioning CLI options](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#permissions-accounts-config-file-enabled) and [permissioned network tutorial](https://besu.hyperledger.org/en/stable/)

### Technical Improvements

- Choose sync target based on td rather than height [\#1256](https://github.com/PegaSysEng/pantheon/pull/1256)
- CLI ewp options [\#1246](https://github.com/PegaSysEng/pantheon/pull/1246)
- Update BesuCommand.java [\#1245](https://github.com/PegaSysEng/pantheon/pull/1245)
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
   - Updated endpoints in [Private Network Quickstart](https://besu.hyperledger.org/en/latest/Tutorials/Quickstarts/Private-Network-Quickstart/) (thanks to [laubai](https://github.com/laubai))
   - Updated [documentation contribution guidelines](https://besu.hyperledger.org/en/stable/)
   - Added [`admin_removePeer`](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#admin_removepeer)
   - Updated [tutorials](https://besu.hyperledger.org/en/latest/Tutorials/Private-Network/Create-Private-Clique-Network/) for printing of enode on startup
   - Added [`txpool_pantheonTransactions`](https://besu.hyperledger.org/en/stable/Reference/API-Methods/#txpool_besutransactions)
   - Added [Transaction Pool content](https://besu.hyperledger.org/en/latest/Concepts/Transactions/Transaction-Pool/)
   - Added [`tx-pool-max-size` CLI option](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#tx-pool-max-size)
   - Updated [developer build instructions to use installDist](https://besu.hyperledger.org/en/stable/)
   - Added [Azure quickstart tutorial](https://besu.hyperledger.org/en/latest/Tutorials/Quickstarts/Azure-Private-Network-Quickstart/)
   - Enabled copy button in code blocks
   - Added [IBFT 1.0](https://besu.hyperledger.org/en/latest/HowTo/Configure/Consensus-Protocols/QuorumIBFT/)
   - Added section on using [Geth attach with Besu](https://besu.hyperledger.org/en/latest/HowTo/Interact/APIs/Using-JSON-RPC-API/#geth-console)
   - Enabled the edit link doc site to ease external doc contributions
   - Added [EthStats docs](https://besu.hyperledger.org/HowTo/Deploy/Lite-Network-Monitor/) (thanks to [baxy](https://github.com/baxy))
   - Updated [Postman collection](https://besu.hyperledger.org/en/latest/HowTo/Interact/APIs/Authentication/#postman)
   - Added [`metrics-category` CLI option](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#metrics-category)
   - Added information on [block time and timeout settings](https://besu.hyperledger.org/en/latest/HowTo/Configure/Consensus-Protocols/IBFT/#block-time) for IBFT 2.0
   - Added [`admin_nodeInfo`](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#admin_nodeinfo)
   - Added [permissions images](https://besu.hyperledger.org/en/latest/Concepts/Permissioning/Permissioning-Overview/)
   - Added permissioning blog to [Resources](https://besu.hyperledger.org/en/latest/Reference/Resources/)
   - Updated [Create Permissioned Network](https://besu.hyperledger.org/en/latest/Tutorials/Permissioning/Create-Permissioned-Network/) tutorial to use `export-address`
   - Updated [Clique](https://besu.hyperledger.org/en/latest/HowTo/Configure/Consensus-Protocols/Clique/) and [IBFT 2.0](https://besu.hyperledger.org/en/latest/HowTo/Configure/Consensus-Protocols/IBFT/) docs to include complete genesis file
   - Updated [Clique tutorial](https://besu.hyperledger.org/en/latest/Tutorials/Private-Network/Create-Private-Clique-Network/) to use `export-address` subcommand
   - Added IBFT 2.0 [future message configuration options](https://besu.hyperledger.org/en/latest/HowTo/Configure/Consensus-Protocols/IBFT/#optional-configuration-options)

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
- Adding default pending transactions value in BesuControllerBuilder [\#1114](https://github.com/PegaSysEng/pantheon/pull/1114)
- Fix intermittency in WorldStateDownloaderTest [\#1113](https://github.com/PegaSysEng/pantheon/pull/1113)
- Reduce number of seen blocks and transactions Besu tracks [\#1112](https://github.com/PegaSysEng/pantheon/pull/1112)
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
- RPC tests can use 127.0.0.1 loopback rather than localhost [\#974](https://github.com/PegaSysEng/pantheon/pull/974) thanks to [glethuillier](https://github.com/glethuillier) for raising)
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
- Documentation update for the [`public-key export-address`](https://besu.hyperledger.org/en/stable/) subcommand.
- Updated [IBFT 2.0 overview](https://besu.hyperledger.org/en/stable/) to include use of `rlp encode` command and information on setting IBFT 2.0 properties to achieve your desired block time.

## 1.0

### Additions and Improvements
- [IBFT 2.0](https://besu.hyperledger.org/en/latest/Tutorials/Private-Network/Create-IBFT-Network/)
- [Permissioning](https://besu.hyperledger.org/en/latest/Concepts/Permissioning/Permissioning-Overview/)
- [JSON-RPC Authentication](https://besu.hyperledger.org/en/latest/HowTo/Interact/APIs/Authentication/)
- Added `rlp encode` subcommand [\#965](https://github.com/PegaSysEng/pantheon/pull/965)
- Method to reload permissions file [\#834](https://github.com/PegaSysEng/pantheon/pull/834)
- Added rebind mitigation for Websockets. [\#905](https://github.com/PegaSysEng/pantheon/pull/905)
- Support genesis contract code [\#749](https://github.com/PegaSysEng/pantheon/pull/749) (thanks to [kziemianek](https://github.com/kziemianek)).
- Documentation updates include:
  - Added details on [port configuration](https://besu.hyperledger.org/en/latest/HowTo/Find-and-Connect/Configuring-Ports/)
  - Added [Resources page](https://besu.hyperledger.org/en/latest/Reference/Resources/) linking to Besu blog posts and webinars
  - Added [JSON-RPC Authentication](https://besu.hyperledger.org/en/latest/HowTo/Interact/APIs/Authentication/)
  - Added [tutorial to create permissioned network](https://besu.hyperledger.org/en/latest/Tutorials/Permissioning/Create-Permissioned-Network/)
  - Added [Permissioning](https://besu.hyperledger.org/en/latest/Concepts/Permissioning/Permissioning-Overview/) content
  - Added [Permissioning API methods](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#permissioning-methods)
  - Added [tutorial to create Clique private network](https://besu.hyperledger.org/en/latest/Tutorials/Private-Network/Create-Private-Clique-Network/)
  - Added [tutorial to create IBFT 2.0 private network](https://besu.hyperledger.org/en/latest/Tutorials/Private-Network/Create-IBFT-Network/)

### Technical Improvements
- RoundChangeCertificateValidator requires unique authors [\#997](https://github.com/PegaSysEng/pantheon/pull/997)
- RPC tests can use 127.0.0.1 loopback rather than localhost [\#979](https://github.com/PegaSysEng/pantheon/pull/979)
- Integration Test implementation dependency for non-IntelliJ IDE [\#978](https://github.com/PegaSysEng/pantheon/pull/978)
- Only import block if it isn't already on the block chain [\#977](https://github.com/PegaSysEng/pantheon/pull/977)
- Disable picocli ansi when testing [\#975](https://github.com/PegaSysEng/pantheon/pull/975)
- Create peer discovery packets on a worker thread [\#961](https://github.com/PegaSysEng/pantheon/pull/961)
- Removed Orion snapshot dependency [\#933](https://github.com/PegaSysEng/pantheon/pull/933)
- Use network ID instead of chain ID in MainnetBesuController. [\#929](https://github.com/PegaSysEng/pantheon/pull/929)
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
- Use absolute datapath instead of relative. [\#894](https://github.com/PegaSysEng/pantheon/pull/894).
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
- Fix 'the Input Is Too Long' Error on Windows [\#843](https://github.com/PegaSysEng/pantheon/pull/843) (thanks to [glethuillier](https://github.com/glethuillier)).
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
- Homogenize error messages when required parameters are set [\#822](https://github.com/PegaSysEng/pantheon/pull/822) ([glethuillier](https://github.com/glethuillier)).
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

The [documentation](https://docs.pantheon.pegasys.tech/en/latest/) has been updated throughout to use the changed command line options and the [command line reference](https://besu.hyperledger.org/en/stable/) documents the changed options.

| Previous Option                     | New Option                                                                                                                                                                                                                                  | Change                            |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| `--config`                          | [`--config-file`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#config-file)                                                                                                                                  | Renamed                          |
| `--datadir`                         | [`--data-path`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#data-path)                                                                                                                                      | Renamed                          |
| `--dev-mode`                        | [`--network=dev`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#network)                                                                                                                                     | Replaced by `--network` option   |
| `--genesis`                         | [`--genesis-file`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#genesis-file)                                                                                                                                | Renamed                          |
| `--goerli`                          | [`--network=goerli`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#network)                                                                                                                                  | Replaced by `--network` option   |
| `--metrics-listen=<HOST:PORT>`      | [`--metrics-host=<HOST>`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#metrics-host) and [`--metrics-port=<PORT>`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#metrics-port) | Split into host and port options |
| `--miner-extraData`                 | [`--miner-extra-data`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#miner-extra-data)                                                                                                                       | Renamed                          |
| `--miner-minTransactionGasPriceWei` | [`--min-gas-price`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#min-gas-price)                                                                                                                              | Renamed                          |
| `--no-discovery`                    | [`--discovery-enabled`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#discovery-enabled)                                                                                                                      | Replaced                         |
| `--node-private-key`                | [`--node-private-key-file`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#node-private-key-file)                                                                                                              | Renamed                          |
| `--ottoman`                         | N/A                                                                                                                                                                                                                                         | Removed                          |
| `--p2p-listen=<HOST:PORT>`          | [`--p2p-host=<HOST>`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#p2p-hostt) and [`--p2p-port=<PORT>`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#p2p-port) | Split into host and port options |
| `--rinkeby`                         | [`--network=rinkeby`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#network)                                                                                                                                     | Replaced by `--network` option   |
| `--ropsten`                         | [`--network=ropsten`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#network)                                                                                                                                     | Replaced by `--network` option   |
| `--rpc-enabled`                     | [` --rpc-http-enabled`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#rpc-http-enabled)| Renamed|
| `--rpc-listen=<HOST:PORT>`          | [`--rpc-http-host=<HOST>`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#rpc-http-host) and [`--rpc-http-port=<PORT>`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#rpc-http-port) | Split into host and port options |
| `--rpc-api`                         | [`--rpc-http-api`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#rpc-http-api)| Renamed |
| `--rpc-cors-origins`                | [`--rpc-http-cors-origins`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#rpc-http-cors-origins) | Renamed |
| `--ws-enabled`                      | [`--rpc-ws-enabled`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#rpc-ws-enabled)  | Renamed |
| `--ws-api`                          | [`--rpc-ws-api`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#rpc-ws-api) | Renamed|
| `--ws-listen=<HOST:PORT>`           | [`--rpc-ws-host=<HOST>`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#rpc-ws-host) and [`--rpc-ws-port=<PORT>`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#rpc-ws-port) | Split into host and port options |
| `--ws-refresh-delay`                | [`--rpc-ws-refresh-delay`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Syntax/#rpc-ws-refresh-delay)|Renamed|

| Previous Subcommand                 | New Subcommand                                                                                                                                                                                                                  | Change                            |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| `pantheon import <block-file>`      | [`pantheon blocks import --from=<block-file>`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Subcommands/#blocks)                                                                                            | Renamed                          |
| `pantheon export-pub-key <key-file>`| [`pantheon public-key export --to=<key-file>`](https://besu.hyperledger.org/en/latest/Reference/CLI/CLI-Subcommands/#public-key)                                                                                                      | Renamed                          |


### Private Network Quickstart

The Private Network Quickstart has been moved from the `pantheon` repository to the `pantheon-quickstart`
repository. The [Private Network Quickstart tutorial](https://besu.hyperledger.org/en/latest/Tutorials/Quickstarts/Private-Network-Quickstart/)
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
  - Updated [Private Network Quickstart tutorial](https://besu.hyperledger.org/en/latest/Tutorials/Quickstarts/Private-Network-Quickstart/)
    to use quickstart in `pantheon-quickstart` repository and indicate that the quickstart is not supported on Windows.
  - Added IBFT 2.0 [content](https://besu.hyperledger.org/en/latest/HowTo/Configure/Consensus-Protocols/IBFT/) and [JSON RPC API methods](https://besu.hyperledger.org/en/latest/Reference/API-Methods/#ibft-20-methods).
  - Added [consensus protocols content](https://besu.hyperledger.org/en/latest/Concepts/Consensus-Protocols/Comparing-PoA/).
  - Added content on [events and logs](https://besu.hyperledger.org/en/latest/Concepts/Events-and-Logs/), and [using filters](https://besu.hyperledger.org/en/latest/HowTo/Interact/Filters/Accessing-Logs-Using-JSON-RPC/).
  - Added content on integrating with [Prometheus Push Gateway](https://besu.hyperledger.org/en/latest/HowTo/Deploy/Monitoring-Performance/#running-prometheus-with-besu-in-push-mode)

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
- Added `p2pEnabled` configuration to `ProcessBesuNodeRunner` [\#649](https://github.com/PegaSysEng/pantheon/pull/649)
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
specified [data directory](https://besu.hyperledger.org/en/stable/),
or referring to the custom [configuration file](https://besu.hyperledger.org/en/stable/)
or [genesis file](https://besu.hyperledger.org/en/stable/).

To recover the node key and data directory from the Docker container:
`docker cp <container>:/opt/pantheon/key <destination_file>`
`docker cp <container>:/opt/pantheon/database <destination_directory>`

Where `container` is the name or ID of the Docker container containing the Besu node.

The container can be running or stopped when you copy the key and data directory. If your node was
fully synchronized to MainNet, the data directory will be ~2TB.

When restarting your node with the v0.8.4 Docker image:

* Save the node key in the [`key` file](https://besu.hyperledger.org/en/latest/Concepts/Node-Keys/#node-private-key) in the data
    directory or specify the location using the [`--node-private-key` option](https://besu.hyperledger.org/en/stable/).
* Specify the `<destination_directory` as a [volume for the data directory](https://besu.hyperledger.org/en/stable/).

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
  * Added [configuration file content](https://besu.hyperledger.org/en/stable/)
  * Added [tutorial to create private network](https://besu.hyperledger.org/en/latest/Tutorials/Private-Network/Create-Private-Network/)
  * Added content on [enabling non-default APIs](https://besu.hyperledger.org/en/latest/Reference/API-Methods/)

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
- Make Besu behave as a submodule [\#419](https://github.com/PegaSysEng/pantheon/pull/419)
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

If your application publishes RPC ports, specify the hostnames when starting Besu. For example:

```bash
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
- Begin capturing metrics to better understand Besu's behaviour [\#326](https://github.com/PegaSysEng/pantheon/pull/326)
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
- Fix `pantheon.cli.BesuCommandTest` test on Windows [\#380](https://github.com/PegaSysEng/pantheon/pull/380)
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
- Delegate creation of additional JSON-RPC methods to the BesuController [\#289](https://github.com/PegaSysEng/pantheon/pull/289)
- Remove unnecessary `RlpInput` and `RlpOutput` classes [\#287](https://github.com/PegaSysEng/pantheon/pull/287)
- Remove `RlpUtils` [\#285](https://github.com/PegaSysEng/pantheon/pull/285)
- Enabling previously ignored acceptance tests [\#282](https://github.com/PegaSysEng/pantheon/pull/282)
- IPv6 peers [\#281](https://github.com/PegaSysEng/pantheon/pull/281)
- IPv6 Bootnode [\#280](https://github.com/PegaSysEng/pantheon/pull/280)
- Acceptance test for `getTransactionReceipt` JSON-RPC method [\#278](https://github.com/PegaSysEng/pantheon/pull/278)
- Inject `StorageProvider` into `BesuController` instances [\#259](https://github.com/PegaSysEng/pantheon/pull/259)

## 0.8.2

### Removed
 - Removed `import-blockchain` command because nothing exports to the required format yet (PR [\#223](https://github.com/PegaSysEng/pantheon/pull/223))

### Bug Fixes
 - `io.netty.util.internal.OutOfDirectMemoryError` errors by removing reference counting from network messages.
 - Log spam: endless loop in `nioEventLoopGroup` thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#261](https://github.com/PegaSysEng/pantheon/pull/261))
 - Rinkeby import can stall with too many fragments thanks to [@steffenkux](https://github.com/steffenkux) and [@5chdn](https://github.com/5chdn) for reporting) (PR [#255](https://github.com/PegaSysEng/pantheon/pull/255))
 - Clique incorrectly used the chain ID instead of the network ID in ETH status messages (PR [#209](https://github.com/PegaSysEng/pantheon/pull/209))
 - Gradle deprecation warnings (PR [#246](https://github.com/PegaSysEng/pantheon/pull/246) with thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Consensus issue on Ropsten:
    - Treat output length as a maximum length for CALL operations (PR [#236](https://github.com/PegaSysEng/pantheon/pull/236))
    - ECRec precompile should return empty instead of 32 zero bytes when the input is invalid (PR [#227](https://github.com/PegaSysEng/pantheon/pull/227))
 - File name too long error while building from source thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#221](https://github.com/PegaSysEng/pantheon/pull/221))
 - Loop syntax in `runBesuPrivateNetwork.sh` (PR [#237](https://github.com/PegaSysEng/pantheon/pull/237) thanks to [@matt9ucci](https://github.com/matt9ucci))
 - Fix `CompressionException: Snappy decompression failed` errors thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#274](https://github.com/PegaSysEng/pantheon/pull/274))

### Additions and Improvements
 - Added `--ropsten` command line argument to make syncing to Ropsten easier (PR [#197](https://github.com/PegaSysEng/pantheon/pull/197) with thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Enabled constantinople in `--dev-mode` (PR [#256](https://github.com/PegaSysEng/pantheon/pull/256))
 - Supported Constantinople with Clique thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#250](https://github.com/PegaSysEng/pantheon/pull/250), PR [#247](https://github.com/PegaSysEng/pantheon/pull/247))
 - Implemented `eth_chainId` JSON-RPC method (PR [#219](https://github.com/PegaSysEng/pantheon/pull/219))
 - Updated client version to be ethstats friendly (PR [#258](https://github.com/PegaSysEng/pantheon/pull/258))
 - Added `--node-private-key` option to allow nodekey file to be specified separately to data directory thanks to [@peterbroadhurst](https://github.com/peterbroadhurst) for requesting)  (PR [#234](https://github.com/PegaSysEng/pantheon/pull/234))
 - Added `--banned-nodeids` option to prevent connection to specific nodes (PR [#254](https://github.com/PegaSysEng/pantheon/pull/254))
 - Send client quitting disconnect message to peers on shutdown (PR [#253](https://github.com/PegaSysEng/pantheon/pull/253))
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
 - Added unit tests for `EthBlockNumber` (PR [#195](https://github.com/PegaSysEng/pantheon/pull/195) thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Code style improvements (PR [#196](https://github.com/PegaSysEng/pantheon/pull/196) thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Added unit tests for `Web3ClientVersion` (PR [#194](https://github.com/PegaSysEng/pantheon/pull/194) with thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Removed RLPUtils from `RawBlockIterator` (PR [#179](https://github.com/PegaSysEng/pantheon/pull/179))
 - Replace the JNI based snappy library with a pure-Java version (PR [#257](https://github.com/PegaSysEng/pantheon/pull/257))
