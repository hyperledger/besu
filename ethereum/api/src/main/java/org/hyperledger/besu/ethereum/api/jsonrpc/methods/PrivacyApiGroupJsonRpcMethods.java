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

import org.hyperledger.besu.ethereum.api.jsonrpc.LatestNonceProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.ChainHeadPrivateNonceProvider;
import org.hyperledger.besu.ethereum.privacy.PrivateNonceProvider;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;
import org.hyperledger.besu.ethereum.privacy.markertransaction.FixedKeySigningPrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.markertransaction.PrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.markertransaction.RandomSigningPrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.privatetransaction.FixedKeySigningGroupCreationTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.privatetransaction.GroupCreationTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.privatetransaction.RandomSigningGroupCreationTransactionFactory;

import java.util.Map;

public abstract class PrivacyApiGroupJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final BlockchainQueries blockchainQueries;
  private final ProtocolSchedule<?> protocolSchedule;
  private final TransactionPool transactionPool;
  private final PrivacyParameters privacyParameters;

  public PrivacyApiGroupJsonRpcMethods(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule<?> protocolSchedule,
      final TransactionPool transactionPool,
      final PrivacyParameters privacyParameters) {
    this.blockchainQueries = blockchainQueries;
    this.protocolSchedule = protocolSchedule;
    this.transactionPool = transactionPool;
    this.privacyParameters = privacyParameters;
  }

  public BlockchainQueries getBlockchainQueries() {
    return blockchainQueries;
  }

  public ProtocolSchedule<?> getProtocolSchedule() {
    return protocolSchedule;
  }

  public TransactionPool getTransactionPool() {
    return transactionPool;
  }

  public PrivacyParameters getPrivacyParameters() {
    return privacyParameters;
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final PrivateMarkerTransactionFactory markerTransactionFactory =
        createPrivateMarkerTransactionFactory(
            privacyParameters, blockchainQueries, transactionPool.getPendingTransactions());

    final GroupCreationTransactionFactory groupCreationTransactionFactory =
        createGroupCreationTransactionFactory(
            privacyParameters, blockchainQueries, transactionPool.getPendingTransactions());

    final PrivateStateRootResolver privateStateRootResolver =
        new PrivateStateRootResolver(privacyParameters.getPrivateStateStorage());

    final PrivateNonceProvider privateNonceProvider =
        new ChainHeadPrivateNonceProvider(
            blockchainQueries.getBlockchain(),
            privateStateRootResolver,
            privacyParameters.getPrivateWorldStateArchive());

    final PrivateTransactionHandler privateTransactionHandler =
        new PrivateTransactionHandler(
            privacyParameters,
            protocolSchedule.getChainId(),
            markerTransactionFactory,
            privateNonceProvider);

    return create(
        privateTransactionHandler,
        new ChainHeadPrivateNonceProvider(
            blockchainQueries.getBlockchain(),
            privateStateRootResolver,
            privacyParameters.getPrivateWorldStateArchive()),
        groupCreationTransactionFactory);
  }

  protected abstract Map<String, JsonRpcMethod> create(
      final PrivateTransactionHandler privateTransactionHandler,
      final PrivateNonceProvider privateNonceProvider,
      final GroupCreationTransactionFactory groupCreationTransactionFactory);

  private PrivateMarkerTransactionFactory createPrivateMarkerTransactionFactory(
      final PrivacyParameters privacyParameters,
      final BlockchainQueries blockchainQueries,
      final PendingTransactions pendingTransactions) {

    final Address privateContractAddress =
        Address.privacyPrecompiled(privacyParameters.getPrivacyAddress());

    if (privacyParameters.getSigningKeyPair().isPresent()) {
      return new FixedKeySigningPrivateMarkerTransactionFactory(
          privateContractAddress,
          new LatestNonceProvider(blockchainQueries, pendingTransactions),
          privacyParameters.getSigningKeyPair().get());
    }
    return new RandomSigningPrivateMarkerTransactionFactory(privateContractAddress);
  }

  private GroupCreationTransactionFactory createGroupCreationTransactionFactory(
      final PrivacyParameters privacyParameters,
      final BlockchainQueries blockchainQueries,
      final PendingTransactions pendingTransactions) {

    if (privacyParameters.getSigningKeyPair().isPresent()) {
      return new FixedKeySigningGroupCreationTransactionFactory(
          new LatestNonceProvider(blockchainQueries, pendingTransactions),
          privacyParameters.getSigningKeyPair().get());
    }
    return new RandomSigningGroupCreationTransactionFactory();
  }
}
