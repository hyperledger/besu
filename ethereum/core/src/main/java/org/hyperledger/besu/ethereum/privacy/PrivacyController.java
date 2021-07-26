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

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface PrivacyController {

  Optional<ExecutedPrivateTransaction> findPrivateTransactionByPmtHash(
      final Hash pmtHash, final String enclaveKey);

  String createPrivateMarkerTransactionPayload(
      PrivateTransaction privateTransaction,
      String privacyUserId,
      Optional<PrivacyGroup> privacyGroup);

  ReceiveResponse retrieveTransaction(String enclaveKey, String privacyUserId);

  PrivacyGroup createPrivacyGroup(
      List<String> addresses, String name, String description, String privacyUserId);

  String deletePrivacyGroup(String privacyGroupId, String privacyUserId);

  PrivacyGroup[] findOffChainPrivacyGroupByMembers(List<String> addresses, String privacyUserId);

  ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      PrivateTransaction privateTransaction, String privacyUserId);

  long determineEeaNonce(
      String privateFrom, String[] privateFor, Address address, String privacyUserId);

  long determineBesuNonce(Address sender, String privacyGroupId, String privacyUserId);

  Optional<TransactionProcessingResult> simulatePrivateTransaction(
      final String privacyGroupId,
      final String privacyUserId,
      final CallParameter callParams,
      final long blockNumber);

  Optional<String> buildAndSendAddPayload(
      PrivateTransaction privateTransaction, Bytes32 privacyGroupId, String privacyUserId);

  Optional<PrivacyGroup> findOffChainPrivacyGroupByGroupId(
      String privacyGroupId, String privacyUserId);

  Optional<PrivacyGroup> findPrivacyGroupByGroupId(
      final String privacyGroupId, final String privacyUserId);

  List<PrivacyGroup> findOnChainPrivacyGroupByMembers(List<String> asList, String privacyUserId);

  Optional<Bytes> getContractCode(
      final String privacyGroupId,
      final Address contractAddress,
      final Hash blockHash,
      final String privacyUserId);

  Optional<PrivacyGroup> findOnChainPrivacyGroupAndAddNewMembers(
      Bytes privacyGroupId, String privacyUserId, final PrivateTransaction privateTransaction);

  List<PrivateTransactionWithMetadata> retrieveAddBlob(String addDataKey);

  boolean isGroupAdditionTransaction(PrivateTransaction privateTransaction);

  void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId)
      throws MultiTenancyValidationException;

  void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId, final Optional<Long> blockNumber)
      throws MultiTenancyValidationException;

  PrivateTransactionSimulator getTransactionSimulator();

  Optional<Hash> getStateRootByBlockNumber(
      final String privacyGroupId, final String privacyUserId, final long blockNumber);
}
