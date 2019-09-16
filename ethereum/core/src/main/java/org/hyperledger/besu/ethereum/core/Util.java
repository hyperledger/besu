/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.crypto.SECP256K1.PublicKey;
import org.hyperledger.besu.crypto.SECP256K1.Signature;

public class Util {

  /**
   * Converts a Signature to an Address, underlying math requires the hash of the data used to
   * create the signature.
   *
   * @param seal the signature from which an address is to be extracted
   * @param dataHash the hash of the data which was signed.
   * @return The Address of the Ethereum node which signed the data defined by the supplied dataHash
   */
  public static Address signatureToAddress(final Signature seal, final Hash dataHash) {
    return PublicKey.recoverFromSignature(dataHash, seal)
        .map(Util::publicKeyToAddress)
        .orElse(null);
  }

  public static Address publicKeyToAddress(final PublicKey publicKey) {
    return Address.extract(Hash.hash(publicKey.getEncodedBytes()));
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
