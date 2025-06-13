# Changelog
## Unreleased
### Breaking Changes
- Changes in Maven coordinates of Besu artifacts to avoid possible collisions with other libraries when packaging plugins. See [Appendix A](#appendix-a) for a mapping. [#8589](https://github.com/hyperledger/besu/pull/8589) [#8746](https://github.com/hyperledger/besu/pull/8746)
- `BesuContext` is removed, since deprecated in favor of `ServiceManager` since `24.12.0`
- Remove the deprecated `--Xbonsai-limit-trie-logs-enabled`, use `--bonsai-limit-trie-logs-enabled` instead. [#8704](https://github.com/hyperledger/besu/issues/8704)
- Remove the deprecated `--Xbonsai-trie-log-pruning-enabled`, use `--bonsai-limit-trie-logs-enabled` instead. [#8704](https://github.com/hyperledger/besu/issues/8704)
- Sunsetting features - for more context on the reasoning behind the deprecation of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
  - Stratum mining has been removed (part of PoW) [#8802](https://github.com/hyperledger/besu/pull/8802)
  - PoW RPCs removed: `eth_getWork`, `eth_submitWork`, `eth_getHashrate`, `eth_submitHashrate`, `eth_hashrate`

### Upcoming Breaking Changes
- `--Xbonsai-trie-logs-pruning-window-size` is deprecated, use `--bonsai-trie-logs-pruning-window-size` instead.
- `--Xbonsai-parallel-tx-processing-enabled` is deprecated, use `--bonsai-parallel-tx-processing-enabled` instead.
- `--Xsnapsync-bft-enabled` is deprecated and will be removed in a future release. SNAP sync is supported for BFT networks.
- Sunsetting features - for more context on the reasoning behind the deprecation of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
  - Proof of Work consensus (PoW)
  - Fast Sync
- Support for block creation on networks running a pre-Byzantium fork is deprecated for removal in a future release, after that to update Besu on nodes that build blocks, your network needs to be upgraded at least to the Byzantium fork. The main reason is to simplify world state management during block creation, since before Byzantium for each selected transaction, the receipt must contain the root hash of the modified world state, and this does not play well with the new plugin features and future work on parallelism.

### Additions and Improvements
- Introduce the `TransactionValidatorService` to allow plugins to add custom validation rules [#8793](https://github.com/hyperledger/besu/pull/8793)
- Enable parallel tx processing by default if Bonsai is used [#8668](https://github.com/hyperledger/besu/pull/8668)

#### Fusaka Devnet
- EIP-7825 - Transaction gas limit cap [#8700](https://github.com/hyperledger/besu/pull/8700)
- EIP-7823 - Modexp upper bounds [#8632](https://github.com/hyperledger/besu/pull/8632)
- Implement rewardPercentile cap in eth_feeHistory [#8748](https://github.com/hyperledger/besu/pull/8748)

### Bug fixes

#### Appendix A
| Old Module Name | New Module Name |
|---|---|
| org.hyperledger.besu.internal:dsl | org.hyperledger.besu.internal:besu-acceptance-tests-dsl |
| org.hyperledger.besu.internal:besu | org.hyperledger.besu.internal:besu-app |
| org.hyperledger.besu.internal:config | org.hyperledger.besu.internal:besu-config |
| org.hyperledger.besu.internal:clique | org.hyperledger.besu.internal:besu-consensus-clique |
| org.hyperledger.besu.internal:common | org.hyperledger.besu.internal:besu-consensus-common |
| org.hyperledger.besu.internal:ibft | org.hyperledger.besu.internal:besu-consensus-ibft |
| org.hyperledger.besu.internal:ibftlegacy | org.hyperledger.besu.internal:besu-consensus-ibftlegacy |
| org.hyperledger.besu.internal:merge | org.hyperledger.besu.internal:besu-consensus-merge |
| org.hyperledger.besu.internal:qbft | org.hyperledger.besu.internal:besu-consensus-qbft |
| org.hyperledger.besu.internal:qbft-core | org.hyperledger.besu.internal:besu-consensus-qbft-core |
| org.hyperledger.besu.internal:algorithms | org.hyperledger.besu.internal:besu-crypto-algorithms |
| org.hyperledger.besu.internal:services | org.hyperledger.besu.internal:besu-crypto-services |
| org.hyperledger.besu:besu-datatypes | org.hyperledger.besu:besu-datatypes |
| org.hyperledger.besu.internal:api | org.hyperledger.besu.internal:besu-ethereum-api |
| org.hyperledger.besu.internal:blockcreation | org.hyperledger.besu.internal:besu-ethereum-blockcreation |
| org.hyperledger.besu.internal:core | org.hyperledger.besu.internal:besu-ethereum-core |
| org.hyperledger.besu.internal:eth | org.hyperledger.besu.internal:besu-ethereum-eth |
| org.hyperledger.besu.internal:p2p | org.hyperledger.besu.internal:besu-ethereum-p2p |
| org.hyperledger.besu.internal:permissioning | org.hyperledger.besu.internal:besu-ethereum-permissioning |
| org.hyperledger.besu.internal:referencetests | org.hyperledger.besu.internal:besu-ethereum-referencetests |
| org.hyperledger.besu.internal:rlp | org.hyperledger.besu.internal:besu-ethereum-rlp |
| org.hyperledger.besu.internal:trie | org.hyperledger.besu.internal:besu-ethereum-trie |
| org.hyperledger.besu:evm | org.hyperledger.besu:besu-evm |
| org.hyperledger.besu.internal:metrics-core | org.hyperledger.besu.internal:besu-metrics-core |
| org.hyperledger.besu:plugin-api | org.hyperledger.besu:besu-plugin-api |
| org.hyperledger.besu.internal:testutil | org.hyperledger.besu.internal:besu-testutil |
| org.hyperledger.besu.internal:util | org.hyperledger.besu.internal:besu-util |
| org.hyperledger.besu.internal:nat | org.hyperledger.besu.internal:besu-nat |
| org.hyperledger.besu.internal:tasks | org.hyperledger.besu.internal:besu-services-tasks |
| org.hyperledger.besu.internal:pipeline | org.hyperledger.besu.internal:besu-services-pipeline |
| org.hyperledger.besu.internal:kvstore | org.hyperledger.besu.internal:besu-services-kvstore |

## 25.6.0
### Breaking Changes
- Sunset features - for more context on the reasoning behind the removal of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
  - Remove onchain permissioning [#8597](https://github.com/hyperledger/besu/pull/8597)
  - Remove Tessera Privacy feature [#8369](https://github.com/hyperledger/besu/pull/8369)
- Remove `MetricSystem::createLabelledGauge` deprecated since `24.12.0`, replace it with `MetricSystem::createLabelledSuppliedGauge` [#8299](https://github.com/hyperledger/besu/pull/8299)
- Remove the deprecated `--tx-pool-disable-locals` option, use `--tx-pool-no-local-priority` instead. [#8614](https://github.com/hyperledger/besu/pull/8614)
- Remove the deprecated `--Xsnapsync-synchronizer-flat-db-healing-enabled`, use `--Xbonsai-full-flat-db-enabled` instead. [#8415](https://github.com/hyperledger/besu/issues/8415)
- Change in behavior, the non-standard `strict` parameter of the `eth_estimateGas` method changed its default from `false` to `true`, for more accurate estimations. It is still possible to force the previous behavior, explicitly passing the `strict` parameter in the request, set to `false` [#8629](https://github.com/hyperledger/besu/pull/8629)


### Upcoming Breaking Changes
- `--Xbonsai-limit-trie-logs-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-log-pruning-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-logs-pruning-window-size` is deprecated, use `--bonsai-trie-logs-pruning-window-size` instead.
- `--Xsnapsync-bft-enabled` is deprecated and will be removed in a future release. SNAP sync is supported for BFT networks.
- Sunsetting features - for more context on the reasoning behind the deprecation of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
  - Proof of Work consensus (PoW)
  - Fast Sync
- Support for block creation on networks running a pre-Byzantium fork is deprecated for removal in a future release, after that to update Besu on nodes that build blocks, your network needs to be upgraded at least to the Byzantium fork. The main reason is to simplify world state management during block creation, since before Byzantium for each selected transaction, the receipt must contain the root hash of the modified world state, and this does not play well with the new plugin features and future work on parallelism.

### Additions and Improvements
- Add eth/69 protocol for optional use by using the `--Xeth-capability-max=69` flag (currently defaults to 68) [#8519](https://github.com/hyperledger/besu/pull/8519)
- BlobDB GC early access config options:
  - `--Xplugin-rocksdb-blockchain-blob-garbage-collection-enabled` Adds ability to enable BlobDB GC for BLOCKCHAIN column family [#8599](https://github.com/hyperledger/besu/pull/8599)
  - `--Xplugin-rocksdb-blob-garbage-collection-age-cutoff`, `--Xplugin-rocksdb-blob-garbage-collection-force-threshold` BlobDB GC config options [#8599](https://github.com/hyperledger/besu/pull/8599)
- Increase default target-gas-limit to 60M for Ephemery [#8622](https://github.com/hyperledger/besu/pull/8622)
- Increase default target-gas-limit to 60M for Hoodi [#8705](https://github.com/hyperledger/besu/pull/8705)
- Gas estimation `eth_estimateGas`:
  - Estimate gas on pending block by default [#8627](https://github.com/hyperledger/besu/pull/8627)
  - Estimate gas is now strict by default [#8629](https://github.com/hyperledger/besu/pull/8629)
- Update ref test to 4.5.0 [#8643](https://github.com/hyperledger/besu/pull/8643)
- EVM skip unnecssary state access for CALL/LOG [#8639](https://github.com/hyperledger/besu/pull/8639)
- EIP-5920 - PAY opcode [#8498](https://github.com/hyperledger/besu/pull/8498)
- EIP-7892 - Blob Parameter Only (BPO) forks [#8671](https://github.com/hyperledger/besu/pull/8671)
- EIP-7883 - MODEXP gas cost increase [#8707](https://github.com/hyperledger/besu/pull/8707)

#### Dependencies
- Update discovery library to 25.4.0 [#8635](https://github.com/hyperledger/besu/pull/8635)
- Upgrade Gradle to 8.14 and related plugins [#8638](https://github.com/hyperledger/besu/pull/8638)

### Bug fixes
- Fix `besu -X` unstable options help [#8662](https://github.com/hyperledger/besu/pull/8662)
- Prevent parsing of invalid ENR records from crashing DNSDaemon [#8368](https://github.com/hyperledger/besu/issues/8368)
- ENR records with Base64 padding at the end are not parsed correctly [#8697](https://github.com/hyperledger/besu/issues/8697)

## 25.5.0
### Breaking Changes
- Changes to gas estimation algorithm for `eth_estimateGas` and `eth_createAccessList` [#8478](https://github.com/hyperledger/besu/pull/8478) - if you require the previous behavior, specify `--estimate-gas-tolerance-ratio=0.0`
- Transaction indexing is now disabled by default during the initial sync for snap sync and checkpoint sync. This will break RPCs that use transaction hash for historical queries. [#8611](https://github.com/hyperledger/besu/pull/8611). If you need to enable transaction for the initial sync, use `--snapsync-synchronizer-transaction-indexing-enabled`

### Upcoming Breaking Changes
- `MetricSystem::createLabelledGauge` is deprecated and will be removed in a future release, replace it with `MetricSystem::createLabelledSuppliedGauge`
- `--Xsnapsync-synchronizer-flat-db-healing-enabled` is deprecated, use `--Xbonsai-full-flat-db-enabled` instead.
- `--Xbonsai-limit-trie-logs-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-log-pruning-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-logs-pruning-window-size` is deprecated, use `--bonsai-trie-logs-pruning-window-size` instead.
- `--Xsnapsync-bft-enabled` is deprecated and will be removed in a future release. SNAP sync is supported for BFT networks.
- Sunsetting features - for more context on the reasoning behind the deprecation of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
    - Tessera privacy
    - Proof of Work consensus
    - Fast Sync
- Transaction indexing will be disabled by default in a future release for snap sync and checkpoint sync modes. This will break RPCs that use transaction hash for historical queries.
- Support for block creation on networks running a pre-Byzantium fork is deprecated for removal in a future release, after that in order to update Besu on nodes that build blocks, your network needs to be upgraded at least to the Byzantium fork. The main reason is to simplify world state management during block creation, since before Byzantium for each selected transaction, the receipt must contain the root hash of the modified world state, and this does not play well with the new plugin features and future work on parallelism.

### Additions and Improvements
- Sepolia checkpoint block has been updated to the merge block [#8584](https://github.com/hyperledger/besu/pull/8584)
- Add experimental `--profile=PERFORMANCE` for high spec nodes: increases rocksdb cache and enables parallel transaction processing [#8560](https://github.com/hyperledger/besu/pull/8560)
- Add `--profile=PERFORMANCE_RPC` for high spec RPC nodes: increases rocksdb cache and caches last 2048 blocks [#8560](https://github.com/hyperledger/besu/pull/8560)
- Refine gas estimation algorithm for `eth_estimateGas` and `eth_createAccessList` [#8478](https://github.com/hyperledger/besu/pull/8478) including a new option to specify `--estimate-gas-tolerance-ratio`
- Increase default target-gas-limit to 60M for Holesky and Sepolia [#8594](https://github.com/hyperledger/besu/pull/8594)
- Add support for [Pyroscope](https://grafana.com/docs/pyroscope/latest/) to Docker image [#8510](https://github.com/hyperledger/besu/pull/8510)

#### Dependencies
- update jc-kzg-4844 dependency from 2.0.0 to 2.1.1

### Bug fixes
- Fix block import tracing, refactor BlockProcessor interface [#8549](https://github.com/hyperledger/besu/pull/8549)
- Shorten and standardise log labels in AbstractEngineNewPayload and AbstractEngineForkchoiceUpdated [#8568](https://github.com/hyperledger/besu/pull/8568)

## 25.4.1

### Breaking Changes
- `--compatibility-eth64-forkid-enabled` has been removed. [#8541](https://github.com/hyperledger/besu/pull/8541)

### Upcoming Breaking Changes
- `MetricSystem::createLabelledGauge` is deprecated and will be removed in a future release, replace it with `MetricSystem::createLabelledSuppliedGauge`
- `--Xsnapsync-synchronizer-flat-db-healing-enabled` is deprecated, use `--Xbonsai-full-flat-db-enabled` instead.
- `--Xbonsai-limit-trie-logs-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-log-pruning-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-logs-pruning-window-size` is deprecated, use `--bonsai-trie-logs-pruning-window-size` instead.
- `--Xsnapsync-bft-enabled` is deprecated and will be removed in a future release. SNAP sync is supported for BFT networks.
- `--tx-pool-disable-locals` has been deprecated, use `--tx-pool-no-local-priority`, instead.
- Sunsetting features - for more context on the reasoning behind the deprecation of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
    - Tessera privacy
    - Smart-contract-based (onchain) permissioning
    - Proof of Work consensus
    - Fast Sync
- Transaction indexing will be disabled by default in a future release for snap sync and checkpoint sync modes. This will break RPCs that use transaction hash for historical queries.
- Support for block creation on networks running a pre-Byzantium fork is deprecated for removal in a future release, after that in order to update Besu on nodes that build blocks, your network needs to be upgraded at least to the Byzantium fork. The main reason is to simplify world state management during block creation, since before Byzantium for each selected transaction, the receipt must contain the root hash of the modified world state, and this does not play well with the new plugin features and future work on parallelism.

### Additions and Improvements
- Add support for `eth_simulateV1`. [#8406](https://github.com/hyperledger/besu/pull/8406)
- New metric `besu_peers_peer_count_by_client` to report the count of peers by client name [#8515](https://github.com/hyperledger/besu/pull/8515)- Add Prague fork time for mainnet. [#8521](https://github.com/hyperledger/besu/pull/8521)
- Add Prague fork time for mainnet. [#8521](https://github.com/hyperledger/besu/pull/8521)
- Default ExtraData to "besu+version" rather than empty. [#8536](https://github.com/hyperledger/besu/pull/8536)
- Add block import tracer plugin provider, used during block import [#8524](https://github.com/hyperledger/besu/pull/8534)
- Tune and make configurable the PeerTransactionTracker [#8537](https://github.com/hyperledger/besu/pull/8537)

### Bug fixes
- Fix for storage proof key - if the key is zero use `0x0` not `0x` [#8499](https://github.com/hyperledger/besu/pull/8499)
- Fix ephemery genesis since genesisHash changed [#8539](https://github.com/hyperledger/besu/pull/8539)

## 25.4.0

### Upcoming Breaking Changes
- `MetricSystem::createLabelledGauge` is deprecated and will be removed in a future release, replace it with `MetricSystem::createLabelledSuppliedGauge`
- `--Xsnapsync-synchronizer-flat-db-healing-enabled` is deprecated, use `--Xbonsai-full-flat-db-enabled` instead.
- `--Xbonsai-limit-trie-logs-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-log-pruning-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-logs-pruning-window-size` is deprecated, use `--bonsai-trie-logs-pruning-window-size` instead.
- `--Xsnapsync-bft-enabled` is deprecated and will be removed in a future release. SNAP sync is supported for BFT networks.
- `--tx-pool-disable-locals` has been deprecated, use `--tx-pool-no-local-priority`, instead.
- Sunsetting features - for more context on the reasoning behind the deprecation of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
    - Tessera privacy
    - Smart-contract-based (onchain) permissioning
    - Proof of Work consensus
    - Fast Sync
- Transaction indexing will be disabled by default in a future release for snap sync and checkpoint sync modes. This will break RPCs that use transaction hash for historical queries.
- Support for block creation on networks running a pre-Byzantium fork is deprecated for removal in a future release, after that in order to update Besu on nodes that build blocks, your network needs to be upgraded at least to the Byzantium fork. The main reason is to simplify world state management during block creation, since before Byzantium for each selected transaction, the receipt must contain the root hash of the modified world state, and this does not play well with the new plugin features and future work on parallelism.
- `--compatibility-eth64-forkid-enabled` has been deprecated and will be removed in the next release. [#8492](https://github.com/hyperledger/besu/pull/8492)

### Additions and Improvements
- Add Hoodi discovery DNS [#8446](https://github.com/hyperledger/besu/pull/8446)
- Decode deposit log data without Web3j [#8394](https://github.com/hyperledger/besu/issues/8394)
- Tune layered txpool default configuration for upcoming gas limit and blob count increases [#8487](https://github.com/hyperledger/besu/pull/8487)
- Removed support for Ethereum protocol versions `eth/62`, `eth/63`, `eth/64`, and `eth/65`. [#8492](https://github.com/hyperledger/besu/pull/8492)

#### Dependencies 
- Replace tuweni libs with https://github.com/Consensys/tuweni [#8330](https://github.com/hyperledger/besu/pull/8330), [#8461](https://github.com/hyperledger/besu/pull/8461) 
- Performance: Consensys/tuweni 2.7.0 reduces boxing/unboxing overhead on some EVM opcodes, like PushX and Caller [#8330](https://github.com/hyperledger/besu/pull/8330), [#8461](https://github.com/hyperledger/besu/pull/8461)

### Bug fixes
- Fix QBFT and IBFT transitions that change the mining beneficiary [#8387](https://github.com/hyperledger/besu/issues/8387)
- `eth_getLogs` - empty topic is a wildcard match [#8420](https://github.com/hyperledger/besu/pull/8420)
- Upgrade spring-security-crypto to address CVE-2025-22228 [#8448](https://github.com/hyperledger/besu/pull/8448)
- Fix restoring txpool from disk when blob transactions are present [#8481](https://github.com/hyperledger/besu/pull/8481)
- Fix for bonsai db inconsistency on abnormal shutdown [#8500](https://github.com/hyperledger/besu/pull/8500)
- Fix to add stateroot mismatches to bad block manager [#8207](https://github.com/hyperledger/besu/pull/8207)

## 25.3.0 

### Breaking Changes
NOTE: This release breaks native Windows compatibility for mainnet ethereum configurations.  As the prague(pectra) hardfork require 
BLS12-381 precompiles and besu does not currently have a pure java implementation of bls12-381, only platforms which
have support in besu-native can run mainnet ethereum configurations.  Windows support via WSL should still continue to work.

- k8s (KUBERNETES) Nat method is removed. Use docker or none instead. [#8289](https://github.com/hyperledger/besu/pull/8289)
- Change `Invalid block, unable to parse RLP` RPC error message to `Invalid block param (block not found)` [#8328](https://github.com/hyperledger/besu/pull/8328)
- Mainnet ethereum now REQUIRES native crypto libraries, so only linux and macos(darwin) are supported mainnet configurations [#8418](https://github.com/hyperledger/besu/pull/8418)

### Upcoming Breaking Changes
- `MetricSystem::createLabelledGauge` is deprecated and will be removed in a future release, replace it with `MetricSystem::createLabelledSuppliedGauge`
- `--Xsnapsync-synchronizer-flat-db-healing-enabled` is deprecated, use `--Xbonsai-full-flat-db-enabled` instead.
- `--Xbonsai-limit-trie-logs-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-log-pruning-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-logs-pruning-window-size` is deprecated, use `--bonsai-trie-logs-pruning-window-size` instead.
- `--Xsnapsync-bft-enabled` is deprecated and will be removed in a future release. SNAP sync is supported for BFT networks.
- `--tx-pool-disable-locals` has been deprecated, use `--tx-pool-no-local-priority`, instead.
- Sunsetting features - for more context on the reasoning behind the deprecation of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
    - Tessera privacy
    - Smart-contract-based (onchain) permissioning
    - Proof of Work consensus
    - Fast Sync
- Transaction indexing will be disabled by default in a future release for snap sync and checkpoint sync modes. This will break RPCs that use transaction hash for historical queries.
- Support for block creation on networks running a pre-Byzantium fork is deprecated for removal in a future release, after that in order to update Besu on nodes that build blocks, your network needs to be upgraded at least to the Byzantium fork. The main reason is to simplify world state management during block creation, since before Byzantium for each selected transaction, the receipt must contain the root hash of the modified world state, and this does not play well with the new plugin features and future work on parallelism.

### Additions and Improvements
#### Prague
- Increase mainnet and Sepolia gas limit to 36M [#8249](https://github.com/hyperledger/besu/pull/8249)
- Update Holesky and Sepolia deposit contract addresses [#8346](https://github.com/hyperledger/besu/pull/8346)
#### Plugins
- Allow plugins to propose transactions during block creation [#8268](https://github.com/hyperledger/besu/pull/8268)
- Add support for transaction permissioning rules in Plugin API [#8365](https://github.com/hyperledger/besu/pull/8365)
#### Parallelization
- Improve conflict detection by considering slots to reduce false positives [#7923](https://github.com/hyperledger/besu/pull/7923)
#### Dependencies 
- Upgrade Netty to version 4.1.118 to fix CVE-2025-24970 [#8275](https://github.com/hyperledger/besu/pull/8275)
- Update the jc-kzg-4844 dependency from 1.0.0 to 2.0.0, which is now available on Maven Central [#7849](https://github.com/hyperledger/besu/pull/7849)
- Other dependency updates [#8293](https://github.com/hyperledger/besu/pull/8293) [#8315](https://github.com/hyperledger/besu/pull/8315) [#8350](https://github.com/hyperledger/besu/pull/8350)
- Update to gradle 8.11 [#8294](https://github.com/hyperledger/besu/pull/8294) and update usage of some deprecated features [#8295](https://github.com/hyperledger/besu/pull/8295) [#8340](https://github.com/hyperledger/besu/pull/8340)
- Update gradle plugins [#8296](https://github.com/hyperledger/besu/pull/8296) [#8333](https://github.com/hyperledger/besu/pull/8333) [#8334](https://github.com/hyperledger/besu/pull/8334)
#### Other improvements
- Add TLS/mTLS options and configure the GraphQL HTTP service[#7910](https://github.com/hyperledger/besu/pull/7910)
- Update `eth_getLogs` to return a `Block not found` error when the requested block is not found. [#8290](https://github.com/hyperledger/besu/pull/8290)
- Change `Invalid block, unable to parse RLP` RPC error message to `Invalid block param (block not found)` [#8328](https://github.com/hyperledger/besu/pull/8328)
- Add IBFT1 to QBFT migration capability [#8262](https://github.com/hyperledger/besu/pull/8262)
- Support pending transaction score when saving and restoring txpool [#8363](https://github.com/hyperledger/besu/pull/8363)
- Upgrade to execution-spec-tests v4.1.0 including better EIP-2537 coverage for BLS [#8402](https://github.com/hyperledger/besu/pull/8402)
- Add era1 format to blocks import subcommand [#7935](https://github.com/hyperledger/besu/issues/7935)
- Add Hoodi as new named testnet [#8428](https://github.com/hyperledger/besu/issues/8428)

### Bug fixes
- Add missing RPC method `debug_accountRange` to `RpcMethod.java` so this method can be used with `--rpc-http-api-method-no-auth` [#8153](https://github.com/hyperledger/besu/issues/8153)
- Add a fallback pivot strategy when the safe block does not change for a long time, to make possible to complete the initial sync in case the chain is not finalizing [#8395](https://github.com/hyperledger/besu/pull/8395)
- Fix issue with new QBFT/IBFT blocks being produced under certain circumstances. [#8308](https://github.com/hyperledger/besu/issues/8308)
- Bonsai - check if storage is present before saving world state [#8434](https://github.com/hyperledger/besu/pull/8434)

## 25.2.2 hotfix
- Pectra - Sepolia: Fix for deposit contract log decoding [#8383](https://github.com/hyperledger/besu/pull/8383)

## 25.2.1 hotfix
- Pectra - update Holesky and Sepolia deposit contract addresses [#8346](https://github.com/hyperledger/besu/pull/8346)

## 25.2.0

### Breaking Changes
- `rpc-gas-cap` default value has changed from 0 (unlimited) to 50M. If you require `rpc-gas-cap` greater than 50M, you'll need to set that explicitly. [#8251](https://github.com/hyperledger/besu/issues/8251)

### Upcoming Breaking Changes
- `MetricSystem::createLabelledGauge` is deprecated and will be removed in a future release, replace it with `MetricSystem::createLabelledSuppliedGauge`
- k8s (KUBERNETES) Nat method is now deprecated and will be removed in a future release. Use docker or none instead.
- `--Xsnapsync-synchronizer-flat-db-healing-enabled` is deprecated, use `--Xbonsai-full-flat-db-enabled` instead.
- `--Xbonsai-limit-trie-logs-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-log-pruning-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-logs-pruning-window-size` is deprecated, use `--bonsai-trie-logs-pruning-window-size` instead.
- Sunsetting features - for more context on the reasoning behind the deprecation of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
    - Tessera privacy
    - Smart-contract-based (onchain) permissioning
    - Proof of Work consensus
    - Fast Sync
- Support for block creation on networks running a pre-Byzantium fork is deprecated for removal in a future release, after that in order to update Besu on nodes that build blocks, your network needs to be upgraded at least to the Byzantium fork. The main reason is to simplify world state management during block creation, since before Byzantium for each selected transaction, the receipt must contain the root hash of the modified world state, and this does not play well with the new plugin features and future work on parallelism. 
### Additions and Improvements
- Add a tx selector to skip txs from the same sender after the first not selected [#8216](https://github.com/hyperledger/besu/pull/8216)
- `rpc-gas-cap` default value has changed from 0 (unlimited) to 50M [#8251](https://github.com/hyperledger/besu/issues/8251)


#### Prague
- Add timestamps to enable Prague hardfork on Sepolia and Holesky test networks [#8163](https://github.com/hyperledger/besu/pull/8163)
- Update system call addresses to match [devnet-6](https://github.com/ethereum/execution-spec-tests/releases/) values [#8209](https://github.com/hyperledger/besu/issues/8209) 

#### Plugins
- Extend simulate transaction on pending block plugin API [#8174](https://github.com/hyperledger/besu/pull/8174)

### Bug fixes
- Fix the simulation of txs with a future nonce [#8215](https://github.com/hyperledger/besu/pull/8215)
- Bump to besu-native 1.1.2 for ubuntu 20.04 native support[#8264](https://github.com/hyperledger/besu/pull/8264)

## 25.1.0

### Breaking Changes
- `--host-whitelist` has been deprecated since 2020 and this option is removed. Use the equivalent `--host-allowlist` instead. 
- Change tracer API to include the mining beneficiary in BlockAwareOperationTracer::traceStartBlock [#8096](https://github.com/hyperledger/besu/pull/8096)
- Change the input defaults on debug_trace* calls to not trace memory by default ("disableMemory": true, "disableStack": false,  "disableStorage": false)
- Change the output format of debug_trace* and trace_* calls to match Geth behaviour

### Upcoming Breaking Changes
- `MetricSystem::createLabelledGauge` is deprecated and will be removed in a future release, replace it with `MetricSystem::createLabelledSuppliedGauge`
- k8s (KUBERNETES) Nat method is now deprecated and will be removed in a future release. Use docker or none instead.
- `--Xsnapsync-synchronizer-flat-db-healing-enabled` is deprecated, use `--Xbonsai-full-flat-db-enabled` instead.
- `--Xbonsai-limit-trie-logs-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-log-pruning-enabled` is deprecated, use `--bonsai-limit-trie-logs-enabled` instead.
- `--Xbonsai-trie-logs-pruning-window-size` is deprecated, use `--bonsai-trie-logs-pruning-window-size` instead.
- Sunsetting features - for more context on the reasoning behind the deprecation of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
  - Tessera privacy
  - Smart-contract-based (onchain) permissioning
  - Proof of Work consensus
  - Fast Sync
- Plugins 
  - `BesuConfiguration` methods `getRpcHttpHost` and `getRpcHttpPort` (which return Optionals) have been deprecated in favour of `getConfiguredRpcHttpHost` and `getConfiguredRpcHttpPort` which return the actual values, which will always be populated since these options have defaults. [#8127](https://github.com/hyperledger/besu/pull/8127) 

### Additions and Improvements
- Add RPC HTTP options to specify custom truststore and its password [#7978](https://github.com/hyperledger/besu/pull/7978)
- Retrieve all transaction receipts for a block in one request [#6646](https://github.com/hyperledger/besu/pull/6646)
- Implement EIP-7840: Add blob schedule to config files [#8042](https://github.com/hyperledger/besu/pull/8042)
- Allow gasPrice (legacy) and 1559 gasPrice params to be specified simultaneously for `eth_call`, `eth_createAccessList`, and `eth_estimateGas` [#8059](https://github.com/hyperledger/besu/pull/8059)
- Improve debug_traceBlock calls performance and reduce output size [#8103](https://github.com/hyperledger/besu/pull/8103)
- Add support for EIP-7702 transaction in the txpool [#8018](https://github.com/hyperledger/besu/pull/8018) [#7984](https://github.com/hyperledger/besu/pull/7984)
- Add support for `movePrecompileToAddress` in `StateOverrides` (`eth_call`)[8115](https://github.com/hyperledger/besu/pull/8115)
- Default target-gas-limit to 36M for holesky [#8125](https://github.com/hyperledger/besu/pull/8125)
- Add EIP-7623 - Increase calldata cost [#8093](https://github.com/hyperledger/besu/pull/8093)
- Add nonce to transaction call object [#8139](https://github.com/hyperledger/besu/pull/8139)

### Bug fixes
- Fix serialization of state overrides when `movePrecompileToAddress` is present [#8024](https://github.com/hyperledger/besu/pull/8024)
- Revise the approach for setting level_compaction_dynamic_level_bytes RocksDB configuration option [#8037](https://github.com/hyperledger/besu/pull/8037)
- Fix possible incomplete txpool restore from dump file [#7991](https://github.com/hyperledger/besu/pull/7991)

## 24.12.2 Hotfix

This is an optional hotfix to address serialization of state overrides parameter when `movePrecompileToAddress` is present. 

There is no need to upgrade from 24.12.0 (or 24.12.1) to this release if you are not yet using this functionality.

### Bug fixes
- Fix serialization of state overrides when `movePrecompileToAddress` is present [#8024](https://github.com/hyperledger/besu/pull/8024)

## 24.12.1 Hotfix

This is a hotfix to address publishing besu maven artifacts.  There are no issues with 24.12.0 other than incomplete artifact publishing, and there is no functional difference between 24.12.0 and 24.12.1 release binaries.

### Bug fixes
- Fix BOM pom publication to Artifactory [#8021](https://github.com/hyperledger/besu/pull/8021)

## 24.12.0

### Breaking Changes
- Removed Retesteth rpc service and commands [#7833](https://github.com/hyperledger/besu/pull/7783)
- TLS for P2P (early access feature) has been removed [#7942](https://github.com/hyperledger/besu/pull/7942)
- In the plugin API, `BesuContext` has been renamed to `ServiceManager` to better reflect its function, plugins must be updated to work with this version
- With the upgrade of the Prometheus Java Metrics library, there are the following changes:
  - Gauge names are not allowed to end with `total`, therefore the metric `besu_blockchain_difficulty_total` is losing the `_total` suffix
  - The `_created` timestamps are not returned by default, you can set the env var `BESU_OPTS="-Dio.prometheus.exporter.includeCreatedTimestamps=true"` to enable them
  - Some JVM metrics have changed name to adhere to the OTEL standard (see the table below), [Besu Full Grafana dashboard](https://grafana.com/grafana/dashboards/16455-besu-full/) is updated to support both names

    | Old Name                        | New Name                        |
    |---------------------------------|---------------------------------|
    | jvm_memory_bytes_committed      | jvm_memory_committed_bytes      |
    | jvm_memory_bytes_init           | jvm_memory_init_bytes           |
    | jvm_memory_bytes_max            | jvm_memory_max_bytes            |
    | jvm_memory_bytes_used           | jvm_memory_used_bytes           |
    | jvm_memory_pool_bytes_committed | jvm_memory_pool_committed_bytes |
    | jvm_memory_pool_bytes_init      | jvm_memory_pool_init_bytes      |
    | jvm_memory_pool_bytes_max       | jvm_memory_pool_max_bytes       |
    | jvm_memory_pool_bytes_used      | jvm_memory_pool_used_bytes      |

### Upcoming Breaking Changes
- Plugin API will be deprecating the BesuContext interface to be replaced with the ServiceManager interface.
- `MetricSystem::createLabelledGauge` is deprecated and will be removed in a future release, replace it with `MetricSystem::createLabelledSuppliedGauge`
- k8s (KUBERNETES) Nat method is now deprecated and will be removed in a future release
- `--host-whitelist` has been deprecated in favor of `--host-allowlist` since 2020 and will be removed in a future release
- Sunsetting features - for more context on the reasoning behind the deprecation of these features, including alternative options, read [this blog post](https://www.lfdecentralizedtrust.org/blog/sunsetting-tessera-and-simplifying-hyperledger-besu)
  - Tessera privacy
  - Smart-contract-based (onchain) permissioning
  - Proof of Work consensus
  - Fast Sync
### Additions and Improvements
- Fine tune already seen txs tracker when a tx is removed from the pool [#7755](https://github.com/hyperledger/besu/pull/7755)
- Support for enabling and configuring TLS/mTLS in WebSocket service. [#7854](https://github.com/hyperledger/besu/pull/7854)
- Create and publish Besu BOM (Bill of Materials) [#7615](https://github.com/hyperledger/besu/pull/7615) 
- Update Java dependencies [#7786](https://github.com/hyperledger/besu/pull/7786)
- Add a method to get all the transaction in the pool, to the `TransactionPoolService`, to easily access the transaction pool content from plugins [#7813](https://github.com/hyperledger/besu/pull/7813)
- Upgrade RocksDB JNI library from version 8.3.2 to 9.7.3 [#7817](https://github.com/hyperledger/besu/pull/7817)
- Add a method to check if a metric category is enabled to the plugin API [#7832](https://github.com/hyperledger/besu/pull/7832)
- Add a new metric collector for counters which get their value from suppliers [#7894](https://github.com/hyperledger/besu/pull/7894)
- Add account and state overrides to `eth_call` [#7801](https://github.com/hyperledger/besu/pull/7801) and `eth_estimateGas` [#7890](https://github.com/hyperledger/besu/pull/7890)
- Add RPC WS options to specify password file for keystore and truststore [#7970](https://github.com/hyperledger/besu/pull/7970)
- Prometheus Java Metrics library upgraded to version 1.3.3 [#7880](https://github.com/hyperledger/besu/pull/7880)
- Add histogram to Prometheus metrics system [#7944](https://github.com/hyperledger/besu/pull/7944)
- Improve newPayload and FCU logs [#7961](https://github.com/hyperledger/besu/pull/7961)
- Proper support for `pending` block tag when calling `eth_estimateGas` and `eth_createAccessList` [#7951](https://github.com/hyperledger/besu/pull/7951)

### Bug fixes
- Fix registering new metric categories from plugins [#7825](https://github.com/hyperledger/besu/pull/7825)
- Fix CVE-2024-47535 [7878](https://github.com/hyperledger/besu/pull/7878)
- Fix QBFT prepared block based proposal validation [#7875](https://github.com/hyperledger/besu/pull/7875)
- Correct default parameters for frontier transactions in `eth_call` and `eth_estimateGas` [#7965](https://github.com/hyperledger/besu/pull/7965)
- Correctly parse nonce as hex in `eth_call` account overrides [#7999](https://github.com/hyperledger/besu/pull/7999)

## 24.10.0

### Breaking Changes
- Besu will now fail to start if any plugins encounter errors during initialization. To allow Besu to continue running despite plugin errors, use the `--plugin-continue-on-error` option. [#7662](https://github.com/hyperledger/besu/pull/7662)

### Upcoming Breaking Changes
- k8s (KUBERNETES) Nat method is now deprecated and will be removed in a future release
- `--host-whitelist` has been deprecated in favor of `--host-allowlist` since 2020 and will be removed in a future release

### Additions and Improvements
- Remove privacy test classes support [#7569](https://github.com/hyperledger/besu/pull/7569)
- Add Blob Transaction Metrics [#7622](https://github.com/hyperledger/besu/pull/7622)
- Implemented support for emptyBlockPeriodSeconds in QBFT [#6965](https://github.com/hyperledger/besu/pull/6965)
- LUKSO Cancun Hardfork [#7686](https://github.com/hyperledger/besu/pull/7686)
- Add configuration of Consolidation Request Contract Address via genesis configuration [#7647](https://github.com/hyperledger/besu/pull/7647)
- Interrupt pending transaction processing on block creation timeout [#7673](https://github.com/hyperledger/besu/pull/7673)
- Align gas cap calculation for transaction simulation to Geth approach [#7703](https://github.com/hyperledger/besu/pull/7703)
- Expose chainId in the `BlockchainService` [7702](https://github.com/hyperledger/besu/pull/7702)
- Add support for `chainId` in `CallParameters` [#7720](https://github.com/hyperledger/besu/pull/7720)
- Add `--ephemery` network support for Ephemery Testnet [#7563](https://github.com/hyperledger/besu/pull/7563) thanks to [@gconnect](https://github.com/gconnect)
- Add configuration of Consolidation Request Contract Address via genesis configuration [#7647](https://github.com/hyperledger/besu/pull/7647)

### Bug fixes
- Fix mounted data path directory permissions for besu user [#7575](https://github.com/hyperledger/besu/pull/7575)
- Fix for `debug_traceCall` to handle transactions without specified gas price. [#7510](https://github.com/hyperledger/besu/pull/7510)
- Corrects a regression where custom plugin services are not initialized correctly. [#7625](https://github.com/hyperledger/besu/pull/7625)
- Fix for IBFT2 chains using the BONSAI DB format [#7631](https://github.com/hyperledger/besu/pull/7631)
- Fix reading `tx-pool-min-score` option from configuration file [#7623](https://github.com/hyperledger/besu/pull/7623)
- Fix an unhandled PeerTable exception [#7733](https://github.com/hyperledger/besu/issues/7733)
- Fix RocksDBException: Busy leading to MerkleTrieException: Unable to load trie node value [#7745](https://github.com/hyperledger/besu/pull/7745)
- If a BFT validator node is syncing, pause block production until sync has completed [#7657](https://github.com/hyperledger/besu/pull/7657)
- Fix eth_feeHistory rewards when bounded by configuration [#7750](https://github.com/hyperledger/besu/pull/7750)

## 24.9.1

### Upcoming Breaking Changes

### Breaking Changes
- Receipt compaction is enabled by default. It will no longer be possible to downgrade Besu to versions prior to 24.5.1.

### Additions and Improvements
- Add 'inbound' field to admin_peers JSON-RPC Call [#7461](https://github.com/hyperledger/besu/pull/7461)
- Add pending block header to `TransactionEvaluationContext` plugin API [#7483](https://github.com/hyperledger/besu/pull/7483)
- Add bootnode to holesky config [#7500](https://github.com/hyperledger/besu/pull/7500)
- Implement engine_getClientVersionV1 [#7512](https://github.com/hyperledger/besu/pull/7512)
- Performance optimzation for ECMUL (1 of 2) [#7509](https://github.com/hyperledger/besu/pull/7509)
- Performance optimzation for ECMUL (2 of 2) [#7543](https://github.com/hyperledger/besu/pull/7543)
- Include current chain head block when computing `eth_maxPriorityFeePerGas` [#7485](https://github.com/hyperledger/besu/pull/7485)
- Remove (old) documentation updates from the changelog [#7562](https://github.com/hyperledger/besu/pull/7562)
- Update Java and Gradle dependencies [#7571](https://github.com/hyperledger/besu/pull/7571)
- Layered txpool: new options `--tx-pool-min-score` to remove a tx from pool when its score is lower than the specified value [#7576](https://github.com/hyperledger/besu/pull/7576)
- Add `engine_getBlobsV1` method to the Engine API [#7553](https://github.com/hyperledger/besu/pull/7553)

### Bug fixes
- Fix tracing in precompiled contracts when halting for out of gas [#7318](https://github.com/hyperledger/besu/issues/7318)
- Correctly release txpool save and restore lock in case of exceptions [#7473](https://github.com/hyperledger/besu/pull/7473)
- Fix for `eth_gasPrice` could not retrieve block error [#7482](https://github.com/hyperledger/besu/pull/7482)
- Correctly drops messages that exceeds local message size limit [#5455](https://github.com/hyperledger/besu/pull/7507)
- **DebugMetrics**: Fixed a `ClassCastException` occurring in `DebugMetrics` when handling nested metric structures. Previously, `Double` values within these structures were incorrectly cast to `Map` objects, leading to errors. This update allows for proper handling of both direct values and nested structures at the same level. Issue# [#7383](https://github.com/hyperledger/besu/pull/7383)
- `evmtool` was not respecting the `--genesis` setting, resulting in unexpected trace results. [#7433](https://github.com/hyperledger/besu/pull/7433)
- The genesis config override `contractSizeLimit` was not wired into code size limits [#7557](https://github.com/hyperledger/besu/pull/7557)
- Fix incorrect key filtering in LayeredKeyValueStorage stream [#7535](https://github.com/hyperledger/besu/pull/7557)
- Layered txpool: do not send notifications when moving tx between layers [#7539](https://github.com/hyperledger/besu/pull/7539)
- Layered txpool: fix for unsent drop notifications on remove [#7538](https://github.com/hyperledger/besu/pull/7538)
- Honor block number or tag parameter in eth_estimateGas and eth_createAccessList [#7502](https://github.com/hyperledger/besu/pull/7502)
- Fixed NPE during DefaultBlockchain object initialization [#7601](https://github.com/hyperledger/besu/pull/7601)

## 24.9.0

This release version has been deprecated release due to CI bug

## 24.8.0

### Upcoming Breaking Changes
- Receipt compaction will be enabled by default in a future version of Besu. After this change it will not be possible to downgrade to the previous Besu version.
- --Xbonsai-limit-trie-logs-enabled is deprecated, use --bonsai-limit-trie-logs-enabled instead
- --Xbonsai-trie-logs-pruning-window-size is deprecated, use --bonsai-trie-logs-pruning-window-size instead
- `besu storage x-trie-log` subcommand is deprecated, use `besu storage trie-log` instead
- Allow configuration of Withdrawal Request Contract Address via genesis configuration [#7356](https://github.com/hyperledger/besu/pull/7356)

### Breaking Changes
- Remove long-deprecated `perm*whitelist*` methods [#7401](https://github.com/hyperledger/besu/pull/7401)

### Additions and Improvements
- Allow optional loading of `jemalloc` (if installed) by setting the environment variable `BESU_USING_JEMALLOC` to true/false. It that env is not set at all it will behave as if it is set to `true`
- Expose set finalized/safe block in plugin api BlockchainService. These method can be used by plugins to set finalized/safe block for a PoA network (such as QBFT, IBFT and Clique).[#7382](https://github.com/hyperledger/besu/pull/7382)
- In process RPC service [#7395](https://github.com/hyperledger/besu/pull/7395)
- Added support for tracing private transactions using `priv_traceTransaction` API. [#6161](https://github.com/hyperledger/besu/pull/6161)
- Wrap WorldUpdater into EVMWorldupdater [#7434](https://github.com/hyperledger/besu/pull/7434)
- Bump besu-native to 0.9.4 [#7456](https://github.com/hyperledger/besu/pull/7456)=

### Bug fixes
- Correct entrypoint in Docker evmtool [#7430](https://github.com/hyperledger/besu/pull/7430)
- Fix protocol schedule check for devnets [#7429](https://github.com/hyperledger/besu/pull/7429)
- Fix behaviour when starting in a pre-merge network [#7431](https://github.com/hyperledger/besu/pull/7431)
- Fix Null pointer from DNS daemon [#7505](https://github.com/hyperledger/besu/issues/7505)

### Download Links
https://github.com/hyperledger/besu/releases/tag/24.8.0
https://github.com/hyperledger/besu/releases/download/24.8.0/besu-24.8.0.tar.gz / sha256 9671157a623fb94005357bc409d1697a0d62bb6fd434b1733441bb301a9534a4
https://github.com/hyperledger/besu/releases/download/24.8.0/besu-24.8.0.zip / sha256 9ee217d2188e8da89002c3f42e4f85f89aab782e9512bd03520296f0a4dcdd90

## 24.7.1

### Breaking Changes
- Remove deprecated sync modes (X_SNAP and X_CHECKPOINT). Use SNAP and CHECKPOINT instead [#7309](https://github.com/hyperledger/besu/pull/7309)
- Remove PKI-backed QBFT (deprecated in 24.5.1) Other forms of QBFT remain unchanged. [#7293](https://github.com/hyperledger/besu/pull/7293)
- Do not maintain connections to PoA bootnodes [#7358](https://github.com/hyperledger/besu/pull/7358). See [#7314](https://github.com/hyperledger/besu/pull/7314) for recommended alternative behaviour.

### Upcoming Breaking Changes
- Receipt compaction will be enabled by default in a future version of Besu. After this change it will not be possible to downgrade to the previous Besu version.
- --Xbonsai-limit-trie-logs-enabled is deprecated, use --bonsai-limit-trie-logs-enabled instead
- --Xbonsai-trie-logs-pruning-window-size is deprecated, use --bonsai-trie-logs-pruning-window-size instead
- `besu storage x-trie-log` subcommand is deprecated, use `besu storage trie-log` instead

### Additions and Improvements
- `--Xsnapsync-bft-enabled` option enables experimental support for snap sync with IBFT/QBFT permissioned Bonsai-DB chains [#7140](https://github.com/hyperledger/besu/pull/7140)
- Add support to load external profiles using `--profile` [#7265](https://github.com/hyperledger/besu/issues/7265)
- `privacy-nonce-always-increments` option enables private transactions to always increment the nonce, even if the transaction is invalid [#6593](https://github.com/hyperledger/besu/pull/6593)
- Added `block-test` subcommand to the evmtool which runs blockchain reference tests [#7293](https://github.com/hyperledger/besu/pull/7293)
- removed PKI backed QBFT [#7310](https://github.com/hyperledger/besu/pull/7310)
- Implement gnark-crypto for eip-2537 [#7316](https://github.com/hyperledger/besu/pull/7316)
- Improve blob size transaction selector [#7312](https://github.com/hyperledger/besu/pull/7312)
- Added EIP-7702 [#7237](https://github.com/hyperledger/besu/pull/7237)
- Implement gnark-crypto for eip-196 [#7262](https://github.com/hyperledger/besu/pull/7262)
- Add trie log pruner metrics [#7352](https://github.com/hyperledger/besu/pull/7352)
- Force bonsai-limit-trie-logs-enabled=false when sync-mode=FULL instead of startup error [#7357](https://github.com/hyperledger/besu/pull/7357)
- `--Xbonsai-parallel-tx-processing-enabled` option enables executing transactions in parallel during block processing for Bonsai nodes
- Reduce default trie log pruning window size from 30,000 to 5,000 [#7365](https://github.com/hyperledger/besu/pull/7365)
- Add option `--poa-discovery-retry-bootnodes` for PoA networks to always use bootnodes during peer refresh, not just on first start [#7314](https://github.com/hyperledger/besu/pull/7314)

### Bug fixes
- Fix `eth_call` deserialization to correctly ignore unknown fields in the transaction object. [#7323](https://github.com/hyperledger/besu/pull/7323)
- Prevent Besu from starting up with sync-mode=FULL and bonsai-limit-trie-logs-enabled=true for private networks [#7357](https://github.com/hyperledger/besu/pull/7357)
- Add 30 second timeout to trie log pruner preload [#7365](https://github.com/hyperledger/besu/pull/7365)
- Avoid executing pruner preload during trie log subcommands [#7366](https://github.com/hyperledger/besu/pull/7366)

### Download Links
https://github.com/hyperledger/besu/releases/tag/24.7.1
https://github.com/hyperledger/besu/releases/download/24.7.1/besu-24.7.1.tar.gz / sha256 59ac352a86fd887225737a5fe4dad1742347edd3c3fbed98b079177e4ea8d544
https://github.com/hyperledger/besu/releases/download/24.7.1/besu-24.7.1.zip / sha256 e616f8100f026a71a146a33847b40257c279b38085b17bb991df045cccb6f832

## 24.7.0

### Upcoming Breaking Changes
- Receipt compaction will be enabled by default in a future version of Besu. After this change it will not be possible to downgrade to the previous Besu version.
- PKI-backed QBFT will be removed in a future version of Besu. Other forms of QBFT will remain unchanged.
- --Xbonsai-limit-trie-logs-enabled is deprecated, use --bonsai-limit-trie-logs-enabled instead
- --Xbonsai-trie-logs-pruning-window-size is deprecated, use --bonsai-trie-logs-pruning-window-size instead
- `besu storage x-trie-log` subcommand is deprecated, use `besu storage trie-log` instead

### Breaking Changes
- `Xp2p-peer-lower-bound` has been removed. [#7247](https://github.com/hyperledger/besu/pull/7247)

### Additions and Improvements
- Support for eth_maxPriorityFeePerGas [#5658](https://github.com/hyperledger/besu/issues/5658)
- Improve genesis state performance at startup [#6977](https://github.com/hyperledger/besu/pull/6977)
- Enable continuous profiling with default setting [#7006](https://github.com/hyperledger/besu/pull/7006)
- A full and up to date implementation of EOF for Prague [#7169](https://github.com/hyperledger/besu/pull/7169)
- Add Subnet-Based Peer Permissions.  [#7168](https://github.com/hyperledger/besu/pull/7168)
- Reduce lock contention on transaction pool when building a block [#7180](https://github.com/hyperledger/besu/pull/7180)
- Update Docker base image to Ubuntu 24.04 [#7251](https://github.com/hyperledger/besu/pull/7251)
- Add LUKSO as predefined network name [#7223](https://github.com/hyperledger/besu/pull/7223)
- Refactored how code, initcode, and max stack size are configured in forks. [#7245](https://github.com/hyperledger/besu/pull/7245)
- Nodes in a permissioned chain maintain (and retry) connections to bootnodes [#7257](https://github.com/hyperledger/besu/pull/7257)
- Promote experimental `besu storage x-trie-log` subcommand to production-ready [#7278](https://github.com/hyperledger/besu/pull/7278)
- Enhanced BFT round-change diagnostics [#7271](https://github.com/hyperledger/besu/pull/7271)

### Bug fixes
- Validation errors ignored in accounts-allowlist and empty list [#7138](https://github.com/hyperledger/besu/issues/7138)
- Fix "Invalid block detected" for BFT chains using Bonsai DB [#7204](https://github.com/hyperledger/besu/pull/7204)
- Fix "Could not confirm best peer had pivot block" [#7109](https://github.com/hyperledger/besu/issues/7109)
- Fix "Chain Download Halt" [#6884](https://github.com/hyperledger/besu/issues/6884)

### Download Links
https://github.com/hyperledger/besu/releases/tag/24.7.0
https://github.com/hyperledger/besu/releases/download/24.7.0/besu-24.7.0.tar.gz / sha256 96cf47defd1d8c10bfc22634e53e3d640eaa81ef58cb0808e5f4265998979530
https://github.com/hyperledger/besu/releases/download/24.7.0/besu-24.7.0.zip / sha256 7e92e2eb469be197af8c8ca7ac494e7a2e7ee91cbdb02d99ff87fb5209e0c2a0



## 24.6.0

### Breaking Changes
- Java 21 has been enforced as minimum version to build and run Besu.
- With --Xbonsai-limit-trie-logs-enabled by default in this release, historic trie log data will be removed from the database unless sync-mode=FULL. It respects the --bonsai-historical-block-limit setting so shouldn't break any RPCs, but may be breaking if you are accessing this data from the database directly. Can be disabled with --bonsai-limit-trie-logs-enabled=false
- In profile=ENTERPRISE, use sync-mode=FULL (instead of FAST) and data-storage-format=FOREST (instead of BONSAI) [#7186](https://github.com/hyperledger/besu/pull/7186)
  - If this breaks your node, you can reset sync-mode=FAST and data-storage-format=BONSAI

### Upcoming Breaking Changes
- Receipt compaction will be enabled by default in a future version of Besu. After this change it will not be possible to downgrade to the previous Besu version.
- PKI-backed QBFT will be removed in a future version of Besu. Other forms of QBFT will remain unchanged.
- --Xbonsai-limit-trie-logs-enabled is deprecated, use --bonsai-limit-trie-logs-enabled instead
- --Xbonsai-trie-logs-pruning-window-size is deprecated, use --bonsai-trie-logs-pruning-window-size instead

### Additions and Improvements
- Add two counters to DefaultBlockchain in order to be able to calculate TPS and Mgas/s [#7105](https://github.com/hyperledger/besu/pull/7105)
- Enable --Xbonsai-limit-trie-logs-enabled by default, unless sync-mode=FULL [#7181](https://github.com/hyperledger/besu/pull/7181)
- Promote experimental --Xbonsai-limit-trie-logs-enabled to production-ready, --bonsai-limit-trie-logs-enabled [#7192](https://github.com/hyperledger/besu/pull/7192)
- Promote experimental --Xbonsai-trie-logs-pruning-window-size to production-ready, --bonsai-trie-logs-pruning-window-size [#7192](https://github.com/hyperledger/besu/pull/7192)
- `admin_nodeInfo` JSON/RPC call returns the currently active EVM version [#7127](https://github.com/hyperledger/besu/pull/7127)
- Improve the selection of the most profitable built block [#7174](https://github.com/hyperledger/besu/pull/7174)

### Bug fixes
- Make `eth_gasPrice` aware of the base fee market [#7102](https://github.com/hyperledger/besu/pull/7102)

### Download Links
https://github.com/hyperledger/besu/releases/tag/24.6.0
https://github.com/hyperledger/besu/releases/download/24.6.0/besu-24.6.0.tar.gz / sha256 fa86e5c6873718cd568e3326151ce06957a5e7546b52df79a831ea9e39b857ab
https://github.com/hyperledger/besu/releases/download/24.6.0/besu-24.6.0.zip / sha256 8b2d3a674cd7ead68b9ca68fea21e46d5ec9b278bbadc73f8c13c6a1e1bc0e4d

## 24.5.2

### Upcoming Breaking Changes
- Version 24.5.x will be the last series to support Java 17. Next release after versions 24.5.x will require Java 21 to build and run.
- Receipt compaction will be enabled by default in a future version of Besu. After this change it will not be possible to downgrade to the previous Besu version.
- PKI-backed QBFT will be removed in a future version of Besu. Other forms of QBFT will remain unchanged.

### Additions and Improvements
- Remove deprecated Goerli testnet [#7049](https://github.com/hyperledger/besu/pull/7049)
- Default bonsai to use full-flat db and code-storage-by-code-hash [#6984](https://github.com/hyperledger/besu/pull/6894)
- New RPC methods miner_setExtraData and miner_getExtraData [#7078](https://github.com/hyperledger/besu/pull/7078)
- Disconnect peers that have multiple discovery ports since they give us bad neighbours [#7089](https://github.com/hyperledger/besu/pull/7089)
- Port Tuweni dns-discovery into Besu. [#7129](https://github.com/hyperledger/besu/pull/7129)

### Known Issues
- [Frequency: occasional < 10%] Chain download halt. Only affects new syncs (new nodes syncing from scratch). Symptom: Block import halts, despite having a full set of peers and world state downloading finishing. Generally restarting besu will resolve the issue. We are tracking this in [#6884](https://github.com/hyperledger/besu/pull/6884)

### Bug fixes
- Fix parsing `gasLimit` parameter when its value is > `Long.MAX_VALUE` [#7116](https://github.com/hyperledger/besu/pull/7116)
- Skip validation of withdrawals when importing BFT blocks since withdrawals don't apply to BFT chains [#7115](https://github.com/hyperledger/besu/pull/7115)
- Make `v` abd `yParity` match in type 1 and 2 transactions in JSON-RPC and GraphQL [#7139](https://github.com/hyperledger/besu/pull/7139)

### Download Links
https://github.com/hyperledger/besu/releases/tag/24.5.2
https://github.com/hyperledger/besu/releases/download/24.5.2/besu-24.5.2.tar.gz / sha256 4049bf48022ae073065b46e27088399dfb22035e9134ed4ac2c86dd8c5b5fbe9
https://github.com/hyperledger/besu/releases/download/24.5.2/besu-24.5.2.zip / sha256 23966b501a69e320e8f8f46a3d103ccca45b53f8fee35a6543bd9a260b5784ee

## 24.5.1

### Breaking Changes
- RocksDB database metadata format has changed to be more expressive, the migration of an existing metadata file to the new format is automatic at startup. Before performing a downgrade to a previous version it is mandatory to revert to the original format using the subcommand `besu --data-path=/path/to/besu/datadir storage revert-metadata v2-to-v1`.
- BFT networks won't start with SNAP or CHECKPOINT sync (previously Besu would start with this config but quietly fail to sync, so it's now more obvious that it won't work) [#6625](https://github.com/hyperledger/besu/pull/6625), [#6667](https://github.com/hyperledger/besu/pull/6667)
- Forest pruning has been removed, it was deprecated since 24.1.0. In case you are still using it you must now remove any of the following options: `pruning-enabled`, `pruning-blocks-retained` and `pruning-block-confirmations`, from your configuration, and you may want to consider switching to Bonsai.
- Deprecated Goerli testnet has been removed.

### Upcoming Breaking Changes
- Version 24.5.x will be the last series to support Java 17. Next release after versions 24.5.x will require Java 21 to build and run.
- Receipt compaction will be enabled by default in a future version of Besu. After this change it will not be possible to downgrade to the previous Besu version.
- PKI-backed QBFT will be removed in a future version of Besu. Other forms of QBFT will remain unchanged. 

### Known Issues
- [Frequency: occasional < 10%] Chain download halt. Only affects new syncs (new nodes syncing from scratch). Symptom: Block import halts, despite having a full set of peers and world state downloading finishing. Generally restarting besu will resolve the issue. We are tracking this in [#6884](https://github.com/hyperledger/besu/pull/6884)
- [Frequency: occasional < 10%] Low peer numbers. More likely to occur on testnets (holesky and sepolia) but also can occur on mainnet. Symptom: peer count stays at 0 for an hour or more. Generally restarting besu will resolve the issue. We are tracking this in [#6805](https://github.com/hyperledger/besu/pull/6805)

### Additions and Improvements
- Update "host allow list" logic to transition from deprecated `host()` method to suggested `authority()` method.[#6878](https://github.com/hyperledger/besu/issues/6878)
- `txpool_besuPendingTransactions`change parameter `numResults` to optional parameter [#6708](https://github.com/hyperledger/besu/pull/6708)
- Extend `Blockchain` service [#6592](https://github.com/hyperledger/besu/pull/6592)
- Add bft-style `blockperiodseconds` transitions to Clique [#6596](https://github.com/hyperledger/besu/pull/6596)
- Add `createemptyblocks` transitions to Clique [#6608](https://github.com/hyperledger/besu/pull/6608)
- RocksDB database metadata refactoring [#6555](https://github.com/hyperledger/besu/pull/6555)
- Make layered txpool aware of `minGasPrice` and `minPriorityFeePerGas` dynamic options [#6611](https://github.com/hyperledger/besu/pull/6611)
- Update commons-compress to 1.26.0 [#6648](https://github.com/hyperledger/besu/pull/6648)
- Update Vert.x to 4.5.4 [#6666](https://github.com/hyperledger/besu/pull/6666)
- Refactor and extend `TransactionPoolValidatorService` [#6636](https://github.com/hyperledger/besu/pull/6636)
- Introduce `TransactionSimulationService` [#6686](https://github.com/hyperledger/besu/pull/6686)
- Transaction call object to accept both `input` and `data` field simultaneously if they are set to equal values [#6702](https://github.com/hyperledger/besu/pull/6702)
- `eth_call` for blob tx allows for empty `maxFeePerBlobGas` [#6731](https://github.com/hyperledger/besu/pull/6731)
- Extend error handling of plugin RPC methods [#6759](https://github.com/hyperledger/besu/pull/6759)
- Added engine_newPayloadV4 and engine_getPayloadV4 methods [#6783](https://github.com/hyperledger/besu/pull/6783)
- Reduce storage size of receipts [#6602](https://github.com/hyperledger/besu/pull/6602)
- Dedicated log marker for invalid txs removed from the txpool [#6826](https://github.com/hyperledger/besu/pull/6826)
- Prevent startup with BONSAI and privacy enabled [#6809](https://github.com/hyperledger/besu/pull/6809)
- Remove deprecated Forest pruning [#6810](https://github.com/hyperledger/besu/pull/6810)
- Experimental Snap Sync Server [#6640](https://github.com/hyperledger/besu/pull/6640)
- Upgrade Reference Tests to 13.2 [#6854](https://github.com/hyperledger/besu/pull/6854)
- Update Web3j dependencies [#6811](https://github.com/hyperledger/besu/pull/6811)
- Add `tx-pool-blob-price-bump` option to configure the price bump percentage required to replace blob transactions (by default 100%) [#6874](https://github.com/hyperledger/besu/pull/6874)
- Log detailed timing of block creation steps [#6880](https://github.com/hyperledger/besu/pull/6880)
- Expose transaction count by type metrics for the layered txpool [#6903](https://github.com/hyperledger/besu/pull/6903)
- Expose bad block events via the BesuEvents plugin API  [#6848](https://github.com/hyperledger/besu/pull/6848)
- Add RPC errors metric [#6919](https://github.com/hyperledger/besu/pull/6919/)
- Add `rlp decode` subcommand to decode IBFT/QBFT extraData to validator list [#6895](https://github.com/hyperledger/besu/pull/6895)
- Allow users to specify which plugins are registered [#6700](https://github.com/hyperledger/besu/pull/6700)
- Layered txpool tuning for blob transactions [#6940](https://github.com/hyperledger/besu/pull/6940)

### Bug fixes
- Fix txpool dump/restore race condition [#6665](https://github.com/hyperledger/besu/pull/6665)
- Make block transaction selection max time aware of PoA transitions [#6676](https://github.com/hyperledger/besu/pull/6676)
- Don't enable the BFT mining coordinator when running sub commands such as `blocks export` [#6675](https://github.com/hyperledger/besu/pull/6675)
- In JSON-RPC return optional `v` fields for type 1 and type 2 transactions [#6762](https://github.com/hyperledger/besu/pull/6762)
- Fix Shanghai/QBFT block import bug when syncing new nodes [#6765](https://github.com/hyperledger/besu/pull/6765)
- Fix to avoid broadcasting full blob txs, instead of only the tx announcement, to a subset of nodes [#6835](https://github.com/hyperledger/besu/pull/6835)
- Snap client fixes discovered during snap server testing [#6847](https://github.com/hyperledger/besu/pull/6847)
- Correctly initialize the txpool as disabled on creation [#6890](https://github.com/hyperledger/besu/pull/6890)
- Fix worldstate download halt when using snap sync during initial sync [#6981](https://github.com/hyperledger/besu/pull/6981)
- Fix chain halt due to peers only partially responding with headers. And worldstate halts caused by a halt in the chain sync [#7027](https://github.com/hyperledger/besu/pull/7027)

### Download Links
https://github.com/hyperledger/besu/releases/tag/24.5.1
https://github.com/hyperledger/besu/releases/download/24.5.1/besu-24.5.1.tar.gz / sha256 77e39b21dbd4186136193fc6e832ddc1225eb5078a5ac980fb754b33ad35d554
https://github.com/hyperledger/besu/releases/download/24.5.1/besu-24.5.1.zip / sha256 13d75b6b22e1303f39fd3eaddf736b24ca150b2bafa7b98fce7c7782e54b213f

## 24.3.0

### Breaking Changes
- SNAP - Snap sync is now the default for named networks [#6530](https://github.com/hyperledger/besu/pull/6530)
  - if you want to use the previous default behavior, you'll need to specify `--sync-mode=FAST`
- BONSAI - Default data storage format is now Bonsai [#6536](https://github.com/hyperledger/besu/pull/6536)
  - if you had previously used the default (FOREST), at startup you will get an error indicating the mismatch
    `Mismatch: DB at '/your-path' is FOREST (Version 1) but config expects BONSAI (Version 2). Please check your config.`
  - to fix this mismatch, specify the format explicitly using `--data-storage-format=FOREST`
- Following the OpenMetrics convention, the updated Prometheus client adds the `_total` suffix to every metrics of type counter, with the effect that some existing metrics have been renamed to have this suffix. If you are using the official Besu Grafana dashboard [(available here)](https://grafana.com/grafana/dashboards/16455-besu-full/), just update it to the latest revision, that accepts the old and the new name of the affected metrics. If you have a custom dashboard or use the metrics in other ways, then you need to manually update it to support the new naming.
- The `trace-filter` method in JSON-RPC API now has a default block range limit of 1000, adjustable with `--rpc-max-trace-filter-range` (thanks @alyokaz) [#6446](https://github.com/hyperledger/besu/pull/6446)
- Requesting the Ethereum Node Record (ENR) to acquire the fork id from bonded peers is now enabled by default, so the following change has been made [#5628](https://github.com/hyperledger/besu/pull/5628):
- `--Xfilter-on-enr-fork-id` has been removed. To disable the feature use `--filter-on-enr-fork-id=false`.
- `--engine-jwt-enabled` has been removed. Use `--engine-jwt-disabled` instead. [#6491](https://github.com/hyperledger/besu/pull/6491)
- Release docker images now provided at ghcr.io instead of dockerhub

### Deprecations
- X_SNAP and X_CHECKPOINT are marked for deprecation and will be removed in 24.6.0 in favor of SNAP and CHECKPOINT [#6405](https://github.com/hyperledger/besu/pull/6405)
- `--Xp2p-peer-lower-bound` is deprecated. [#6501](https://github.com/hyperledger/besu/pull/6501)

### Upcoming Breaking Changes
- `--Xbonsai-limit-trie-logs-enabled` will be removed. You will need to use `--bonsai-limit-trie-logs-enabled` instead. Additionally, this limit will change to be enabled by default.
  - If you do not want the limit enabled (eg you have `--bonsai-historical-block-limit` set < 512), you need to explicitly disable it using `--bonsai-limit-trie-logs-enabled=false` or increase the limit. [#6561](https://github.com/hyperledger/besu/pull/6561)

### Additions and Improvements
- Upgrade Prometheus and Opentelemetry dependencies [#6422](https://github.com/hyperledger/besu/pull/6422)
- Add `OperationTracer.tracePrepareTransaction`, where the sender account has not yet been altered[#6453](https://github.com/hyperledger/besu/pull/6453)
- Improve the high spec flag by limiting it to a few column families [#6354](https://github.com/hyperledger/besu/pull/6354)
- Log blob count when importing a block via Engine API [#6466](https://github.com/hyperledger/besu/pull/6466)
- Introduce `--Xbonsai-limit-trie-logs-enabled` experimental feature which by default will only retain the latest 512 trie logs, saving about 3GB per week in database growth [#5390](https://github.com/hyperledger/besu/issues/5390)
- Introduce `besu storage x-trie-log prune` experimental offline subcommand which will prune all redundant trie logs except the latest 512 [#6303](https://github.com/hyperledger/besu/pull/6303)
- Improve flat trace generation performance [#6472](https://github.com/hyperledger/besu/pull/6472)
- SNAP and CHECKPOINT sync - early access flag removed so now simply SNAP and CHECKPOINT [#6405](https://github.com/hyperledger/besu/pull/6405)
- X_SNAP and X_CHECKPOINT are marked for deprecation and will be removed in 24.4.0
- Github Actions based build.
- Introduce caching mechanism to optimize Keccak hash calculations for account storage slots during block processing [#6452](https://github.com/hyperledger/besu/pull/6452)
- Added configuration options for `pragueTime` to genesis file for Prague fork development [#6473](https://github.com/hyperledger/besu/pull/6473)
- Moving trielog storage to RocksDB's blobdb to improve write amplications [#6289](https://github.com/hyperledger/besu/pull/6289)
- Support for `shanghaiTime` fork and Shanghai EVM smart contracts in QBFT/IBFT chains [#6353](https://github.com/hyperledger/besu/pull/6353)
- Change ExecutionHaltReason for contract creation collision case to return ILLEGAL_STATE_CHANGE [#6518](https://github.com/hyperledger/besu/pull/6518) 
- Experimental feature `--Xbonsai-code-using-code-hash-enabled` for storing Bonsai code storage by code hash [#6505](https://github.com/hyperledger/besu/pull/6505)
- More accurate column size `storage rocksdb usage` subcommand [#6540](https://github.com/hyperledger/besu/pull/6540)
- Adds `storage rocksdb x-stats` subcommand [#6540](https://github.com/hyperledger/besu/pull/6540)
- New `eth_blobBaseFee`JSON-RPC method [#6581](https://github.com/hyperledger/besu/pull/6581)
- Add blob transaction support to `eth_call` [#6661](https://github.com/hyperledger/besu/pull/6661)
- Add blobs to `eth_feeHistory` [#6679](https://github.com/hyperledger/besu/pull/6679)
- Upgrade reference tests to version 13.1 [#6574](https://github.com/hyperledger/besu/pull/6574)
- Extend `BesuConfiguration` service [#6584](https://github.com/hyperledger/besu/pull/6584)
- Add `ethereum_min_gas_price` and `ethereum_min_priority_fee` metrics to track runtime values of `min-gas-price` and `min-priority-fee` [#6587](https://github.com/hyperledger/besu/pull/6587)
- Option to perform version incompatibility checks when starting Besu. In this first release of the feature, if `--version-compatibility-protection` is set to true it checks that the version of Besu being started is the same or higher than the previous version. [6307](https://github.com/hyperledger/besu/pull/6307)
- Moved account frame warming from GasCalculator into the Call operations [#6557](https://github.com/hyperledger/besu/pull/6557)

### Bug fixes
- Fix the way an advertised host configured with `--p2p-host` is treated when communicating with the originator of a PING packet [#6225](https://github.com/hyperledger/besu/pull/6225)
- Fix `poa-block-txs-selection-max-time` option that was inadvertently reset to its default after being configured [#6444](https://github.com/hyperledger/besu/pull/6444)
- Fix for tx incorrectly discarded when there is a timeout during block creation [#6563](https://github.com/hyperledger/besu/pull/6563)
- Fix traces so that call gas costing in traces matches other clients traces [#6525](https://github.com/hyperledger/besu/pull/6525)

### Download Links
https://github.com/hyperledger/besu/releases/tag/24.3.0
https://github.com/hyperledger/besu/releases/download/24.3.0/besu-24.3.0.tar.gz / sha256 8037ce51bb5bb396d29717a812ea7ff577b0d6aa341d67d1e5b77cbc55b15f84
https://github.com/hyperledger/besu/releases/download/24.3.0/besu-24.3.0.zip / sha256 41ea2ca734a3b377f43ee178166b5b809827084789378dbbe4e5b52bbd8e0674

## 24.1.2

### Bug fixes
- Fix ETC Spiral upgrade breach of consensus [#6524](https://github.com/hyperledger/besu/pull/6524)

### Additions and Improvements
- Adds timestamp to enable Cancun upgrade on mainnet [#6545](https://github.com/hyperledger/besu/pull/6545)
- Github Actions based build.[#6427](https://github.com/hyperledger/besu/pull/6427)

### Download Links
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/24.1.2/besu-24.1.2.zip / sha256 9033f300edd81c770d3aff27a29f59dd4b6142a113936886a8f170718e412971
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/24.1.2/besu-24.1.2.tar.gz / sha256 082db8cf4fb67527aa0dd757e5d254b3b497f5027c23287f9c0a74a6a743bf08

## 24.1.1

### Breaking Changes
- New `EXECUTION_HALTED` error returned if there is an error executing or simulating a transaction, with the reason for execution being halted. Replaces the generic `INTERNAL_ERROR` return code in certain cases which some applications may be checking for [#6343](https://github.com/hyperledger/besu/pull/6343)
- The Besu Docker images with `openjdk-latest` tags since 23.10.3 were incorrectly using UID 1001 instead of 1000 for the container's `besu` user. The user now uses 1000 again. Containers created from or migrated to images using UID 1001 will need to chown their persistent database files to UID 1000 (thanks @h4l) [#6360](https://github.com/hyperledger/besu/pull/6360)
- The deprecated `--privacy-onchain-groups-enabled` option has now been removed. Use the `--privacy-flexible-groups-enabled` option instead. [#6411](https://github.com/hyperledger/besu/pull/6411)
- The time that can be spent selecting transactions during block creation is not capped at 5 seconds for PoS and PoW networks, and for PoA networks, at 75% of the block period specified in the genesis. This is to prevent possible DoS attacks in case a single transaction is taking too long to execute, and to have a stable block production rate. This could be a breaking change if an existing network needs to accept transactions that take more time to execute than the newly introduced limit. If it is mandatory for these networks to keep processing these long processing transaction, then the default value of `block-txs-selection-max-time` or `poa-block-txs-selection-max-time` needs to be tuned accordingly. [#6423](https://github.com/hyperledger/besu/pull/6423)

### Deprecations

### Additions and Improvements
- Optimize RocksDB WAL files, allows for faster restart and a more linear disk space utilization [#6328](https://github.com/hyperledger/besu/pull/6328)
- Disable transaction handling when the node is not in sync, to avoid unnecessary transaction validation work [#6302](https://github.com/hyperledger/besu/pull/6302)
- Introduce TransactionEvaluationContext to pass data between transaction selectors and plugin, during block creation [#6381](https://github.com/hyperledger/besu/pull/6381)
- Upgrade dependencies [#6377](https://github.com/hyperledger/besu/pull/6377)
- Upgrade `com.fasterxml.jackson` dependencies [#6378](https://github.com/hyperledger/besu/pull/6378)
- Upgrade Guava dependency [#6396](https://github.com/hyperledger/besu/pull/6396)
- Upgrade Mockito [#6397](https://github.com/hyperledger/besu/pull/6397)
- Upgrade `tech.pegasys.discovery:discovery` [#6414](https://github.com/hyperledger/besu/pull/6414)
- Options to tune the max allowed time that can be spent selecting transactions during block creation are now stable [#6423](https://github.com/hyperledger/besu/pull/6423)
- Support for "pending" in `qbft_getValidatorsByBlockNumber` [#6436](https://github.com/hyperledger/besu/pull/6436)

### Bug fixes
- INTERNAL_ERROR from `eth_estimateGas` JSON/RPC calls [#6344](https://github.com/hyperledger/besu/issues/6344)
- Fix Besu Docker images with `openjdk-latest` tags since 23.10.3 using UID 1001 instead of 1000 for the `besu` user [#6360](https://github.com/hyperledger/besu/pull/6360)
- Fluent EVM API definition for Tangerine Whistle had incorrect code size validation configured [#6382](https://github.com/hyperledger/besu/pull/6382)
- Correct mining beneficiary for Clique networks in TraceServiceImpl [#6390](https://github.com/hyperledger/besu/pull/6390)
- Fix to gas limit delta calculations used in block production. Besu should now increment or decrement the block gas limit towards its target correctly (thanks @arbora) #6425
- Ensure Backward Sync waits for initial sync before starting a session [#6455](https://github.com/hyperledger/besu/issues/6455)
- Silence the noisy DNS query errors [#6458](https://github.com/hyperledger/besu/issues/6458)

### Download Links
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/24.1.1/besu-24.1.1.zip / sha256 e23c5b790180756964a70dcdd575ee2ed2c2efa79af00bce956d23bd2f7dc67c
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/24.1.1/besu-24.1.1.tar.gz / sha256 4b0ddd5a25be2df5d2324bff935785eb63e4e3a5f421614ea690bacb5b9cb344

### Errata
Note, due to a CI race with the release job, the initial published version of 24.1.1 were overwritten by artifacts generated from the same sources, but differ in their embedded timestamps. The initial SHAs are noted here but are deprecated:
~~https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/24.1.1/besu-24.1.1.zip / sha256 b6b64f939e0bb4937ce90fc647e0a7073ce3e359c10352b502059955070a60c6
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/24.1.1/besu-24.1.1.tar.gz / sha256 cfcae04c30769bf338b0740ac65870f9346d3469931bb46cdba3b2f65d311e7a~~


## 24.1.0

### Breaking Changes

### Deprecations
- Forest pruning (`pruning-enabled` option) is deprecated and will be removed soon. To save disk space consider switching to Bonsai data storage format [#6230](https://github.com/hyperledger/besu/pull/6230)

### Additions and Improvements
- Add error messages on authentication failures with username and password [#6212](https://github.com/hyperledger/besu/pull/6212)
- New `Sequenced` transaction pool. The pool is an evolution of the `legacy` pool and is likely to be more suitable to enterprise or permissioned chains than the `layered` transaction pool. Select to use this pool with `--tx-pool=sequenced`. Supports the same options as the `legacy` pool [#6274](https://github.com/hyperledger/besu/issues/6274)
- Set Ethereum Classic mainnet activation block for Spiral network upgrade [#6267](https://github.com/hyperledger/besu/pull/6267)
- Add custom genesis file name to config overview if specified [#6297](https://github.com/hyperledger/besu/pull/6297)
- Update Gradle plugins and replace unmaintained License Gradle Plugin with the actively maintained Gradle License Report [#6275](https://github.com/hyperledger/besu/pull/6275)
- Optimize RocksDB WAL files, allows for faster restart and a more linear disk space utilization [#6328](https://github.com/hyperledger/besu/pull/6328)
- Add a cache on senders by transaction hash [#6375](https://github.com/hyperledger/besu/pull/6375)

### Bug fixes
- Hotfix for selfdestruct preimages on bonsai [#6359]((https://github.com/hyperledger/besu/pull/6359)
- Fix trielog shipping issue during self destruct [#6340]((https://github.com/hyperledger/besu/pull/6340)
- mitigation for trielog failure [#6315]((https://github.com/hyperledger/besu/pull/6315)

### Download Links
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/24.1.0/besu-24.1.0.zip / sha256 d36c8aeef70f0a516d4c26d3bc696c3e2a671e515c9e6e9475a31fe759e39f64
https://hyperledger.jfrog.io/artifactory/besu-binaries/besu/24.1.0/besu-24.1.0.tar.gz / sha256 602b04c0729a7b17361d1f0b39f4ce6a2ebe47932165add666560fe594d9ca99

[CHANGELOG_ARCHIVE](docs/CHANGELOG_ARCHIVE.md)