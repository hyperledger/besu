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

import static org.hyperledger.besu.datatypes.VersionedHash.DEFAULT_VERSIONED_HASH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hyperledger.besu.datatypes.VersionedHash;

import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;

public class BlobProofBundleTest {

  final Blob blob = new Blob(Bytes.EMPTY);
  final KZGCommitment kzgCommitment = new KZGCommitment(Bytes48.ZERO);
  final VersionedHash versionedHash = DEFAULT_VERSIONED_HASH;
  List<KZGProof> kzgProofs = List.of(new KZGProof(Bytes48.ZERO));
  List<KZGProof> kzgCellProofs =
      Collections.nCopies(BlobProofBundle.CELL_PROOFS_PER_BLOB, new KZGProof(Bytes48.ZERO));

  @Test
  void shouldSucceedWithValidInputsV0() {
    BlobProofBundle bundle =
        BlobProofBundle.builder()
            .versionId(0)
            .blob(blob)
            .kzgCommitment(kzgCommitment)
            .versionedHash(versionedHash)
            .kzgProof(kzgProofs)
            .build();

    assertEquals(0, bundle.versionId());
    assertEquals(blob, bundle.blob());
    assertEquals(kzgCommitment, bundle.kzgCommitment());
    assertEquals(versionedHash, bundle.versionedHash());
    assertEquals(kzgProofs, bundle.kzgProof());
  }

  @Test
  void shouldSucceedWithValidInputsV1() {
    BlobProofBundle bundle =
        BlobProofBundle.builder()
            .versionId(1)
            .blob(blob)
            .kzgCommitment(kzgCommitment)
            .versionedHash(versionedHash)
            .kzgProof(kzgCellProofs)
            .build();
    assertEquals(1, bundle.versionId());
    assertEquals(blob, bundle.blob());
    assertEquals(kzgCommitment, bundle.kzgCommitment());
    assertEquals(versionedHash, bundle.versionedHash());
    assertEquals(kzgCellProofs, bundle.kzgProof());
  }

  @Test
  void shouldThrowsExceptionWhenKzgCommitmentIsNull() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                BlobProofBundle.builder()
                    .versionId(0)
                    .blob(blob)
                    .versionedHash(versionedHash)
                    .kzgProof(kzgProofs)
                    .build());
    assertEquals("kzgCommitment must not be empty", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenVersionedHashIsNull() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              BlobProofBundle.builder()
                  .versionId(0)
                  .blob(blob)
                  .kzgCommitment(kzgCommitment)
                  .kzgProof(kzgProofs)
                  .build();
            });
    assertEquals("versionedHash must not be empty", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenBlobIsNull() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                BlobProofBundle.builder()
                    .versionId(0)
                    .kzgCommitment(kzgCommitment)
                    .versionedHash(versionedHash)
                    .build());
    assertEquals("blob must not be empty", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenKzgCellProofNotEmpty_V0() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                BlobProofBundle.builder()
                    .versionId(BlobProofBundle.VERSION_0_KZG_PROOFS)
                    .blob(blob)
                    .kzgCommitment(kzgCommitment)
                    .versionedHash(versionedHash)
                    .build());
    assertEquals("kzgProof must not be empty", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenKzgProofNotEmpty_V0() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                BlobProofBundle.builder()
                    .versionId(BlobProofBundle.VERSION_0_KZG_PROOFS)
                    .blob(blob)
                    .kzgCommitment(kzgCommitment)
                    .versionedHash(versionedHash)
                    .build());
    assertEquals("kzgProof must not be empty", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenKzgProofNotEmpty_V1() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                BlobProofBundle.builder()
                    .versionId(BlobProofBundle.VERSION_1_KZG_CELL_PROOFS)
                    .blob(blob)
                    .kzgCommitment(kzgCommitment)
                    .versionedHash(versionedHash)
                    .build());
    assertEquals("kzgProof must not be empty", exception.getMessage());
  }

  @Test
  void shouldThrowsExceptionWhenKzgCellProofNotEmpty_V1() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                BlobProofBundle.builder()
                    .versionId(BlobProofBundle.VERSION_1_KZG_CELL_PROOFS)
                    .blob(blob)
                    .kzgCommitment(kzgCommitment)
                    .versionedHash(versionedHash)
                    .build());
    assertEquals("kzgProof must not be empty", exception.getMessage());
  }
}
