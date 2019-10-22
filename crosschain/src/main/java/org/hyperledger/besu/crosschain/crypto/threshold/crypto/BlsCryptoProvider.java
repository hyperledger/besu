/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.crosschain.crypto.threshold.crypto;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.altbn128.AltBn128CryptoProvider;

import java.math.BigInteger;

public interface BlsCryptoProvider {
  enum CryptoProviderTypes {
    LOCAL_ALT_BN_128
  }

  enum DigestAlgorithm {
    KECCAK256
  }

  static BlsCryptoProvider getInstance(
      final CryptoProviderTypes type, final DigestAlgorithm digestAlgorithm) {
    switch (type) {
      case LOCAL_ALT_BN_128:
        return new AltBn128CryptoProvider(digestAlgorithm);
      default:
        throw new Error("Unknown BlsCryptoProvider type: " + type);
    }
  }

  BigInteger modPrime(BigInteger val);

  BigInteger getPrimeModulus();

  BlsPoint createPointE1(BigInteger scalar);

  BlsPoint hashToCurveE1(byte[] data);

  BlsPoint getBasePointE1();

  BlsPoint createPointE2(BigInteger scalar);

  BlsPoint hashToCurveE2(byte[] data);

  BlsPoint getBasePointE2();

  /**
   * Create a signature as a point on the E1 curve.
   *
   * @param privateKey Private key to sign data with.
   * @param data Data to be signed.
   * @return signature.
   */
  BlsPoint sign(BigInteger privateKey, byte[] data);

  /**
   * Verify a signature.
   *
   * @param publicKey Point on the E2 curve to verify the data with.
   * @param data Data to be verified.
   * @param signature Signature on E1 curve.
   * @return true if the signature is verified.
   */
  boolean verify(BlsPoint publicKey, byte[] data, BlsPoint signature);
}
