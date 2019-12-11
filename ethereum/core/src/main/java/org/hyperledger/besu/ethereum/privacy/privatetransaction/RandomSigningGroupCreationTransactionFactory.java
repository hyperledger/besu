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
package org.hyperledger.besu.ethereum.privacy.privatetransaction;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;

public class RandomSigningGroupCreationTransactionFactory extends GroupCreationTransactionFactory {

  @Override
  public PrivateTransaction create(
      final BytesValue privacyGroupId,
      final BytesValue privateFrom,
      final List<BytesValue> participants) {
    final KeyPair signingKey = KeyPair.generate();
    return create(privateFrom, privacyGroupId, participants, 0, signingKey);
  }
}
