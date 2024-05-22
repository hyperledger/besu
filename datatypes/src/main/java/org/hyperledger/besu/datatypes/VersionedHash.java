/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.datatypes;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * A VersionedHash is a Hash that forfeits its most significant byte to indicate the hashing
 * algorithm which was used.
 */
public class VersionedHash {

  /**
   * The versionedHash value. The first byte is the version id, the remainder is the subsequent
   * bytes of the hash.
   */
  Bytes32 hashish;

  /** The version id for sha256 hashes. */
  public static final byte SHA256_VERSION_ID = 1;

  /** A default versioned hash, nonsensical but valid. */
  public static final VersionedHash DEFAULT_VERSIONED_HASH =
      new VersionedHash(SHA256_VERSION_ID, Hash.ZERO);

  /**
   * Construct a VersionedHash from a Bytes32 value.
   *
   * @param versionId The version id of the hash. 01 for sha256.
   * @param hash The hash value being versioned.
   */
  public VersionedHash(final byte versionId, final Hash hash) {
    if (versionId != SHA256_VERSION_ID) {
      throw new IllegalArgumentException("Only supported hash version is 0x01, sha256 hash.");
    }
    this.hashish =
        Bytes32.wrap(
            Bytes.concatenate(Bytes.of(SHA256_VERSION_ID), hash.slice(1, hash.size() - 1)));
  }

  /**
   * Parse a VersionedHash from a Bytes32 value.
   *
   * @param typedHash raw versioned hash bytes to parse.
   */
  public VersionedHash(final Bytes32 typedHash) {
    byte versionId = typedHash.get(0);
    if (versionId != SHA256_VERSION_ID) {
      throw new IllegalArgumentException("Only supported hash version is 0x01, sha256 hash.");
    }
    this.hashish = typedHash;
  }

  /**
   * Parse a hexadecimal string representing a versioned hash value.
   *
   * @param str A hexadecimal string (with or without the leading '0x') representing a valid hash
   *     value.
   * @return The parsed hash.
   * @throws NullPointerException if the provided string is {@code null}.
   * @throws IllegalArgumentException if the string is either not hexadecimal, or not the valid
   *     representation of a versioned hash (not 32 bytes or bad version).
   */
  @JsonCreator
  public static VersionedHash fromHexString(final String str) {
    return new VersionedHash(Bytes32.fromHexString(str));
  }

  /**
   * Convert it to raw bytes.
   *
   * @return The hash value.
   */
  public Bytes32 toBytes() {
    return this.hashish;
  }

  /**
   * The version id of the hash.
   *
   * @return the version id.
   */
  public byte getVersionId() {
    return this.hashish.get(0);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VersionedHash that = (VersionedHash) o;
    return getVersionId() == that.getVersionId() && Objects.equals(this.toBytes(), that.toBytes());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getVersionId(), hashish);
  }

  @JsonValue
  @Override
  public String toString() {
    return this.toBytes().toHexString();
  }
}
