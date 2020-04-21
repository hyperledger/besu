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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivGetFilterChanges;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivGetFilterLogs;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivUninstallFilter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivCall;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivCreatePrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivDeletePrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivDistributeRawTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivFindPrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetCode;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetLogs;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetPrivacyPrecompileAddress;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetPrivateTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetTransactionCount;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetTransactionReceipt;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivNewFilter;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.Map;

public class PrivJsonRpcMethods extends PrivacyApiGroupJsonRpcMethods {

  private final FilterManager filterManager;

  public PrivJsonRpcMethods(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule<?> protocolSchedule,
      final TransactionPool transactionPool,
      final PrivacyParameters privacyParameters,
      final FilterManager filterManager) {
    super(blockchainQueries, protocolSchedule, transactionPool, privacyParameters);
    this.filterManager = filterManager;
  }

  @Override
  protected RpcApi getApiGroup() {
    return RpcApis.PRIV;
  }

  @Override
  protected Map<String, JsonRpcMethod> create(
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider) {
    if (getPrivacyParameters().isOnchainPrivacyGroupsEnabled()) {
      return mapOf(
          new PrivGetTransactionReceipt(
              getBlockchainQueries(),
              getPrivacyParameters(),
              privacyController,
              enclavePublicKeyProvider),
          new PrivGetPrivacyPrecompileAddress(getPrivacyParameters()),
          new PrivGetTransactionCount(privacyController, enclavePublicKeyProvider),
          new PrivGetPrivateTransaction(
              getBlockchainQueries(),
              privacyController,
              getPrivacyParameters().getPrivateStateStorage(),
              enclavePublicKeyProvider),
          new PrivDistributeRawTransaction(
              privacyController,
              enclavePublicKeyProvider,
              getPrivacyParameters().isOnchainPrivacyGroupsEnabled()),
          new PrivCall(getBlockchainQueries(), privacyController, enclavePublicKeyProvider),
          new PrivGetCode(getBlockchainQueries(), privacyController, enclavePublicKeyProvider),
          new PrivGetLogs(
              getBlockchainQueries(),
              getPrivacyQueries(),
              privacyController,
              enclavePublicKeyProvider),
          new PrivNewFilter(filterManager, privacyController, enclavePublicKeyProvider),
          new PrivUninstallFilter(filterManager, privacyController, enclavePublicKeyProvider),
          new PrivGetFilterLogs(filterManager, privacyController, enclavePublicKeyProvider),
          new PrivGetFilterChanges(filterManager, privacyController, enclavePublicKeyProvider));
    } else {
      return mapOf(
          new PrivGetTransactionReceipt(
              getBlockchainQueries(),
              getPrivacyParameters(),
              privacyController,
              enclavePublicKeyProvider),
          new PrivCreatePrivacyGroup(privacyController, enclavePublicKeyProvider),
          new PrivDeletePrivacyGroup(privacyController, enclavePublicKeyProvider),
          new PrivFindPrivacyGroup(privacyController, enclavePublicKeyProvider),
          new PrivGetPrivacyPrecompileAddress(getPrivacyParameters()),
          new PrivGetTransactionCount(privacyController, enclavePublicKeyProvider),
          new PrivGetPrivateTransaction(
              getBlockchainQueries(),
              privacyController,
              getPrivacyParameters().getPrivateStateStorage(),
              enclavePublicKeyProvider),
          new PrivDistributeRawTransaction(
              privacyController,
              enclavePublicKeyProvider,
              getPrivacyParameters().isOnchainPrivacyGroupsEnabled()),
          new PrivCall(getBlockchainQueries(), privacyController, enclavePublicKeyProvider),
          new PrivGetCode(getBlockchainQueries(), privacyController, enclavePublicKeyProvider),
          new PrivGetLogs(
              getBlockchainQueries(),
              getPrivacyQueries(),
              privacyController,
              enclavePublicKeyProvider),
          new PrivNewFilter(filterManager, privacyController, enclavePublicKeyProvider),
          new PrivUninstallFilter(filterManager, privacyController, enclavePublicKeyProvider),
          new PrivGetFilterLogs(filterManager, privacyController, enclavePublicKeyProvider),
          new PrivGetFilterChanges(filterManager, privacyController, enclavePublicKeyProvider));
    }
  }
}
