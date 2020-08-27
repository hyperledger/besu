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
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface PrivacyController {

  Optional<ExecutedPrivateTransaction> findPrivateTransactionByPmtHash(
      final Hash pmtHash, final String enclaveKey);

  String sendTransaction(
      PrivateTransaction privateTransaction,
      String enclavePublicKey,
      Optional<PrivacyGroup> privacyGroup);

  ReceiveResponse retrieveTransaction(String enclaveKey, String enclavePublicKey);

  PrivacyGroup createPrivacyGroup(
      List<String> addresses, String name, String description, String enclavePublicKey);

  String deletePrivacyGroup(String privacyGroupId, String enclavePublicKey);

  PrivacyGroup[] findPrivacyGroup(List<String> addresses, String enclavePublicKey);

  Transaction createPrivacyMarkerTransaction(
      String transactionEnclaveKey, PrivateTransaction privateTransaction);

  Transaction createPrivacyMarkerTransaction(
      String transactionEnclaveKey,
      PrivateTransaction privateTransaction,
      Address privacyPrecompileAddress);

  ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      PrivateTransaction privateTransaction, String enclavePublicKey);

  long determineEeaNonce(
      String privateFrom, String[] privateFor, Address address, String enclavePublicKey);

  long determineBesuNonce(Address sender, String privacyGroupId, String enclavePublicKey);

  Optional<PrivateTransactionProcessor.Result> simulatePrivateTransaction(
      final String privacyGroupId,
      final String enclavePublicKey,
      final CallParameter callParams,
      final long blockNumber);

  Optional<String> buildAndSendAddPayload(
      PrivateTransaction privateTransaction, Bytes32 privacyGroupId, String enclaveKey);

  Optional<PrivacyGroup> retrieveOffChainPrivacyGroup(String toBase64String, String enclaveKey);

  List<PrivacyGroup> findOnChainPrivacyGroup(List<String> asList, String enclaveKey);

  Optional<Bytes> getContractCode(
      final String privacyGroupId,
      final Address contractAddress,
      final Hash blockHash,
      final String enclavePublicKey);

  Optional<PrivacyGroup> retrieveOnChainPrivacyGroupWithToBeAddedMembers(
      Bytes privacyGroupId, String enclavePublicKey, final PrivateTransaction privateTransaction);

  List<PrivateTransactionWithMetadata> retrieveAddBlob(String addDataKey);

  boolean isGroupAdditionTransaction(PrivateTransaction privateTransaction);

  void verifyPrivacyGroupContainsEnclavePublicKey(
      final String privacyGroupId, final String enclavePublicKey)
      throws MultiTenancyValidationException;

  void verifyPrivacyGroupContainsEnclavePublicKey(
      final String privacyGroupId, final String enclavePublicKey, final Optional<Long> blockNumber)
      throws MultiTenancyValidationException;

  PrivateTransactionSimulator getTransactionSimulator();

  Optional<Hash> getStateRootByBlockNumber(
      final String privacyGroupId, final String enclavePublicKey, final long blockNumber);
}
