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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;

import java.util.Optional;

public abstract class AbstractRestrictedPrivacyController extends AbstractPrivacyController {

  final Enclave enclave;
  final PrivateTransactionLocator privateTransactionLocator;

  protected AbstractRestrictedPrivacyController(
      final Blockchain blockchain,
      final PrivateStateStorage privateStateStorage,
      final Enclave enclave,
      final PrivateTransactionValidator privateTransactionValidator,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateWorldStateReader privateWorldStateReader,
      final PrivateStateRootResolver privateStateRootResolver) {
    super(
        blockchain,
        privateStateStorage,
        privateTransactionValidator,
        privateTransactionSimulator,
        privateNonceProvider,
        privateWorldStateReader,
        privateStateRootResolver);
    this.enclave = enclave;
    this.privateTransactionLocator =
        new PrivateTransactionLocator(blockchain, enclave, privateStateStorage);
  }

  @Override
  public Optional<ExecutedPrivateTransaction> findPrivateTransactionByPmtHash(
      final Hash pmtHash, final String enclaveKey) {
    return privateTransactionLocator.findByPmtHash(pmtHash, enclaveKey);
  }
}
