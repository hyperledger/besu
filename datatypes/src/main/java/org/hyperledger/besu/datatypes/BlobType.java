/*
 * Copyright contributors to Besu.
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

/**
 * Enum representing different types of sidecars used in KZG proofs. Each enum constant has an
 * associated version ID.
 */
public enum BlobType {
  /** Version ID for KZG proofs. */
  KZG_PROOF(0),
  /** Version ID for KZG cell proofs. */
  KZG_CELL_PROOFS(1);

  /** The version ID associated with this BlobType. */
  private final int versionId;

  /**
   * Constructs a BlobType with the specified version ID.
   *
   * @param versionId the version ID associated with this BlobType
   */
  BlobType(final int versionId) {
    this.versionId = versionId;
  }

  /**
   * Retrieves the version ID associated with this BlobType.
   *
   * @return the version ID as an integer
   */
  public int getVersionId() {
    return versionId;
  }

  /**
   * Retrieves the BlobType enum constant corresponding to the given version ID.
   *
   * @param versionId the integer version ID
   * @return the corresponding BlobType enum constant
   * @throws IllegalArgumentException if no matching BlobType is found
   */
  public static BlobType of(final int versionId) {
    for (BlobType blobType : BlobType.values()) {
      if (blobType.getVersionId() == versionId) {
        return blobType;
      }
    }
    throw new IllegalArgumentException("No BlobType found for version ID: " + versionId);
  }
}
