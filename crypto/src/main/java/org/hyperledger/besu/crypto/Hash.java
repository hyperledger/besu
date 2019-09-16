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
package org.hyperledger.besu.crypto;

import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Various utilities for providing hashes (digests) of arbitrary data. */
public abstract class Hash {
  private Hash() {}

  public static final String KECCAK256_ALG = "KECCAK-256";

  private static final String SHA256_ALG = "SHA-256";
  private static final String RIPEMD160 = "RIPEMD160";
  private static final String BLAKE2BF_ALG = "BLAKE2BF";

  /**
   * Helper method to generate a digest using the provided algorithm.
   *
   * @param input The input bytes to produce the digest for.
   * @param alg The name of the digest algorithm to use.
   * @return A digest.
   */
  private static byte[] digestUsingAlgorithm(final BytesValue input, final String alg) {
    try {
      final MessageDigest digest = MessageDigestFactory.create(alg);
      input.update(digest);
      return digest.digest();
    } catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Digest using SHA2-256.
   *
   * @param input The input bytes to produce the digest for.
   * @return A digest.
   */
  public static Bytes32 sha256(final BytesValue input) {
    return Bytes32.wrap(digestUsingAlgorithm(input, SHA256_ALG));
  }

  /**
   * Digest using keccak-256.
   *
   * @param input The input bytes to produce the digest for.
   * @return A digest.
   */
  public static Bytes32 keccak256(final BytesValue input) {
    return Bytes32.wrap(digestUsingAlgorithm(input, KECCAK256_ALG));
  }

  /**
   * Digest using RIPEMD-160.
   *
   * @param input The input bytes to produce the digest for.
   * @return A digest.
   */
  public static BytesValue ripemd160(final BytesValue input) {
    return BytesValue.wrap(digestUsingAlgorithm(input, RIPEMD160));
  }

  /**
   * Digest using Blake2f compression function.
   *
   * @param input The input bytes to produce the digest for.
   * @return A digest.
   */
  public static BytesValue blake2bf(final BytesValue input) {
    return BytesValue.wrap(digestUsingAlgorithm(input, BLAKE2BF_ALG));
  }
}
