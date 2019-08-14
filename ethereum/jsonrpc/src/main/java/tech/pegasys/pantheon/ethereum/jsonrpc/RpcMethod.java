/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc;

import java.util.ArrayList;
import java.util.Collection;

public enum RpcMethod {
  ADMIN_ADD_PEER("admin_addPeer"),
  ADMIN_NODE_INFO("admin_nodeInfo"),
  ADMIN_PEERS("admin_peers"),
  ADMIN_REMOVE_PEER("admin_removePeer"),
  ADMIN_CHANGE_LOG_LEVEL("admin_changeLogLevel"),
  CLIQUE_DISCARD("clique_discard"),
  CLIQUE_GET_SIGNERS("clique_getSigners"),
  CLIQUE_GET_SIGNERS_AT_HASH("clique_getSignersAtHash"),
  CLIQUE_GET_PROPOSALS("clique_proposals"),
  CLIQUE_PROPOSE("clique_propose"),
  CLIQUE_GET_SIGNER_METRICS("clique_getSignerMetrics"),
  DEBUG_METRICS("debug_metrics"),
  DEBUG_STORAGE_RANGE_AT("debug_storageRangeAt"),
  DEBUG_TRACE_BLOCK("debug_traceBlock"),
  DEBUG_TRACE_BLOCK_BY_HASH("debug_traceBlockByHash"),
  DEBUG_TRACE_BLOCK_BY_NUMBER("debug_traceBlockByNumber"),
  DEBUG_TRACE_TRANSACTION("debug_traceTransaction"),
  PRIV_GET_PRIVATE_TRANSACTION("priv_getPrivateTransaction"),
  PRIV_GET_TRANSACTION_COUNT("priv_getTransactionCount"),
  PRIV_GET_PRIVACY_PRECOMPILE_ADDRESS("priv_getPrivacyPrecompileAddress"),
  EEA_GET_TRANSACTION_RECEIPT("eea_getTransactionReceipt"),
  PRIV_CREATE_PRIVACY_GROUP("priv_createPrivacyGroup"),
  PRIV_DELETE_PRIVACY_GROUP("priv_deletePrivacyGroup"),
  PRIV_FIND_PRIVACY_GROUP("priv_findPrivacyGroup"),
  EEA_SEND_RAW_TRANSACTION("eea_sendRawTransaction"),
  ETH_ACCOUNTS("eth_accounts"),
  ETH_BLOCK_NUMBER("eth_blockNumber"),
  ETH_CALL("eth_call"),
  ETH_CHAIN_ID("eth_chainId"),
  ETH_COINBASE("eth_coinbase"),
  ETH_ESTIMATE_GAS("eth_estimateGas"),
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
  ETH_SEND_RAW_TRANSACTION("eth_sendRawTransaction"),
  ETH_SEND_TRANSACTION("eth_sendTransaction"),
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
  PERM_ADD_NODES_TO_WHITELIST("perm_addNodesToWhitelist"),
  PERM_GET_ACCOUNTS_WHITELIST("perm_getAccountsWhitelist"),
  PERM_GET_NODES_WHITELIST("perm_getNodesWhitelist"),
  PERM_RELOAD_PERMISSIONS_FROM_FILE("perm_reloadPermissionsFromFile"),
  PERM_REMOVE_ACCOUNTS_FROM_WHITELIST("perm_removeAccountsFromWhitelist"),
  PERM_REMOVE_NODES_FROM_WHITELIST("perm_removeNodesFromWhitelist"),
  RPC_MODULES("rpc_modules"),
  TX_POOL_PANTHEON_STATISTICS("txpool_pantheonStatistics"),
  TX_POOL_PANTHEON_TRANSACTIONS("txpool_pantheonTransactions"),
  WEB3_CLIENT_VERSION("web3_clientVersion"),
  WEB3_SHA3("web3_sha3");

  private final String methodName;

  private static Collection<String> allMethodNames;

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
