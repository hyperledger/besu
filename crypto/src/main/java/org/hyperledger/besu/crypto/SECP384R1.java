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
package org.hyperledger.besu.crypto;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.math.ec.custom.sec.SecP384R1Curve;

public class SECP384R1 extends AbstractSignatureAlgorithm {

  private static final int PRIVATE_KEY_BYTE_LENGTH = 48;
  private static final int PUBLIC_KEY_BYTE_LENGTH = 96;
  private static final int SIGNATURE_BYTE_LENGTH = 97;

  public static final String CURVE_NAME = "secp384r1";

  @Override
  public void enableNative() {
    throw new RuntimeException("Native encryption not implemented for SECP384R1");
  }

  @Override
  public SECPSignature sign(final Bytes dataHash, final KeyPair keyPair) {
    return super.sign(dataHash, keyPair, new SHA384Digest());
  }

  @Override
  public SECPSignature normaliseSignature(
      final BigInteger nativeR,
      final BigInteger nativeS,
      final SECPPublicKey publicKey,
      final Bytes dataHash) {
    return super.normaliseSignature(nativeR, nativeS, publicKey, dataHash, SecP384R1Curve.q);
  }

  @Override
  public Optional<SECPPublicKey> recoverPublicKeyFromSignature(
      final Bytes dataHash, final SECPSignature signature) {
    return super.recoverPublicKeyFromSignature(dataHash, signature, SecP384R1Curve.q);
  }

  @Override
  public String getCurveName() {
    return CURVE_NAME;
  }

  @Override
  public int getPrivateKeyByteLength() {
    return PRIVATE_KEY_BYTE_LENGTH;
  }

  @Override
  public int getPublicKeyByteLength() {
    return PUBLIC_KEY_BYTE_LENGTH;
  }

  @Override
  public int getSignatureByteLength() {
    return SIGNATURE_BYTE_LENGTH;
  }
}
