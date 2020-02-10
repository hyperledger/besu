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
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.util.List;
import java.util.Optional;

public interface PrivacyController {

  String sendTransaction(PrivateTransaction privateTransaction, String enclavePublicKey);

  ReceiveResponse retrieveTransaction(String enclaveKey, String enclavePublicKey);

  PrivacyGroup createPrivacyGroup(
      List<String> addresses, String name, String description, String enclavePublicKey);

  String deletePrivacyGroup(String privacyGroupId, String enclavePublicKey);

  PrivacyGroup[] findPrivacyGroup(List<String> addresses, String enclavePublicKey);

  Transaction createPrivacyMarkerTransaction(
      String transactionEnclaveKey, PrivateTransaction privateTransaction);

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
}
