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

import java.util.ArrayList;
import java.util.Collection;

public enum RpcMethod {
  ADMIN_ADD_PEER("admin_addPeer"),
  ADMIN_NODE_INFO("admin_nodeInfo"),
  ADMIN_PEERS("admin_peers"),
  ADMIN_REMOVE_PEER("admin_removePeer"),
  ADMIN_CHANGE_LOG_LEVEL("admin_changeLogLevel"),
  ADMIN_GENERATE_LOG_BLOOM_CACHE("admin_generateLogBloomCache"),
  ADMIN_LOGS_REPAIR_CACHE("admin_logsRepairCache"),
  ADMIN_LOGS_REMOVE_CACHE("admin_logsRemoveCache"),
  CLIQUE_DISCARD("clique_discard"),
  CLIQUE_GET_SIGNERS("clique_getSigners"),
  CLIQUE_GET_SIGNERS_AT_HASH("clique_getSignersAtHash"),
  CLIQUE_GET_PROPOSALS("clique_proposals"),
  CLIQUE_PROPOSE("clique_propose"),
  CLIQUE_GET_SIGNER_METRICS("clique_getSignerMetrics"),
  DEBUG_ACCOUNT_AT("debug_accountAt"),
  DEBUG_METRICS("debug_metrics"),
  DEBUG_STORAGE_RANGE_AT("debug_storageRangeAt"),
  DEBUG_TRACE_BLOCK("debug_traceBlock"),
  DEBUG_TRACE_BLOCK_BY_HASH("debug_traceBlockByHash"),
  DEBUG_TRACE_BLOCK_BY_NUMBER("debug_traceBlockByNumber"),
  DEBUG_STANDARD_TRACE_BLOCK_TO_FILE("debug_standardTraceBlockToFile"),
  DEBUG_STANDARD_TRACE_BAD_BLOCK_TO_FILE("debug_standardTraceBadBlockToFile"),
  DEBUG_TRACE_TRANSACTION("debug_traceTransaction"),
  DEBUG_BATCH_RAW_TRANSACTION("debug_batchSendRawTransaction"),
  DEBUG_GET_BAD_BLOCKS("debug_getBadBlocks"),

  ENGINE_GET_PAYLOAD("engine_getPayloadV1"),
  ENGINE_EXECUTE_PAYLOAD("engine_executePayloadV1"),
  ENGINE_NEW_PAYLOAD("engine_newPayloadV1"),
  ENGINE_FORKCHOICE_UPDATED("engine_forkchoiceUpdatedV1"),
  ENGINE_EXCHANGE_TRANSITION_CONFIGURATION("engine_exchangeTransitionConfigurationV1"),

