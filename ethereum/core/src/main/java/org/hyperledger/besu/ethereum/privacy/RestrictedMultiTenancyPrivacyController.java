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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class RestrictedMultiTenancyPrivacyController implements PrivacyController {

  private final PrivacyController privacyController;
  private final Enclave enclave;
  private final PrivateTransactionValidator privateTransactionValidator;
  private final Optional<OnchainPrivacyGroupContract> onchainPrivacyGroupContract;

  public RestrictedMultiTenancyPrivacyController(
      final PrivacyController privacyController,
      final Optional<BigInteger> chainId,
      final Enclave enclave,
      final boolean onchainPrivacyGroupsEnabled) {
    this.privacyController = privacyController;
    this.enclave = enclave;
    this.onchainPrivacyGroupContract =
        onchainPrivacyGroupsEnabled
            ? Optional.of(
                new OnchainPrivacyGroupContract(privacyController.getTransactionSimulator()))
            : Optional.empty();
    privateTransactionValidator = new PrivateTransactionValidator(chainId);
  }

  @VisibleForTesting
  RestrictedMultiTenancyPrivacyController(
      final PrivacyController privacyController,
      final Optional<BigInteger> chainId,
      final Enclave enclave,
      final Optional<OnchainPrivacyGroupContract> onchainPrivacyGroupContract) {
    this.privacyController = privacyController;
    this.enclave = enclave;
    this.onchainPrivacyGroupContract = onchainPrivacyGroupContract;
    privateTransactionValidator = new PrivateTransactionValidator(chainId);
  }

  @Override
  public Optional<ExecutedPrivateTransaction> findPrivateTransactionByPmtHash(
      final Hash pmtHash, final String enclaveKey) {
    return privacyController.findPrivateTransactionByPmtHash(pmtHash, enclaveKey);
  }

  @Override
  public String createPrivateMarkerTransactionPayload(
      final PrivateTransaction privateTransaction,
      final String privacyUserId,
      final Optional<PrivacyGroup> maybePrivacyGroup) {
    verifyPrivateFromMatchesPrivacyUserId(
        privateTransaction.getPrivateFrom().toBase64String(), privacyUserId);
    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      verifyPrivacyGroupContainsPrivacyUserId(
          privateTransaction.getPrivacyGroupId().get().toBase64String(), privacyUserId);
    }
    return privacyController.createPrivateMarkerTransactionPayload(
        privateTransaction, privacyUserId, maybePrivacyGroup);
  }

  @Override
  public ReceiveResponse retrieveTransaction(final String enclaveKey, final String privacyUserId) {
    // no validation necessary as the enclave receive only returns data for the enclave public key
    return privacyController.retrieveTransaction(enclaveKey, privacyUserId);
  }

  @Override
  public PrivacyGroup createPrivacyGroup(
      final List<String> addresses,
      final String name,
      final String description,
      final String privacyUserId) {
    // no validation necessary as the enclave createPrivacyGroup fails if the addresses don't
    // include the from (privacyUserId)
    return privacyController.createPrivacyGroup(addresses, name, description, privacyUserId);
  }

  @Override
  public String deletePrivacyGroup(final String privacyGroupId, final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);
    return privacyController.deletePrivacyGroup(privacyGroupId, privacyUserId);
  }

  @Override
  public PrivacyGroup[] findOffchainPrivacyGroupByMembers(
      final List<String> addresses, final String privacyUserId) {
    if (!addresses.contains(privacyUserId)) {
      throw new MultiTenancyValidationException(
          "Privacy group addresses must contain the enclave public key");
    }
    final PrivacyGroup[] resultantGroups =
        privacyController.findOffchainPrivacyGroupByMembers(addresses, privacyUserId);
    return Arrays.stream(resultantGroups)
        .filter(g -> g.getMembers().contains(privacyUserId))
        .toArray(PrivacyGroup[]::new);
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final String privacyUserId) {

    final String privacyGroupId = privateTransaction.determinePrivacyGroupId().toBase64String();
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);
    return privateTransactionValidator.validate(
        privateTransaction,
        determineBesuNonce(privateTransaction.getSender(), privacyGroupId, privacyUserId),
        true);
  }

  @Override
  public long determineEeaNonce(
      final String privateFrom,
      final String[] privateFor,
      final Address address,
      final String privacyUserId) {
    verifyPrivateFromMatchesPrivacyUserId(privateFrom, privacyUserId);
    return privacyController.determineEeaNonce(privateFrom, privateFor, address, privacyUserId);
  }

  @Override
  public long determineBesuNonce(
      final Address sender, final String privacyGroupId, final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);
    return privacyController.determineBesuNonce(sender, privacyGroupId, privacyUserId);
  }

  @Override
  public Optional<TransactionProcessingResult> simulatePrivateTransaction(
      final String privacyGroupId,
      final String privacyUserId,
      final CallParameter callParams,
      final long blockNumber) {
    verifyPrivacyGroupContainsPrivacyUserId(
        privacyGroupId, privacyUserId, Optional.of(blockNumber));
    return privacyController.simulatePrivateTransaction(
        privacyGroupId, privacyUserId, callParams, blockNumber);
  }

  @Override
  public Optional<String> buildAndSendAddPayload(
      final PrivateTransaction privateTransaction,
      final Bytes32 privacyGroupId,
      final String privacyUserId) {
    verifyPrivateFromMatchesPrivacyUserId(
        privateTransaction.getPrivateFrom().toBase64String(), privacyUserId);
    verifyPrivacyGroupContainsPrivacyUserId(
        privateTransaction.getPrivacyGroupId().get().toBase64String(), privacyUserId);
    return privacyController.buildAndSendAddPayload(
        privateTransaction, privacyGroupId, privacyUserId);
  }

  @Override
  public Optional<PrivacyGroup> findOffchainPrivacyGroupByGroupId(
      final String privacyGroupId, final String privacyUserId) {
    final Optional<PrivacyGroup> maybePrivacyGroup =
        privacyController.findOffchainPrivacyGroupByGroupId(privacyGroupId, privacyUserId);
    checkGroupParticipation(maybePrivacyGroup, privacyUserId);
    return maybePrivacyGroup;
  }

  @Override
  public Optional<PrivacyGroup> findPrivacyGroupByGroupId(
      final String privacyGroupId, final String privacyUserId) {
    final Optional<PrivacyGroup> maybePrivacyGroup =
        privacyController.findPrivacyGroupByGroupId(privacyGroupId, privacyUserId);
    checkGroupParticipation(maybePrivacyGroup, privacyUserId);
    return maybePrivacyGroup;
  }

  private void checkGroupParticipation(
      final Optional<PrivacyGroup> maybePrivacyGroup, final String enclaveKey) {
    if (maybePrivacyGroup.isPresent()
        && !maybePrivacyGroup.get().getMembers().contains(enclaveKey)) {
      throw new MultiTenancyValidationException(
          "Privacy group must contain the enclave public key");
    }
  }

  @Override
  public List<PrivacyGroup> findOnchainPrivacyGroupByMembers(
      final List<String> addresses, final String privacyUserId) {
    if (!addresses.contains(privacyUserId)) {
      throw new MultiTenancyValidationException(
          "Privacy group addresses must contain the enclave public key");
    }
    final List<PrivacyGroup> resultantGroups =
        privacyController.findOnchainPrivacyGroupByMembers(addresses, privacyUserId);
    return resultantGroups.stream()
        .filter(g -> g.getMembers().contains(privacyUserId))
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
      final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);
    return privacyController.getContractCode(
        privacyGroupId, contractAddress, blockHash, privacyUserId);
  }

  @Override
  public Optional<PrivacyGroup> findOnchainPrivacyGroupAndAddNewMembers(
      final Bytes privacyGroupId,
      final String privacyUserId,
      final PrivateTransaction privateTransaction) {
    final Optional<PrivacyGroup> maybePrivacyGroup =
        privacyController.findOnchainPrivacyGroupAndAddNewMembers(
            privacyGroupId, privacyUserId, privateTransaction);
    // The check that the privacyUserId is a member (if the group already exists) is done in the
    // DefaultPrivacyController.
    return maybePrivacyGroup;
  }

  private void verifyPrivateFromMatchesPrivacyUserId(
      final String privateFrom, final String privacyUserId) {
    if (!privateFrom.equals(privacyUserId)) {
      throw new MultiTenancyValidationException(
          "Transaction privateFrom must match enclave public key");
    }
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId, Optional.empty());
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId, final Optional<Long> blockNumber)
      throws MultiTenancyValidationException {
    onchainPrivacyGroupContract.ifPresentOrElse(
        (contract) -> {
          verifyOnchainPrivacyGroupContainsMember(
              contract, privacyGroupId, privacyUserId, blockNumber);
        },
        () -> {
          verifyOffchainPrivacyGroupContainsMember(privacyGroupId, privacyUserId);
        });
  }

  private void verifyOffchainPrivacyGroupContainsMember(
      final String privacyGroupId, final String privacyUserId) {
    final PrivacyGroup offchainPrivacyGroup = enclave.retrievePrivacyGroup(privacyGroupId);
    if (!offchainPrivacyGroup.getMembers().contains(privacyUserId)) {
      throw new MultiTenancyValidationException(
          "Privacy group must contain the enclave public key");
    }
  }

  private void verifyOnchainPrivacyGroupContainsMember(
      final OnchainPrivacyGroupContract contract,
      final String privacyGroupId,
      final String privacyUserId,
      final Optional<Long> blockNumber) {
    final Optional<PrivacyGroup> maybePrivacyGroup =
        contract.getPrivacyGroupByIdAndBlockNumber(privacyGroupId, blockNumber);
    // IF the group exists, check member
    // ELSE member is valid if the group doesn't exist yet - this is normal for onchain privacy
    // groups
    maybePrivacyGroup.ifPresent(
        (group) -> {
          if (!group.getMembers().contains(privacyUserId)) {
            throw new MultiTenancyValidationException(
                "Privacy group must contain the enclave public key");
          }
        });
  }

  @Override
  public PrivateTransactionSimulator getTransactionSimulator() {
    return privacyController.getTransactionSimulator();
  }

  @Override
  public Optional<Hash> getStateRootByBlockNumber(
      final String privacyGroupId, final String privacyUserId, final long blockNumber) {
    verifyPrivacyGroupContainsPrivacyUserId(
        privacyGroupId, privacyUserId, Optional.of(blockNumber));
    return privacyController.getStateRootByBlockNumber(privacyGroupId, privacyUserId, blockNumber);
  }
}
