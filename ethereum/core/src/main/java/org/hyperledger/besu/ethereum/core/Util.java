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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import org.apache.tuweni.bytes.Bytes;

public class Util {

  /**
   * Converts a Signature to an Address, underlying math requires the hash of the data used to
   * create the signature.
   *
   * @param seal the signature from which an address is to be extracted
   * @param dataHash the hash of the data which was signed.
   * @return The Address of the Ethereum node which signed the data defined by the supplied dataHash
   */
  public static Address signatureToAddress(final SECPSignature seal, final Hash dataHash) {
    return SignatureAlgorithmFactory.getInstance()
        .recoverPublicKeyFromSignature(dataHash, seal)
        .map(Util::publicKeyToAddress)
        .orElse(null);
  }

  public static Address publicKeyToAddress(final SECPPublicKey publicKey) {
    return publicKeyToAddress(publicKey.getEncodedBytes());
  }

  public static Address publicKeyToAddress(final Bytes publicKeyBytes) {
    return Address.extract(Hash.hash(publicKeyBytes));
  }

  /**
   * Implements a fast version of ceiling(numerator/denominator) that does not require using
   * floating point math
   *
   * @param numerator Numerator
   * @param denominator Denominator
   * @return result of ceiling(numerator/denominator)
   */
  public static int fastDivCeiling(final int numerator, final int denominator) {
    return ((numerator - 1) / denominator) + 1;
  }
}