  GOQUORUM_ETH_GET_QUORUM_PAYLOAD("eth_getQuorumPayload"),
  GOQUORUM_STORE_RAW("goquorum_storeRaw"),
  PRIV_CALL("priv_call"),
  PRIV_GET_PRIVATE_TRANSACTION("priv_getPrivateTransaction"),
  PRIV_GET_TRANSACTION_COUNT("priv_getTransactionCount"),
  PRIV_GET_PRIVACY_PRECOMPILE_ADDRESS("priv_getPrivacyPrecompileAddress"),
  PRIV_GET_TRANSACTION_RECEIPT("priv_getTransactionReceipt"),
  PRIV_CREATE_PRIVACY_GROUP("priv_createPrivacyGroup"),
  PRIV_DELETE_PRIVACY_GROUP("priv_deletePrivacyGroup"),
  PRIV_FIND_PRIVACY_GROUP("priv_findPrivacyGroup"),
  PRIV_DEBUG_GET_STATE_ROOT("priv_debugGetStateRoot"),
  PRIV_DISTRIBUTE_RAW_TRANSACTION("priv_distributeRawTransaction"),
  PRIV_GET_EEA_TRANSACTION_COUNT("priv_getEeaTransactionCount"),
  PRIV_GET_CODE("priv_getCode"),
  PRIV_GET_LOGS("priv_getLogs"),
  PRIV_NEW_FILTER("priv_newFilter"),
  PRIV_UNINSTALL_FILTER("priv_uninstallFilter"),
  PRIV_GET_FILTER_CHANGES("priv_getFilterChanges"),
  PRIV_GET_FILTER_LOGS("priv_getFilterLogs"),
  PRIV_SUBSCRIBE("priv_subscribe"),
  PRIV_UNSUBSCRIBE("priv_unsubscribe"),
  PRIVX_FIND_PRIVACY_GROUP_OLD("privx_findOnchainPrivacyGroup"),
  PRIVX_FIND_PRIVACY_GROUP("privx_findFlexiblePrivacyGroup"),
  EEA_SEND_RAW_TRANSACTION("eea_sendRawTransaction"),
  ETH_ACCOUNTS("eth_accounts"),
  ETH_BLOCK_NUMBER("eth_blockNumber"),
  ETH_CALL("eth_call"),
  ETH_CHAIN_ID("eth_chainId"),
  ETH_COINBASE("eth_coinbase"),
  ETH_ESTIMATE_GAS("eth_estimateGas"),
  ETH_FEE_HISTORY("eth_feeHistory"),
  ETH_GAS_PRICE("eth_gasPrice"),
  ETH_GET_BALANCE("eth_getBalance"),
  ETH_GET_BLOCK_BY_HASH("eth_getBlockByHash"),
  ETH_GET_BLOCK_BY_NUMBER("eth_getBlockByNumber"),
  ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH("eth_getBlockTransactionCountByHash"),
  ETH_GET_BLOCK_TRANSACTION_COUNT_BY_NUMBER("eth_getBlockTransactionCountByNumber"),
  ETH_GET_CODE("eth_getCode"),
  ETH_GET_FILTER_CHANGES("eth_getFilterChanges"),
  ETH_GET_FILTER_LOGS("eth_getFilterLogs"),
  ETH_GET_LOGS("eth_getLogs"),
  ETH_GET_MINER_DATA_BY_BLOCK_HASH("eth_getMinerDataByBlockHash"),
  ETH_GET_MINER_DATA_BY_BLOCK_NUMBER("eth_getMinerDataByBlockNumber"),
  ETH_GET_PROOF("eth_getProof"),
  ETH_GET_STORAGE_AT("eth_getStorageAt"),
  ETH_GET_TRANSACTION_BY_BLOCK_HASH_AND_INDEX("eth_getTransactionByBlockHashAndIndex"),
  ETH_GET_TRANSACTION_BY_BLOCK_NUMBER_AND_INDEX("eth_getTransactionByBlockNumberAndIndex"),
  ETH_GET_TRANSACTION_BY_HASH("eth_getTransactionByHash"),
  ETH_GET_TRANSACTION_COUNT("eth_getTransactionCount"),
  ETH_GET_TRANSACTION_RECEIPT("eth_getTransactionReceipt"),
  ETH_GET_UNCLE_BY_BLOCK_HASH_AND_INDEX("eth_getUncleByBlockHashAndIndex"),
  ETH_GET_UNCLE_BY_BLOCK_NUMBER_AND_INDEX("eth_getUncleByBlockNumberAndIndex"),
  ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH("eth_getUncleCountByBlockHash"),
  ETH_GET_UNCLE_COUNT_BY_BLOCK_NUMBER("eth_getUncleCountByBlockNumber"),
  ETH_GET_WORK("eth_getWork"),
  ETH_HASHRATE("eth_hashrate"),
  ETH_MINING("eth_mining"),
  ETH_NEW_BLOCK_FILTER("eth_newBlockFilter"),
  ETH_NEW_FILTER("eth_newFilter"),
  ETH_NEW_PENDING_TRANSACTION_FILTER("eth_newPendingTransactionFilter"),
  ETH_PROTOCOL_VERSION("eth_protocolVersion"),
  ETH_SEND_RAW_PRIVATE_TRANSACTION("eth_sendRawPrivateTransaction"),
  ETH_SEND_RAW_TRANSACTION("eth_sendRawTransaction"),
  ETH_SEND_TRANSACTION("eth_sendTransaction"),
  ETH_SUBMIT_HASHRATE("eth_submitHashrate"),
  ETH_SUBMIT_WORK("eth_submitWork"),
  ETH_SUBSCRIBE("eth_subscribe"),
  ETH_SYNCING("eth_syncing"),
  ETH_UNINSTALL_FILTER("eth_uninstallFilter"),
  ETH_UNSUBSCRIBE("eth_unsubscribe"),
  IBFT_DISCARD_VALIDATOR_VOTE("ibft_discardValidatorVote"),
  IBFT_GET_PENDING_VOTES("ibft_getPendingVotes"),
  IBFT_GET_VALIDATORS_BY_BLOCK_HASH("ibft_getValidatorsByBlockHash"),
  IBFT_GET_VALIDATORS_BY_BLOCK_NUMBER("ibft_getValidatorsByBlockNumber"),
  IBFT_PROPOSE_VALIDATOR_VOTE("ibft_proposeValidatorVote"),
  IBFT_GET_SIGNER_METRICS("ibft_getSignerMetrics"),
  QBFT_DISCARD_VALIDATOR_VOTE("qbft_discardValidatorVote"),
  QBFT_GET_PENDING_VOTES("qbft_getPendingVotes"),
  QBFT_GET_VALIDATORS_BY_BLOCK_HASH("qbft_getValidatorsByBlockHash"),
  QBFT_GET_VALIDATORS_BY_BLOCK_NUMBER("qbft_getValidatorsByBlockNumber"),
  QBFT_PROPOSE_VALIDATOR_VOTE("qbft_proposeValidatorVote"),
  QBFT_GET_SIGNER_METRICS("qbft_getSignerMetrics"),
  MINER_CHANGE_TARGET_GAS_LIMIT("miner_changeTargetGasLimit"),
  MINER_SET_COINBASE("miner_setCoinbase"),
  MINER_SET_ETHERBASE("miner_setEtherbase"),
  MINER_START("miner_start"),
  MINER_STOP("miner_stop"),
  NET_ENODE("net_enode"),
  NET_LISTENING("net_listening"),
  NET_PEER_COUNT("net_peerCount"),
  NET_SERVICES("net_services"),
  NET_VERSION("net_version"),
  PERM_ADD_ACCOUNTS_TO_WHITELIST("perm_addAccountsToWhitelist"),
  PERM_ADD_ACCOUNTS_TO_ALLOWLIST("perm_addAccountsToAllowlist"),
  PERM_ADD_NODES_TO_WHITELIST("perm_addNodesToWhitelist"),
  PERM_ADD_NODES_TO_ALLOWLIST("perm_addNodesToAllowlist"),
  PERM_GET_ACCOUNTS_WHITELIST("perm_getAccountsWhitelist"),
  PERM_GET_ACCOUNTS_ALLOWLIST("perm_getAccountsAllowlist"),
  PERM_GET_NODES_WHITELIST("perm_getNodesWhitelist"),
  PERM_GET_NODES_ALLOWLIST("perm_getNodesAllowlist"),
  PERM_RELOAD_PERMISSIONS_FROM_FILE("perm_reloadPermissionsFromFile"),
  PERM_REMOVE_ACCOUNTS_FROM_WHITELIST("perm_removeAccountsFromWhitelist"),
  PERM_REMOVE_ACCOUNTS_FROM_ALLOWLIST("perm_removeAccountsFromAllowlist"),
  PERM_REMOVE_NODES_FROM_WHITELIST("perm_removeNodesFromWhitelist"),
  PERM_REMOVE_NODES_FROM_ALLOWLIST("perm_removeNodesFromAllowlist"),
  RPC_MODULES("rpc_modules"),
  TRACE_BLOCK("trace_block"),
  TRACE_CALL("trace_call"),
  TRACE_CALL_MANY("trace_callMany"),
  TRACE_GET("trace_get"),
  TRACE_FILTER("trace_filter"),
  TRACE_RAW_TRANSACTION("trace_rawTransaction"),
  TRACE_REPLAY_BLOCK_TRANSACTIONS("trace_replayBlockTransactions"),
  TRACE_TRANSACTION("trace_transaction"),
  TX_POOL_BESU_STATISTICS("txpool_besuStatistics"),
  TX_POOL_BESU_TRANSACTIONS("txpool_besuTransactions"),
  TX_POOL_BESU_PENDING_TRANSACTIONS("txpool_besuPendingTransactions"),
  WEB3_CLIENT_VERSION("web3_clientVersion"),
  WEB3_SHA3("web3_sha3"),
  PLUGINS_RELOAD_CONFIG("plugins_reloadPluginConfig");

  private final String methodName;

  private static final Collection<String> allMethodNames;

  public String getMethodName() {
    return methodName;
  }

  static {
    allMethodNames = new ArrayList<>();
    for (RpcMethod m : RpcMethod.values()) {
      allMethodNames.add(m.getMethodName());
    }
  }

  RpcMethod(final String methodName) {
    this.methodName = methodName;
  }

  public static boolean rpcMethodExists(final String rpcMethodName) {
    return allMethodNames.contains(rpcMethodName);
  }
}
