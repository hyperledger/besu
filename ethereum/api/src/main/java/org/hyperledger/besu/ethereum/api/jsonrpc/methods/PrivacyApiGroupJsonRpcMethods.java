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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.MultiTenancyUserUtil.enclavePublicKey;

import org.hyperledger.besu.ethereum.api.jsonrpc.LatestNonceProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.DisabledPrivacyRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.MultiTenancyRpcMethodDecorator;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.ChainHeadPrivateNonceProvider;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateNonceProvider;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionSimulator;
import org.hyperledger.besu.ethereum.privacy.markertransaction.FixedKeySigningPrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.markertransaction.PrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.markertransaction.RandomSigningPrivateMarkerTransactionFactory;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public abstract class PrivacyApiGroupJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final BlockchainQueries blockchainQueries;
  private final ProtocolSchedule<?> protocolSchedule;
  private final TransactionPool transactionPool;
  private final PrivacyParameters privacyParameters;
  private final PrivateNonceProvider nonceProvider;

  public PrivacyApiGroupJsonRpcMethods(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule<?> protocolSchedule,
      final TransactionPool transactionPool,
      final PrivacyParameters privacyParameters) {
    this.blockchainQueries = blockchainQueries;
    this.protocolSchedule = protocolSchedule;
    this.transactionPool = transactionPool;
    this.privacyParameters = privacyParameters;

    final PrivateStateRootResolver privateStateRootResolver =
        new PrivateStateRootResolver(privacyParameters.getPrivateStateStorage());
    this.nonceProvider =
        new ChainHeadPrivateNonceProvider(
            blockchainQueries.getBlockchain(),
            privateStateRootResolver,
            privacyParameters.getPrivateWorldStateArchive());
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

  public PrivateNonceProvider getNonceProvider() {
    return nonceProvider;
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final PrivateMarkerTransactionFactory markerTransactionFactory =
        createPrivateMarkerTransactionFactory(
            privacyParameters, blockchainQueries, transactionPool.getPendingTransactions());

    final PrivateTransactionSimulator privateTransactionSimulator =
        new PrivateTransactionSimulator(
            getBlockchainQueries().getBlockchain(),
            getBlockchainQueries().getWorldStateArchive(),
            getProtocolSchedule(),
            getPrivacyParameters());

    final PrivacyController privacyController =
        new PrivacyController(
            getBlockchainQueries().getBlockchain(),
            privacyParameters,
            protocolSchedule.getChainId(),
            markerTransactionFactory,
            nonceProvider,
            privateTransactionSimulator);

    final EnclavePublicKeyProvider enclavePublicProvider =
        privacyParameters.isMultiTenancyEnabled()
            ? user ->
                enclavePublicKey(user)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Request does not contain an authorization token"))
            : user -> privacyParameters.getEnclavePublicKey();

    return create(
            privacyController,
            enclavePublicProvider,
            privateTransactionSimulator)
        .entrySet().stream()
        .collect(
            Collectors.toMap(
                Entry::getKey, entry -> createPrivacyMethod(privacyParameters, entry.getValue())));
  }

  protected abstract Map<String, JsonRpcMethod> create(
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider,
      final PrivateTransactionSimulator privateTransactionSimulator);

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

  private JsonRpcMethod createPrivacyMethod(
      final PrivacyParameters privacyParameters, final JsonRpcMethod rpcMethod) {
    if (privacyParameters.isEnabled() && privacyParameters.isMultiTenancyEnabled()) {
      return new MultiTenancyRpcMethodDecorator(rpcMethod);
    } else if (!privacyParameters.isEnabled()) {
      return new DisabledPrivacyRpcMethod(rpcMethod.getName());
    } else {
      return rpcMethod;
    }
  }
}
