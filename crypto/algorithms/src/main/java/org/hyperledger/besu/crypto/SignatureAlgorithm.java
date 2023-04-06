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

/** The interface Signature algorithm. */
public interface SignatureAlgorithm {
  /** The constant ALGORITHM. */
  // needs to be known at compile time otherwise triggers InsecureCryptoUsage error
  String ALGORITHM = "ECDSA";

  /** Disable native. */
  void disableNative();

  /**
   * Is native enabled.
   *
   * @return the boolean
   */
  boolean isNative();

  /**
   * Sign secp signature.
   *
   * @param dataHash the data hash
   * @param keyPair the key pair
   * @return the secp signature
   */
  SECPSignature sign(final Bytes32 dataHash, final KeyPair keyPair);

  /**
   * Verify given data.
   *
   * @param data the data
   * @param signature the signature
   * @param pub the pub
   * @return the boolean
   */
  boolean verify(final Bytes data, final SECPSignature signature, final SECPPublicKey pub);

  /**
   * Verify given data.
   *
   * @param data the data
   * @param signature the signature
   * @param pub the pub
   * @param preprocessor the preprocessor
   * @return the boolean
   */
  boolean verify(
      final Bytes data,
      final SECPSignature signature,
      final SECPPublicKey pub,
      final UnaryOperator<Bytes> preprocessor);

  /**
   * Normalise secp signature.
   *
   * @param nativeR the native r
   * @param nativeS the native s
   * @param publicKey the public key
   * @param dataHash the data hash
   * @return the secp signature
   */
  SECPSignature normaliseSignature(
      final BigInteger nativeR,
      final BigInteger nativeS,
      final SECPPublicKey publicKey,
      final Bytes32 dataHash);

  /**
   * Calculate ecdh key agreement as bytes32.
   *
   * @param privKey the private key
   * @param theirPubKey the public key
   * @return the bytes 32
   */
  Bytes32 calculateECDHKeyAgreement(final SECPPrivateKey privKey, final SECPPublicKey theirPubKey);

  /**
   * Gets half curve order.
   *
   * @return the half curve order
   */
  BigInteger getHalfCurveOrder();

  /**
   * Gets provider.
   *
   * @return the provider
   */
  String getProvider();

  /**
   * Gets curve name.
   *
   * @return the curve name
   */
  String getCurveName();

  /**
   * Create secp private key.
   *
   * @param key the key
   * @return the secp private key
   */
  SECPPrivateKey createPrivateKey(final BigInteger key);

  /**
   * Create secp private key.
   *
   * @param key the key
   * @return the secp private key
   */
  SECPPrivateKey createPrivateKey(final Bytes32 key);

  /**
   * Create secp public key.
   *
   * @param privateKey the private key
   * @return the secp public key
   */
  SECPPublicKey createPublicKey(final SECPPrivateKey privateKey);

  /**
   * Create secp public key.
   *
   * @param key the key
   * @return the secp public key
   */
  SECPPublicKey createPublicKey(final BigInteger key);

  /**
   * Createsecp public key.
   *
   * @param encoded the encoded
   * @return the secp public key
   */
  SECPPublicKey createPublicKey(final Bytes encoded);

  /**
   * Recover public key from signature optional.
   *
   * @param dataHash the data hash
   * @param signature the signature
   * @return the optional public key
   */
  Optional<SECPPublicKey> recoverPublicKeyFromSignature(
      final Bytes32 dataHash, final SECPSignature signature);

  /**
   * Public key as EcPoint.
   *
   * @param publicKey the public key
   * @return the ec point
   */
  ECPoint publicKeyAsEcPoint(final SECPPublicKey publicKey);

  /**
   * Is valid public key.
   *
   * @param publicKey the public key
   * @return the boolean
   */
  boolean isValidPublicKey(SECPPublicKey publicKey);

  /**
   * Create key pair.
   *
   * @param privateKey the private key
   * @return the key pair
   */
  KeyPair createKeyPair(final SECPPrivateKey privateKey);

  /**
   * Generate key pair.
   *
   * @return the key pair
   */
  KeyPair generateKeyPair();

  /**
   * Create secp signature.
   *
   * @param r the r
   * @param s the s
   * @param recId the rec id
   * @return the secp signature
   */
  SECPSignature createSignature(final BigInteger r, final BigInteger s, final byte recId);

  /**
   * Decode secp signature.
   *
   * @param bytes the bytes
   * @return the secp signature
   */
  SECPSignature decodeSignature(final Bytes bytes);

  /**
   * Compress public key bytes.
   *
   * @param uncompressedKey the uncompressed key
   * @return the bytes
   */
  Bytes compressPublicKey(final SECPPublicKey uncompressedKey);
}
