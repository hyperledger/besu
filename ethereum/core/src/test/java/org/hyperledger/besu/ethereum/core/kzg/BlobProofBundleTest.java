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
package org.hyperledger.besu.ethereum.core.kzg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.datatypes.VersionedHash.DEFAULT_VERSIONED_HASH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.datatypes.BlobType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.ethereum.core.BlobTestFixture;
import org.hyperledger.besu.ethereum.util.TrustedSetupClassLoaderExtension;

import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;

public class BlobProofBundleTest extends TrustedSetupClassLoaderExtension {

  final Blob blob = new Blob(Bytes.EMPTY);
  final KZGCommitment kzgCommitment = new KZGCommitment(Bytes48.ZERO);
  final VersionedHash versionedHash = DEFAULT_VERSIONED_HASH;
  List<KZGProof> kzgProofs = List.of(new KZGProof(Bytes48.ZERO));
  List<KZGProof> kzgCellProofs =
      Collections.nCopies(CKZG4844Helper.CELL_PROOFS_PER_BLOB, new KZGProof(Bytes48.ZERO));

  @Test
  void shouldSucceedWithValidInputsV0() {
    BlobProofBundle bundle =
        new BlobProofBundle(BlobType.KZG_PROOF, blob, kzgCommitment, kzgProofs, versionedHash);

    assertEquals(BlobType.KZG_PROOF, bundle.getBlobType());
    assertEquals(blob, bundle.getBlob());
    assertEquals(kzgCommitment, bundle.getKzgCommitment());
    assertEquals(versionedHash, bundle.getVersionedHash());
    assertEquals(kzgProofs, bundle.getKzgProof());
  }

  @Test
  void shouldThrowsExceptionWhenKzgCommitmentIsNull() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BlobProofBundle(BlobType.KZG_PROOF, blob, null, kzgProofs, versionedHash));
    assertEquals("kzgCommitment must not be empty", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenVersionedHashIsNull() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BlobProofBundle(BlobType.KZG_PROOF, blob, kzgCommitment, kzgProofs, null));
    assertEquals("versionedHash must not be empty", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenBlobIsNull() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BlobProofBundle(
                    BlobType.KZG_PROOF, null, kzgCommitment, kzgProofs, versionedHash));
    assertEquals("blob must not be empty", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenProof_empty() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BlobProofBundle(BlobType.KZG_PROOF, blob, kzgCommitment, null, versionedHash));
    assertEquals("kzgProof must not be empty", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenProofWrongSize() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BlobProofBundle(
                    BlobType.KZG_PROOF, blob, kzgCommitment, kzgCellProofs, versionedHash));
    assertEquals(
        "Invalid kzgProof size for versionId 0, expected 1 but got 128", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenProofWrongSize_V1() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BlobProofBundle(
                    BlobType.KZG_CELL_PROOFS, blob, kzgCommitment, kzgProofs, versionedHash));
    assertEquals(
        "Invalid kzgProof size for versionId 1, expected 128 but got 1", exception.getMessage());
  }

  @Test
  void shouldConvertToVersion1() {
    BlobTestFixture blobTestFixture = new BlobTestFixture();
    BlobsWithCommitments bwc = blobTestFixture.createBlobsWithCommitments(2);
    assertThat(bwc.getBlobType()).isEqualTo(BlobType.KZG_PROOF);

    BlobsWithCommitments blobsWithCommitments = CKZG4844Helper.convertToVersion1(bwc);
    assertThat(blobsWithCommitments.getBlobType()).isEqualTo(BlobType.KZG_CELL_PROOFS);

    boolean isValid = CKZG4844Helper.verify4844Kzg(blobsWithCommitments);
    assertTrue(isValid, "KZG proof verification should be valid");
  }
}
