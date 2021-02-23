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
import java.util.function.UnaryOperator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.math.ec.ECPoint;

public interface EllipticCurveSignature {
  // needs to be known at compile time otherwise triggers InsecureCryptoUsage error
  String ALGORITHM = "ECDSA";

  void enableNative();

  Signature sign(final Bytes32 dataHash, final KeyPair keyPair);

  boolean verify(final Bytes data, final Signature signature, final PublicKey pub);

  boolean verify(
      final Bytes data,
      final Signature signature,
      final PublicKey pub,
      final UnaryOperator<Bytes> preprocessor);

  Signature normaliseSignature(
      final BigInteger nativeR,
      final BigInteger nativeS,
      final PublicKey publicKey,
      final Bytes32 dataHash);

  Bytes32 calculateECDHKeyAgreement(final PrivateKey privKey, final PublicKey theirPubKey);

  BigInteger getHalfCurveOrder();

  String getProvider();

  String getCurveName();

  PrivateKey createPrivateKey(final BigInteger key);

  PrivateKey createPrivateKey(final Bytes32 key);

  PublicKey createPublicKey(final PrivateKey privateKey);

  PublicKey createPublicKey(final BigInteger key);

  PublicKey createPublicKey(final Bytes encoded);

  Optional<PublicKey> recoverPublicKeyFromSignature(
      final Bytes32 dataHash, final Signature signature);

  ECPoint publicKeyAsEcPoint(final PublicKey publicKey);

  KeyPair createKeyPair(final PrivateKey privateKey);

  KeyPair generateKeyPair();

  Signature createSignature(final BigInteger r, final BigInteger s, final byte recId);

  Signature decodeSignature(final Bytes bytes);
}
