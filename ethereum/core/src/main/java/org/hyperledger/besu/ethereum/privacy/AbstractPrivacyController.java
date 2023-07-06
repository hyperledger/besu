/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public abstract class AbstractPrivacyController implements PrivacyController {

  final Blockchain blockchain;
  final PrivateStateStorage privateStateStorage;
  final PrivateTransactionValidator privateTransactionValidator;
  final PrivateTransactionSimulator privateTransactionSimulator;
  final PrivateNonceProvider privateNonceProvider;
  final PrivateWorldStateReader privateWorldStateReader;
  final PrivateStateRootResolver privateStateRootResolver;

  protected AbstractPrivacyController(
      final Blockchain blockchain,
      final PrivacyParameters privacyParameters,
      final Optional<BigInteger> chainId,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateWorldStateReader privateWorldStateReader) {
    this(
        blockchain,
        privacyParameters.getPrivateStateStorage(),
        new PrivateTransactionValidator(chainId),
        privateTransactionSimulator,
        privateNonceProvider,
        privateWorldStateReader,
        privacyParameters.getPrivateStateRootResolver());
  }

  protected AbstractPrivacyController(
      final Blockchain blockchain,
      final PrivateStateStorage privateStateStorage,
      final PrivateTransactionValidator privateTransactionValidator,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateWorldStateReader privateWorldStateReader,
      final PrivateStateRootResolver privateStateRootResolver) {
    this.blockchain = blockchain;
    this.privateStateStorage = privateStateStorage;
    this.privateTransactionValidator = privateTransactionValidator;
    this.privateTransactionSimulator = privateTransactionSimulator;
    this.privateNonceProvider = privateNonceProvider;
    this.privateWorldStateReader = privateWorldStateReader;
    this.privateStateRootResolver = privateStateRootResolver;
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final String privacyUserId) {
    final String privacyGroupId = privateTransaction.determinePrivacyGroupId().toBase64String();
    return privateTransactionValidator.validate(
        privateTransaction,
        determineNonce(privateTransaction.getSender(), privacyGroupId, privacyUserId),
        true);
  }

  @Override
  public long determineNonce(
      final Address sender, final String privacyGroupId, final String privacyUserId) {
    return privateNonceProvider.getNonce(
        sender, Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)));
  }

  @Override
  public Optional<TransactionProcessingResult> simulatePrivateTransaction(
      final String privacyGroupId,
      final String privacyUserId,
      final CallParameter callParams,
      final long blockNumber) {
    return privateTransactionSimulator.process(privacyGroupId, callParams, blockNumber);
  }

  @Override
  public Optional<Bytes> getContractCode(
      final String privacyGroupId,
      final Address contractAddress,
      final Hash blockHash,
      final String privacyUserId) {
    return privateWorldStateReader.getContractCode(privacyGroupId, blockHash, contractAddress);
  }

  @Override
  public Optional<Hash> getStateRootByBlockNumber(
      final String privacyGroupId, final String privacyUserId, final long blockNumber) {
    return blockchain
        .getBlockByNumber(blockNumber)
        .map(
            block ->
                privateStateRootResolver.resolveLastStateRoot(
                    Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)), block.getHash()));
  }
}
