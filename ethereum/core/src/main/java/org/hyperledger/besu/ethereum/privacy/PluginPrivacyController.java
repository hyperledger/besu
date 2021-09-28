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
package org.hyperledger.besu.ethereum.privacy;

import static org.hyperledger.besu.ethereum.privacy.PrivateTransaction.readFrom;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PluginPrivacyController implements PrivacyController {
  private final PrivateTransactionValidator privateTransactionValidator;
  private final PrivateStateRootResolver privateStateRootResolver;
  private final Blockchain blockchain;
  private final PrivateTransactionSimulator privateTransactionSimulator;
  private final PrivateNonceProvider privateNonceProvider;
  private final PrivateWorldStateReader privateWorldStateReader;
  private final PrivacyPluginService privacyPluginService;

  public PluginPrivacyController(
      final Blockchain blockchain,
      final PrivacyParameters privacyParameters,
      final Optional<BigInteger> chainId,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateWorldStateReader privateWorldStateReader) {
    this.privateTransactionValidator = new PrivateTransactionValidator(chainId);
    this.blockchain = blockchain;
    this.privateTransactionSimulator = privateTransactionSimulator;
    this.privateNonceProvider = privateNonceProvider;
    this.privateWorldStateReader = privateWorldStateReader;
    this.privateStateRootResolver = privacyParameters.getPrivateStateRootResolver();
    this.privacyPluginService = privacyParameters.getPrivacyService();
  }

  @Override
  public String createPrivateMarkerTransactionPayload(
      final PrivateTransaction privateTransaction,
      final String privacyUserId,
      final Optional<PrivacyGroup> privacyGroup) {

    return privacyPluginService
        .getPayloadProvider()
        .generateMarkerPayload(privateTransaction, privacyUserId)
        .toBase64String();
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final String privacyUserId) {
    final String privacyGroupId = privateTransaction.determinePrivacyGroupId().toBase64String();
    return privateTransactionValidator.validate(
        privateTransaction,
        determineBesuNonce(privateTransaction.getSender(), privacyGroupId, privacyUserId),
        true);
  }

  @Override
  public Optional<ExecutedPrivateTransaction> findPrivateTransactionByPmtHash(
      final Hash pmtHash, final String enclaveKey) {

    final Optional<Transaction> transaction = blockchain.getTransactionByHash(pmtHash);

    if (transaction.isEmpty()) {
      return Optional.empty();
    }

    final TransactionLocation transactionLocation =
        blockchain.getTransactionLocation(pmtHash).orElseThrow();

    final BlockHeader blockHeader =
        blockchain.getBlockHeader(transactionLocation.getBlockHash()).orElseThrow();

    final Optional<org.hyperledger.besu.plugin.data.PrivateTransaction> pluginPrivateTransaction =
        privacyPluginService
            .getPayloadProvider()
            .getPrivateTransactionFromPayload(transaction.get());

    if (pluginPrivateTransaction.isEmpty()) {
      return Optional.empty();
    }

    final PrivateTransaction privateTransaction = readFrom(pluginPrivateTransaction.get());

    final String internalPrivacyGroupId =
        privateTransaction.determinePrivacyGroupId().toBase64String();

    final ExecutedPrivateTransaction executedPrivateTransaction =
        new ExecutedPrivateTransaction(
            blockHeader.getHash(),
            blockHeader.getNumber(),
            pmtHash,
            transactionLocation.getTransactionIndex(),
            internalPrivacyGroupId,
            privateTransaction);

    return Optional.of(executedPrivateTransaction);
  }

  @Override
  public ReceiveResponse retrieveTransaction(final String enclaveKey, final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public PrivacyGroup createPrivacyGroup(
      final List<String> addresses,
      final String name,
      final String description,
      final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public String deletePrivacyGroup(final String privacyGroupId, final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public PrivacyGroup[] findOffChainPrivacyGroupByMembers(
      final List<String> addresses, final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public long determineEeaNonce(
      final String privateFrom,
      final String[] privateFor,
      final Address address,
      final String privacyUserId) {

    final String privacyGroupId = createPrivacyGroupId(privateFrom, privateFor);

    verifyPrivacyGroupContainsPrivacyUserId(privacyUserId, privacyGroupId);

    return determineBesuNonce(address, privacyGroupId, privacyUserId);
  }

  private String createPrivacyGroupId(final String privateFrom, final String[] privateFor) {
    final Bytes32 privacyGroupId =
        PrivacyGroupUtil.calculateEeaPrivacyGroupId(
            Bytes.fromBase64String(privateFrom),
            Arrays.stream(privateFor).map(Bytes::fromBase64String).collect(Collectors.toList()));

    return privacyGroupId.toBase64String();
  }

  @Override
  public long determineBesuNonce(
      final Address sender, final String privacyGroupId, final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyUserId, privacyGroupId);

    return privateNonceProvider.getNonce(
        sender, Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)));
  }

  @Override
  public Optional<TransactionProcessingResult> simulatePrivateTransaction(
      final String privacyGroupId,
      final String privacyUserId,
      final CallParameter callParams,
      final long blockNumber) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyUserId, privacyGroupId);

    return privateTransactionSimulator.process(privacyGroupId, callParams, blockNumber);
  }

  @Override
  public Optional<Bytes> getContractCode(
      final String privacyGroupId,
      final Address contractAddress,
      final Hash blockHash,
      final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyUserId, privacyGroupId);

    return privateWorldStateReader.getContractCode(privacyGroupId, blockHash, contractAddress);
  }

  @Override
  public PrivateTransactionSimulator getTransactionSimulator() {
    return privateTransactionSimulator;
  }

  @Override
  public Optional<Hash> getStateRootByBlockNumber(
      final String privacyGroupId, final String privacyUserId, final long blockNumber) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyUserId, privacyGroupId);

    return blockchain
        .getBlockByNumber(blockNumber)
        .map(
            block ->
                privateStateRootResolver.resolveLastStateRoot(
                    Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)), block.getHash()));
  }

  @Override
  public Optional<String> buildAndSendAddPayload(
      final PrivateTransaction privateTransaction,
      final Bytes32 privacyGroupId,
      final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin - you can not send a payload without it being on-chain");
  }

  @Override
  public Optional<PrivacyGroup> findOffChainPrivacyGroupByGroupId(
      final String toBase64String, final String privacyUserId) {

    return findPrivacyGroupByGroupId(toBase64String, privacyUserId);
  }

  @Override
  public Optional<PrivacyGroup> findPrivacyGroupByGroupId(
      final String privacyGroupId, final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyUserId, privacyGroupId);

    return Optional.of(
        new PrivacyGroup(
            privacyGroupId,
            PrivacyGroup.Type.LEGACY,
            "PrivacyPlugin",
            "PrivacyPlugin",
            Collections.emptyList()));
  }

  @Override
  public List<PrivacyGroup> findOnchainPrivacyGroupByMembers(
      final List<String> asList, final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public Optional<PrivacyGroup> findOnchainPrivacyGroupAndAddNewMembers(
      final Bytes privacyGroupId,
      final String privacyUserId,
      final PrivateTransaction privateTransaction) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public List<PrivateTransactionWithMetadata> retrieveAddBlob(final String addDataKey) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public boolean isGroupAdditionTransaction(final PrivateTransaction privateTransaction) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId, final Optional<Long> blockNumber) {
    if (!privacyPluginService
        .getPrivacyGroupAuthProvider()
        .canAccess(privacyGroupId, privacyUserId, blockNumber)) {
      throw new MultiTenancyValidationException(
          "PrivacyUserId " + privacyUserId + " does not have access to " + privacyGroupId);
    }
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyUserId, final String privacyGroupId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyUserId, privacyGroupId, Optional.empty());
  }
}
