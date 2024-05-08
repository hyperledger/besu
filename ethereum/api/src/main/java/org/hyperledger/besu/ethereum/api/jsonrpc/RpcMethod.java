/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc;

import java.util.Collection;
import java.util.HashSet;

/** The enum Rpc method. */
public enum RpcMethod {
  /** Admin add peer rpc method. */
  ADMIN_ADD_PEER("admin_addPeer"),
  /** Admin node info rpc method. */
  ADMIN_NODE_INFO("admin_nodeInfo"),
  /** Admin peers rpc method. */
  ADMIN_PEERS("admin_peers"),
  /** Admin remove peer rpc method. */
  ADMIN_REMOVE_PEER("admin_removePeer"),
  /** Admin change log level rpc method. */
  ADMIN_CHANGE_LOG_LEVEL("admin_changeLogLevel"),
  /** Admin generate log bloom cache rpc method. */
  ADMIN_GENERATE_LOG_BLOOM_CACHE("admin_generateLogBloomCache"),
  /** Admin logs repair cache rpc method. */
  ADMIN_LOGS_REPAIR_CACHE("admin_logsRepairCache"),
  /** Admin logs remove cache rpc method. */
  ADMIN_LOGS_REMOVE_CACHE("admin_logsRemoveCache"),
  /** Clique discard rpc method. */
  CLIQUE_DISCARD("clique_discard"),
  /** Clique get signers rpc method. */
  CLIQUE_GET_SIGNERS("clique_getSigners"),
  /** Clique get signers at hash rpc method. */
  CLIQUE_GET_SIGNERS_AT_HASH("clique_getSignersAtHash"),
  /** Clique get proposals rpc method. */
  CLIQUE_GET_PROPOSALS("clique_proposals"),
  /** Clique propose rpc method. */
  CLIQUE_PROPOSE("clique_propose"),
  /** Clique get signer metrics rpc method. */
  CLIQUE_GET_SIGNER_METRICS("clique_getSignerMetrics"),
  /** Debug account at rpc method. */
  DEBUG_ACCOUNT_AT("debug_accountAt"),
  /** Debug metrics rpc method. */
  DEBUG_METRICS("debug_metrics"),
  /** Debug resync worldstate rpc method. */
  DEBUG_RESYNC_WORLDSTATE("debug_resyncWorldState"),
  /** Debug set head rpc method. */
  DEBUG_SET_HEAD("debug_setHead"),
  /** Debug replay block rpc method. */
  DEBUG_REPLAY_BLOCK("debug_replayBlock"),
  /** Debug storage range at rpc method. */
  DEBUG_STORAGE_RANGE_AT("debug_storageRangeAt"),
  /** Debug trace block rpc method. */
  DEBUG_TRACE_BLOCK("debug_traceBlock"),
  /** Debug trace block by hash rpc method. */
  DEBUG_TRACE_BLOCK_BY_HASH("debug_traceBlockByHash"),
  /** Debug trace block by number rpc method. */
  DEBUG_TRACE_BLOCK_BY_NUMBER("debug_traceBlockByNumber"),
  /** Debug standard trace block to file rpc method. */
  DEBUG_STANDARD_TRACE_BLOCK_TO_FILE("debug_standardTraceBlockToFile"),
  /** Debug standard trace bad block to file rpc method. */
  DEBUG_STANDARD_TRACE_BAD_BLOCK_TO_FILE("debug_standardTraceBadBlockToFile"),
  /** Debug trace transaction rpc method. */
  DEBUG_TRACE_TRANSACTION("debug_traceTransaction"),
  /** Debug trace call rpc method. */
  DEBUG_TRACE_CALL("debug_traceCall"),
  /** Debug batch raw transaction rpc method. */
  DEBUG_BATCH_RAW_TRANSACTION("debug_batchSendRawTransaction"),
  /** Debug get bad blocks rpc method. */
  DEBUG_GET_BAD_BLOCKS("debug_getBadBlocks"),
  /** Debug get raw header rpc method. */
  DEBUG_GET_RAW_HEADER("debug_getRawHeader"),
  /** Debug get raw block rpc method. */
  DEBUG_GET_RAW_BLOCK("debug_getRawBlock"),
  /** Debug get raw receipts rpc method. */
  DEBUG_GET_RAW_RECEIPTS("debug_getRawReceipts"),
  /** Debug get raw transaction rpc method. */
  DEBUG_GET_RAW_TRANSACTION("debug_getRawTransaction"),
  /** Engine get payload v 1 rpc method. */
  ENGINE_GET_PAYLOAD_V1("engine_getPayloadV1"),
  /** Engine get payload v 2 rpc method. */
  ENGINE_GET_PAYLOAD_V2("engine_getPayloadV2"),
  /** Engine get payload v 3 rpc method. */
  ENGINE_GET_PAYLOAD_V3("engine_getPayloadV3"),
  /** Engine get payload v 4 rpc method. */
  ENGINE_GET_PAYLOAD_V4("engine_getPayloadV4"),
  /** Engine new payload v 1 rpc method. */
  ENGINE_NEW_PAYLOAD_V1("engine_newPayloadV1"),
  /** Engine new payload v 2 rpc method. */
  ENGINE_NEW_PAYLOAD_V2("engine_newPayloadV2"),
  /** Engine new payload v 3 rpc method. */
  ENGINE_NEW_PAYLOAD_V3("engine_newPayloadV3"),
  /** Engine new payload v 4 rpc method. */
  ENGINE_NEW_PAYLOAD_V4("engine_newPayloadV4"),
  /** Engine forkchoice updated v 1 rpc method. */
  ENGINE_FORKCHOICE_UPDATED_V1("engine_forkchoiceUpdatedV1"),
  /** Engine forkchoice updated v 2 rpc method. */
  ENGINE_FORKCHOICE_UPDATED_V2("engine_forkchoiceUpdatedV2"),
  /** Engine forkchoice updated v 3 rpc method. */
  ENGINE_FORKCHOICE_UPDATED_V3("engine_forkchoiceUpdatedV3"),
  /** Engine exchange transition configuration rpc method. */
  ENGINE_EXCHANGE_TRANSITION_CONFIGURATION("engine_exchangeTransitionConfigurationV1"),
  /** Engine get payload bodies by hash v 1 rpc method. */
  ENGINE_GET_PAYLOAD_BODIES_BY_HASH_V1("engine_getPayloadBodiesByHashV1"),
  /** Engine get payload bodies by range v 1 rpc method. */
  ENGINE_GET_PAYLOAD_BODIES_BY_RANGE_V1("engine_getPayloadBodiesByRangeV1"),
  /** Engine exchange capabilities rpc method. */
  ENGINE_EXCHANGE_CAPABILITIES("engine_exchangeCapabilities"),
  /** Engine prepare payload debug rpc method. */
  ENGINE_PREPARE_PAYLOAD_DEBUG("engine_preparePayload_debug"),
  /** Priv call rpc method. */
  PRIV_CALL("priv_call"),
  /** Priv get private transaction rpc method. */
  PRIV_GET_PRIVATE_TRANSACTION("priv_getPrivateTransaction"),
  /** Priv get transaction count rpc method. */
  PRIV_GET_TRANSACTION_COUNT("priv_getTransactionCount"),
  /** Priv get privacy precompile address rpc method. */
  PRIV_GET_PRIVACY_PRECOMPILE_ADDRESS("priv_getPrivacyPrecompileAddress"),
  /** Priv get transaction receipt rpc method. */
  PRIV_GET_TRANSACTION_RECEIPT("priv_getTransactionReceipt"),
  /** Priv create privacy group rpc method. */
  PRIV_CREATE_PRIVACY_GROUP("priv_createPrivacyGroup"),
  /** Priv delete privacy group rpc method. */
  PRIV_DELETE_PRIVACY_GROUP("priv_deletePrivacyGroup"),
  /** Priv find privacy group rpc method. */
  PRIV_FIND_PRIVACY_GROUP("priv_findPrivacyGroup"),
  /** Priv debug get state root rpc method. */
  PRIV_DEBUG_GET_STATE_ROOT("priv_debugGetStateRoot"),
  /** Priv distribute raw transaction rpc method. */
  PRIV_DISTRIBUTE_RAW_TRANSACTION("priv_distributeRawTransaction"),
  /** Priv get eea transaction count rpc method. */
  PRIV_GET_EEA_TRANSACTION_COUNT("priv_getEeaTransactionCount"),
  /** Priv get code rpc method. */
  PRIV_GET_CODE("priv_getCode"),
  /** Priv get logs rpc method. */
  PRIV_GET_LOGS("priv_getLogs"),
  /** Priv new filter rpc method. */
  PRIV_NEW_FILTER("priv_newFilter"),
  /** Priv uninstall filter rpc method. */
  PRIV_UNINSTALL_FILTER("priv_uninstallFilter"),
  /** Priv get filter changes rpc method. */
  PRIV_GET_FILTER_CHANGES("priv_getFilterChanges"),
  /** Priv get filter logs rpc method. */
  PRIV_GET_FILTER_LOGS("priv_getFilterLogs"),
  /** Priv subscribe rpc method. */
  PRIV_SUBSCRIBE("priv_subscribe"),
  /** Priv unsubscribe rpc method. */
  PRIV_UNSUBSCRIBE("priv_unsubscribe"),
  /** Privx find privacy group old rpc method. */
  PRIVX_FIND_PRIVACY_GROUP_OLD("privx_findOnchainPrivacyGroup"),
  /** Privx find privacy group rpc method. */
  PRIVX_FIND_PRIVACY_GROUP("privx_findFlexiblePrivacyGroup"),
  /** Eea send raw transaction rpc method. */
  EEA_SEND_RAW_TRANSACTION("eea_sendRawTransaction"),
  /** Eth accounts rpc method. */
  ETH_ACCOUNTS("eth_accounts"),
  /** Eth block number rpc method. */
  ETH_BLOCK_NUMBER("eth_blockNumber"),
  /** Eth call rpc method. */
  ETH_CALL("eth_call"),
  /** Eth chain id rpc method. */
  ETH_CHAIN_ID("eth_chainId"),
  /** Eth coinbase rpc method. */
  ETH_COINBASE("eth_coinbase"),
  /** Eth estimate gas rpc method. */
  ETH_ESTIMATE_GAS("eth_estimateGas"),
  /** Eth create access list rpc method. */
  ETH_CREATE_ACCESS_LIST("eth_createAccessList"),
  /** Eth fee history rpc method. */
  ETH_FEE_HISTORY("eth_feeHistory"),
  /** Eth gas price rpc method. */
  ETH_GAS_PRICE("eth_gasPrice"),
  /** Eth blob base fee rpc method. */
  ETH_BLOB_BASE_FEE("eth_blobBaseFee"),
  /** Eth get balance rpc method. */
  ETH_GET_BALANCE("eth_getBalance"),
  /** Eth get block by hash rpc method. */
  ETH_GET_BLOCK_BY_HASH("eth_getBlockByHash"),
  /** Eth get block by number rpc method. */
  ETH_GET_BLOCK_BY_NUMBER("eth_getBlockByNumber"),
  /** Eth get block receipts rpc method. */
  ETH_GET_BLOCK_RECEIPTS("eth_getBlockReceipts"),
  /** Eth get block transaction count by hash rpc method. */
  ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH("eth_getBlockTransactionCountByHash"),
  /** Eth get block transaction count by number rpc method. */
  ETH_GET_BLOCK_TRANSACTION_COUNT_BY_NUMBER("eth_getBlockTransactionCountByNumber"),
  /** Eth get code rpc method. */
  ETH_GET_CODE("eth_getCode"),
  /** Eth get filter changes rpc method. */
  ETH_GET_FILTER_CHANGES("eth_getFilterChanges"),
  /** Eth get filter logs rpc method. */
  ETH_GET_FILTER_LOGS("eth_getFilterLogs"),
  /** Eth get logs rpc method. */
  ETH_GET_LOGS("eth_getLogs"),
  /** Eth get miner data by block hash rpc method. */
  ETH_GET_MINER_DATA_BY_BLOCK_HASH("eth_getMinerDataByBlockHash"),
  /** Eth get miner data by block number rpc method. */
  ETH_GET_MINER_DATA_BY_BLOCK_NUMBER("eth_getMinerDataByBlockNumber"),
  /** Eth get proof rpc method. */
  ETH_GET_PROOF("eth_getProof"),
  /** Eth get storage at rpc method. */
  ETH_GET_STORAGE_AT("eth_getStorageAt"),
  /** Eth get transaction by block hash and index rpc method. */
  ETH_GET_TRANSACTION_BY_BLOCK_HASH_AND_INDEX("eth_getTransactionByBlockHashAndIndex"),
  /** Eth get transaction by block number and index rpc method. */
  ETH_GET_TRANSACTION_BY_BLOCK_NUMBER_AND_INDEX("eth_getTransactionByBlockNumberAndIndex"),
  /** Eth get transaction by hash rpc method. */
  ETH_GET_TRANSACTION_BY_HASH("eth_getTransactionByHash"),
  /** Eth get transaction count rpc method. */
  ETH_GET_TRANSACTION_COUNT("eth_getTransactionCount"),
  /** Eth get transaction receipt rpc method. */
  ETH_GET_TRANSACTION_RECEIPT("eth_getTransactionReceipt"),
  /** Eth get uncle by block hash and index rpc method. */
  ETH_GET_UNCLE_BY_BLOCK_HASH_AND_INDEX("eth_getUncleByBlockHashAndIndex"),
  /** Eth get uncle by block number and index rpc method. */
  ETH_GET_UNCLE_BY_BLOCK_NUMBER_AND_INDEX("eth_getUncleByBlockNumberAndIndex"),
  /** Eth get uncle count by block hash rpc method. */
  ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH("eth_getUncleCountByBlockHash"),
  /** Eth get uncle count by block number rpc method. */
  ETH_GET_UNCLE_COUNT_BY_BLOCK_NUMBER("eth_getUncleCountByBlockNumber"),
  /** Eth get work rpc method. */
  ETH_GET_WORK("eth_getWork"),
  /** Eth hashrate rpc method. */
  ETH_HASHRATE("eth_hashrate"),
  /** Eth mining rpc method. */
  ETH_MINING("eth_mining"),
  /** Eth new block filter rpc method. */
  ETH_NEW_BLOCK_FILTER("eth_newBlockFilter"),
  /** Eth new filter rpc method. */
  ETH_NEW_FILTER("eth_newFilter"),
  /** Eth new pending transaction filter rpc method. */
  ETH_NEW_PENDING_TRANSACTION_FILTER("eth_newPendingTransactionFilter"),
  /** Eth protocol version rpc method. */
  ETH_PROTOCOL_VERSION("eth_protocolVersion"),
  /** Eth send raw private transaction rpc method. */
  ETH_SEND_RAW_PRIVATE_TRANSACTION("eth_sendRawPrivateTransaction"),
  /** Eth send raw transaction rpc method. */
  ETH_SEND_RAW_TRANSACTION("eth_sendRawTransaction"),
  /** Eth send transaction rpc method. */
  ETH_SEND_TRANSACTION("eth_sendTransaction"),
  /** Eth submit hashrate rpc method. */
  ETH_SUBMIT_HASHRATE("eth_submitHashrate"),
  /** Eth submit work rpc method. */
  ETH_SUBMIT_WORK("eth_submitWork"),
  /** Eth subscribe rpc method. */
  ETH_SUBSCRIBE("eth_subscribe"),
  /** Eth syncing rpc method. */
  ETH_SYNCING("eth_syncing"),
  /** Eth uninstall filter rpc method. */
  ETH_UNINSTALL_FILTER("eth_uninstallFilter"),
  /** Eth unsubscribe rpc method. */
  ETH_UNSUBSCRIBE("eth_unsubscribe"),
  /** Ibft discard validator vote rpc method. */
  IBFT_DISCARD_VALIDATOR_VOTE("ibft_discardValidatorVote"),
  /** Ibft get pending votes rpc method. */
  IBFT_GET_PENDING_VOTES("ibft_getPendingVotes"),
  /** Ibft get validators by block hash rpc method. */
  IBFT_GET_VALIDATORS_BY_BLOCK_HASH("ibft_getValidatorsByBlockHash"),
  /** Ibft get validators by block number rpc method. */
  IBFT_GET_VALIDATORS_BY_BLOCK_NUMBER("ibft_getValidatorsByBlockNumber"),
  /** Ibft propose validator vote rpc method. */
  IBFT_PROPOSE_VALIDATOR_VOTE("ibft_proposeValidatorVote"),
  /** Ibft get signer metrics rpc method. */
  IBFT_GET_SIGNER_METRICS("ibft_getSignerMetrics"),
  /** Qbft discard validator vote rpc method. */
  QBFT_DISCARD_VALIDATOR_VOTE("qbft_discardValidatorVote"),
  /** Qbft get pending votes rpc method. */
  QBFT_GET_PENDING_VOTES("qbft_getPendingVotes"),
  /** Qbft get validators by block hash rpc method. */
  QBFT_GET_VALIDATORS_BY_BLOCK_HASH("qbft_getValidatorsByBlockHash"),
  /** Qbft get validators by block number rpc method. */
  QBFT_GET_VALIDATORS_BY_BLOCK_NUMBER("qbft_getValidatorsByBlockNumber"),
  /** Qbft propose validator vote rpc method. */
  QBFT_PROPOSE_VALIDATOR_VOTE("qbft_proposeValidatorVote"),
  /** Qbft get signer metrics rpc method. */
  QBFT_GET_SIGNER_METRICS("qbft_getSignerMetrics"),
  /** Miner change target gas limit rpc method. */
  MINER_CHANGE_TARGET_GAS_LIMIT("miner_changeTargetGasLimit"),
  /** Miner set coinbase rpc method. */
  MINER_SET_COINBASE("miner_setCoinbase"),
  /** Miner set etherbase rpc method. */
  MINER_SET_ETHERBASE("miner_setEtherbase"),
  /** Miner start rpc method. */
  MINER_START("miner_start"),
  /** Miner stop rpc method. */
  MINER_STOP("miner_stop"),
  /** Miner get min priority fee rpc method. */
  MINER_GET_MIN_PRIORITY_FEE("miner_getMinPriorityFee"),
  /** Miner set min priority fee rpc method. */
  MINER_SET_MIN_PRIORITY_FEE("miner_setMinPriorityFee"),
  /** Miner get min gas price rpc method. */
  MINER_GET_MIN_GAS_PRICE("miner_getMinGasPrice"),
  /** Miner set min gas price rpc method. */
  MINER_SET_MIN_GAS_PRICE("miner_setMinGasPrice"),
  /** Net enode rpc method. */
  NET_ENODE("net_enode"),
  /** Net listening rpc method. */
  NET_LISTENING("net_listening"),
  /** Net peer count rpc method. */
  NET_PEER_COUNT("net_peerCount"),
  /** Net services rpc method. */
  NET_SERVICES("net_services"),
  /** Net version rpc method. */
  NET_VERSION("net_version"),
  /** Perm add accounts to whitelist rpc method. */
  PERM_ADD_ACCOUNTS_TO_WHITELIST("perm_addAccountsToWhitelist"),
  /** Perm add accounts to allowlist rpc method. */
  PERM_ADD_ACCOUNTS_TO_ALLOWLIST("perm_addAccountsToAllowlist"),
  /** Perm add nodes to whitelist rpc method. */
  PERM_ADD_NODES_TO_WHITELIST("perm_addNodesToWhitelist"),
  /** Perm add nodes to allowlist rpc method. */
  PERM_ADD_NODES_TO_ALLOWLIST("perm_addNodesToAllowlist"),
  /** Perm get accounts whitelist rpc method. */
  PERM_GET_ACCOUNTS_WHITELIST("perm_getAccountsWhitelist"),
  /** Perm get accounts allowlist rpc method. */
  PERM_GET_ACCOUNTS_ALLOWLIST("perm_getAccountsAllowlist"),
  /** Perm get nodes whitelist rpc method. */
  PERM_GET_NODES_WHITELIST("perm_getNodesWhitelist"),
  /** Perm get nodes allowlist rpc method. */
  PERM_GET_NODES_ALLOWLIST("perm_getNodesAllowlist"),
  /** Perm reload permissions from file rpc method. */
  PERM_RELOAD_PERMISSIONS_FROM_FILE("perm_reloadPermissionsFromFile"),
  /** Perm remove accounts from whitelist rpc method. */
  PERM_REMOVE_ACCOUNTS_FROM_WHITELIST("perm_removeAccountsFromWhitelist"),
  /** Perm remove accounts from allowlist rpc method. */
  PERM_REMOVE_ACCOUNTS_FROM_ALLOWLIST("perm_removeAccountsFromAllowlist"),
  /** Perm remove nodes from whitelist rpc method. */
  PERM_REMOVE_NODES_FROM_WHITELIST("perm_removeNodesFromWhitelist"),
  /** Perm remove nodes from allowlist rpc method. */
  PERM_REMOVE_NODES_FROM_ALLOWLIST("perm_removeNodesFromAllowlist"),
  /** Rpc modules rpc method. */
  RPC_MODULES("rpc_modules"),
  /** Trace block rpc method. */
  TRACE_BLOCK("trace_block"),
  /** Trace call rpc method. */
  TRACE_CALL("trace_call"),
  /** Trace call many rpc method. */
  TRACE_CALL_MANY("trace_callMany"),
  /** Trace get rpc method. */
  TRACE_GET("trace_get"),
  /** Trace filter rpc method. */
  TRACE_FILTER("trace_filter"),
  /** Trace raw transaction rpc method. */
  TRACE_RAW_TRANSACTION("trace_rawTransaction"),
  /** Trace replay block transactions rpc method. */
  TRACE_REPLAY_BLOCK_TRANSACTIONS("trace_replayBlockTransactions"),
  /** Trace transaction rpc method. */
  TRACE_TRANSACTION("trace_transaction"),
  /** Tx pool besu statistics rpc method. */
  TX_POOL_BESU_STATISTICS("txpool_besuStatistics"),
  /** Tx pool besu transactions rpc method. */
  TX_POOL_BESU_TRANSACTIONS("txpool_besuTransactions"),
  /** Tx pool besu pending transactions rpc method. */
  TX_POOL_BESU_PENDING_TRANSACTIONS("txpool_besuPendingTransactions"),
  /** Web 3 client version rpc method. */
  WEB3_CLIENT_VERSION("web3_clientVersion"),
  /** Web 3 sha 3 rpc method. */
  WEB3_SHA3("web3_sha3"),
  /** Plugins reload config rpc method. */
  PLUGINS_RELOAD_CONFIG("plugins_reloadPluginConfig");

  private final String methodName;

  private static final Collection<String> allMethodNames;

  /**
   * Gets method name.
   *
   * @return the method name
   */
  public String getMethodName() {
    return methodName;
  }

  static {
    allMethodNames = new HashSet<>();
    for (RpcMethod m : RpcMethod.values()) {
      allMethodNames.add(m.getMethodName());
    }
  }

  RpcMethod(final String methodName) {
    this.methodName = methodName;
  }

  /**
   * Rpc method exists boolean.
   *
   * @param rpcMethodName the rpc method name
   * @return the boolean
   */
  public static boolean rpcMethodExists(final String rpcMethodName) {
    return allMethodNames.contains(rpcMethodName);
  }
}
