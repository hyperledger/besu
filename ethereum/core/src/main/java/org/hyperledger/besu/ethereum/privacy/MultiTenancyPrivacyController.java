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

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class MultiTenancyPrivacyController implements PrivacyController {

  private final PrivacyController privacyController;
  private final Enclave enclave;
  private final PrivateTransactionValidator privateTransactionValidator;

  public MultiTenancyPrivacyController(
      final PrivacyController privacyController,
      final Optional<BigInteger> chainId,
      final Enclave enclave) {
    this.privacyController = privacyController;
    this.enclave = enclave;
    privateTransactionValidator = new PrivateTransactionValidator(chainId);
  }

  @Override
  public String sendTransaction(
      final PrivateTransaction privateTransaction,
      final String enclavePublicKey,
      final Optional<PrivacyGroup> maybePrivacyGroup) {
    verifyPrivateFromMatchesEnclavePublicKey(
        privateTransaction.getPrivateFrom().toBase64String(), enclavePublicKey);
    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      verifyPrivacyGroupContainsEnclavePublicKey(
          privateTransaction.getPrivacyGroupId().get().toBase64String(), enclavePublicKey);
    }
    return privacyController.sendTransaction(
        privateTransaction, enclavePublicKey, maybePrivacyGroup);
  }

  @Override
  public ReceiveResponse retrieveTransaction(
      final String enclaveKey, final String enclavePublicKey) {
    // no validation necessary as the enclave receive only returns data for the enclave public key
    return privacyController.retrieveTransaction(enclaveKey, enclavePublicKey);
  }

  @Override
  public PrivacyGroup createPrivacyGroup(
      final List<String> addresses,
      final String name,
      final String description,
      final String enclavePublicKey) {
    // no validation necessary as the enclave createPrivacyGroup fails if the addresses don't
    // include the from (enclavePublicKey)
    return privacyController.createPrivacyGroup(addresses, name, description, enclavePublicKey);
  }

  @Override
  public String deletePrivacyGroup(final String privacyGroupId, final String enclavePublicKey) {
    verifyPrivacyGroupContainsEnclavePublicKey(privacyGroupId, enclavePublicKey);
    return privacyController.deletePrivacyGroup(privacyGroupId, enclavePublicKey);
  }

  @Override
  public PrivacyGroup[] findPrivacyGroup(
      final List<String> addresses, final String enclavePublicKey) {
    if (!addresses.contains(enclavePublicKey)) {
      throw new MultiTenancyValidationException(
          "Privacy group addresses must contain the enclave public key");
    }
    final PrivacyGroup[] resultantGroups =
        privacyController.findPrivacyGroup(addresses, enclavePublicKey);
    return Arrays.stream(resultantGroups)
        .filter(g -> g.getMembers().contains(enclavePublicKey))
        .toArray(PrivacyGroup[]::new);
  }

  @Override
  public Transaction createPrivacyMarkerTransaction(
      final String transactionEnclaveKey, final PrivateTransaction privateTransaction) {
    return privacyController.createPrivacyMarkerTransaction(
        transactionEnclaveKey, privateTransaction);
  }

  @Override
  public Transaction createPrivacyMarkerTransaction(
      final String transactionEnclaveKey,
      final PrivateTransaction privateTransaction,
      final Address privacyPrecompileAddress) {
    return privacyController.createPrivacyMarkerTransaction(
        transactionEnclaveKey, privateTransaction, privacyPrecompileAddress);
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final String enclavePublicKey) {
    final String privacyGroupId = privateTransaction.determinePrivacyGroupId().toBase64String();
    verifyPrivacyGroupContainsEnclavePublicKey(privacyGroupId, enclavePublicKey);
    return privateTransactionValidator.validate(
        privateTransaction,
        determineBesuNonce(privateTransaction.getSender(), privacyGroupId, enclavePublicKey),
        true);
  }

  @Override
  public long determineEeaNonce(
      final String privateFrom,
      final String[] privateFor,
      final Address address,
      final String enclavePublicKey) {
    verifyPrivateFromMatchesEnclavePublicKey(privateFrom, enclavePublicKey);
    return privacyController.determineEeaNonce(privateFrom, privateFor, address, enclavePublicKey);
  }

  @Override
  public long determineBesuNonce(
      final Address sender, final String privacyGroupId, final String enclavePublicKey) {
    verifyPrivacyGroupContainsEnclavePublicKey(privacyGroupId, enclavePublicKey);
    return privacyController.determineBesuNonce(sender, privacyGroupId, enclavePublicKey);
  }

  @Override
  public Optional<PrivateTransactionProcessor.Result> simulatePrivateTransaction(
      final String privacyGroupId,
      final String enclavePublicKey,
      final CallParameter callParams,
      final long blockNumber) {
    verifyPrivacyGroupContainsEnclavePublicKey(privacyGroupId, enclavePublicKey);
    return privacyController.simulatePrivateTransaction(
        privacyGroupId, enclavePublicKey, callParams, blockNumber);
  }

  @Override
  public Optional<String> buildAndSendAddPayload(
      final PrivateTransaction privateTransaction,
      final Bytes32 privacyGroupId,
      final String enclaveKey) {
    verifyPrivateFromMatchesEnclavePublicKey(
        privateTransaction.getPrivateFrom().toBase64String(), enclaveKey);
    verifyPrivacyGroupContainsEnclavePublicKey(
        privateTransaction.getPrivacyGroupId().get().toBase64String(), enclaveKey);
    return privacyController.buildAndSendAddPayload(privateTransaction, privacyGroupId, enclaveKey);
  }

  @Override
  public Optional<PrivacyGroup> retrieveOffChainPrivacyGroup(
      final String privacyGroupId, final String enclavePublicKey) {
    final Optional<PrivacyGroup> maybePrivacyGroup =
        privacyController.retrieveOffChainPrivacyGroup(privacyGroupId, enclavePublicKey);
    if (!maybePrivacyGroup.get().getMembers().contains(enclavePublicKey)) {
      throw new MultiTenancyValidationException(
          "Privacy group must contain the enclave public key");
    }
    return maybePrivacyGroup;
  }

  @Override
  public List<PrivacyGroup> findOnChainPrivacyGroup(
      final List<String> addresses, final String enclavePublicKey) {
    if (!addresses.contains(enclavePublicKey)) {
      throw new MultiTenancyValidationException(
          "Privacy group addresses must contain the enclave public key");
    }
    final List<PrivacyGroup> resultantGroups =
        privacyController.findOnChainPrivacyGroup(addresses, enclavePublicKey);
    return resultantGroups.stream()
        .filter(g -> g.getMembers().contains(enclavePublicKey))
        .collect(Collectors.toList());
  }

  @Override
  public List<PrivateTransactionWithMetadata> retrieveAddBlob(final String addDataKey) {
    return privacyController.retrieveAddBlob(addDataKey);
  }

  @Override
  public boolean isGroupAdditionTransaction(final PrivateTransaction privateTransaction) {
    return privacyController.isGroupAdditionTransaction(privateTransaction);
  }

  @Override
  public Optional<Bytes> getContractCode(
      final String privacyGroupId,
      final Address contractAddress,
      final Hash blockHash,
      final String enclavePublicKey) {
    verifyPrivacyGroupContainsEnclavePublicKey(privacyGroupId, enclavePublicKey);
    return privacyController.getContractCode(
        privacyGroupId, contractAddress, blockHash, enclavePublicKey);
  }

  @Override
  public Optional<PrivacyGroup> retrieveOnChainPrivacyGroup(
      final Bytes privacyGroupId, final String enclavePublicKey) {
    final Optional<PrivacyGroup> maybePrivacyGroup =
        privacyController.retrieveOnChainPrivacyGroup(privacyGroupId, enclavePublicKey);
    if (maybePrivacyGroup.isPresent()
        && !maybePrivacyGroup.get().getMembers().contains(enclavePublicKey)) {
      throw new MultiTenancyValidationException(
          "Privacy group must contain the enclave public key");
    }
    return maybePrivacyGroup;
  }

  private void verifyPrivateFromMatchesEnclavePublicKey(
      final String privateFrom, final String enclavePublicKey) {
    if (!privateFrom.equals(enclavePublicKey)) {
      throw new MultiTenancyValidationException(
          "Transaction privateFrom must match enclave public key");
    }
  }

  @Override
  public void verifyPrivacyGroupContainsEnclavePublicKey(
      final String privacyGroupId, final String enclavePublicKey) {
    final PrivacyGroup privacyGroup = enclave.retrievePrivacyGroup(privacyGroupId);
    if (!privacyGroup.getMembers().contains(enclavePublicKey)) {
      throw new MultiTenancyValidationException(
          "Privacy group must contain the enclave public key");
    }
  }
}
