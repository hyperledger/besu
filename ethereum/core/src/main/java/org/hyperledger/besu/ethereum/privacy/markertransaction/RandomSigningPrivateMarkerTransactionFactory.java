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
package org.hyperledger.besu.ethereum.privacy.markertransaction;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.plugin.data.PrivateTransaction;
import org.hyperledger.besu.plugin.data.UnsignedPrivateMarkerTransaction;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;

public class RandomSigningPrivateMarkerTransactionFactory
    extends SigningPrivateMarkerTransactionFactory implements PrivateMarkerTransactionFactory {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM_SUPPLIER =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  @Override
  public Address getSender(
      final PrivateTransaction privateTransaction, final String privacyUserId) {
    // Note the address here is only used as a key to lock nonce generation for the same address
    final KeyPair signingKey = SIGNATURE_ALGORITHM_SUPPLIER.get().generateKeyPair();
    return org.hyperledger.besu.datatypes.Address.extract(signingKey.getPublicKey());
  }

  @Override
  public Bytes create(
      final UnsignedPrivateMarkerTransaction unsignedPrivateMarkerTransaction,
      final PrivateTransaction privateTransaction,
      final String privacyUserId) {
    final KeyPair signingKey = SIGNATURE_ALGORITHM_SUPPLIER.get().generateKeyPair();

    return signAndBuild(unsignedPrivateMarkerTransaction, signingKey);
  }
}
