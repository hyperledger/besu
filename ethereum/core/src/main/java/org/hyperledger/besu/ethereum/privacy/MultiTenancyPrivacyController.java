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
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.List;

public class MultiTenancyPrivacyController implements PrivacyController {

  private PrivacyController privacyController;
  private Enclave enclave;

  public MultiTenancyPrivacyController(
      final PrivacyController privacyController, final Enclave enclave) {
    this.privacyController = privacyController;
    this.enclave = enclave;
  }

  @Override
  public SendTransactionResponse sendTransaction(
      final PrivateTransaction privateTransaction, final String enclavePublicKey) {
    if (!privateTransaction.getPrivateFrom().toBase64String().equals(enclavePublicKey)) {
      throw new MultiTenancyValidationException(
          "Transaction privateFrom must match enclave public key");
    }
    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      final PrivacyGroup privacyGroup =
          enclave.retrievePrivacyGroup(
              privateTransaction.getPrivacyGroupId().get().toBase64String());
      if (!privacyGroup.getMembers().contains(enclavePublicKey)) {
        throw new MultiTenancyValidationException(
            "Privacy group must contain the enclave public key");
      }
    }
    return privacyController.sendTransaction(privateTransaction, enclavePublicKey);
  }

  @Override
  public ReceiveResponse retrieveTransaction(
      final String enclaveKey, final String enclavePublicKey) {
    return privacyController.retrieveTransaction(enclaveKey, enclavePublicKey);
  }

  @Override
  public PrivacyGroup createPrivacyGroup(
      final List<String> addresses,
      final String name,
      final String description,
      final String enclavePublicKey) {
    return privacyController.createPrivacyGroup(addresses, name, description, enclavePublicKey);
  }

  @Override
  public String deletePrivacyGroup(final String privacyGroupId, final String enclavePublicKey) {
    return privacyController.deletePrivacyGroup(privacyGroupId, enclavePublicKey);
  }

  @Override
  public PrivacyGroup[] findPrivacyGroup(
      final List<String> addresses, final String enclavePublicKey) {
    return privacyController.findPrivacyGroup(addresses, enclavePublicKey);
  }

  @Override
  public Transaction createPrivacyMarkerTransaction(
      final String transactionEnclaveKey, final PrivateTransaction privateTransaction) {
    return privacyController.createPrivacyMarkerTransaction(
        transactionEnclaveKey, privateTransaction);
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction,
      final String privacyGroupId,
      final String enclavePublicKey) {
    return privacyController.validatePrivateTransaction(
        privateTransaction, privacyGroupId, enclavePublicKey);
  }

  @Override
  public long determineNonce(
      final String privateFrom,
      final String[] privateFor,
      final Address address,
      final String enclavePublicKey) {
    return privacyController.determineNonce(privateFrom, privateFor, address, enclavePublicKey);
  }

  @Override
  public long determineNonce(
      final Address sender, final String privacyGroupId, final String enclavePublicKey) {
    return privacyController.determineNonce(sender, privacyGroupId, enclavePublicKey);
  }
}
