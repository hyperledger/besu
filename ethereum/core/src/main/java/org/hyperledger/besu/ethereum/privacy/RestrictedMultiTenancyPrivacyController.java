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
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class RestrictedMultiTenancyPrivacyController implements PrivacyController {

  private final PrivacyController privacyController;
  private final Enclave enclave;
  private final PrivateTransactionValidator privateTransactionValidator;

  public RestrictedMultiTenancyPrivacyController(
      final PrivacyController privacyController,
      final Optional<BigInteger> chainId,
      final Enclave enclave) {
    this.privacyController = privacyController;
    this.enclave = enclave;
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
  public PrivacyGroup[] findPrivacyGroupByMembers(
      final List<String> addresses, final String privacyUserId) {
    if (!addresses.contains(privacyUserId)) {
      throw new MultiTenancyValidationException(
          "Privacy group addresses must contain the enclave public key");
    }
    final PrivacyGroup[] resultantGroups =
        privacyController.findPrivacyGroupByMembers(addresses, privacyUserId);
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
        determineNonce(privateTransaction.getSender(), privacyGroupId, privacyUserId),
        true);
  }

  @Override
  public long determineNonce(
      final Address sender, final String privacyGroupId, final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);
    return privacyController.determineNonce(sender, privacyGroupId, privacyUserId);
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
  public Optional<Bytes> getContractCode(
      final String privacyGroupId,
      final Address contractAddress,
      final Hash blockHash,
      final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);
    return privacyController.getContractCode(
        privacyGroupId, contractAddress, blockHash, privacyUserId);
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
    privacyController.verifyPrivacyGroupContainsPrivacyUserId(
        privacyGroupId, privacyUserId, blockNumber);
  }

  @Override
  public Optional<Hash> getStateRootByBlockNumber(
      final String privacyGroupId, final String privacyUserId, final long blockNumber) {
    verifyPrivacyGroupContainsPrivacyUserId(
        privacyGroupId, privacyUserId, Optional.of(blockNumber));
    return privacyController.getStateRootByBlockNumber(privacyGroupId, privacyUserId, blockNumber);
  }
}
