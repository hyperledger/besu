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
package org.hyperledger.besu.ethereum.core.kzg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.datatypes.BlobType.KZG_PROOF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.BlobType;
import org.hyperledger.besu.datatypes.VersionedHash;

import java.util.List;

import org.junit.jupiter.api.Test;

public class BlobsWithCommitmentsTest {
  List<Blob> blobs = List.of(mock(Blob.class), mock(Blob.class));
  List<KZGCommitment> kzgCommitments =
      List.of(mock(KZGCommitment.class), mock(KZGCommitment.class));
  List<KZGProof> kzgProofs = List.of(mock(KZGProof.class), mock(KZGProof.class));
  List<VersionedHash> versionedHashes =
      List.of(mock(VersionedHash.class), mock(VersionedHash.class));

  @Test
  public void blobsWithCommitmentsMustHaveAtLeastOneBlob() {
    String actualMessage =
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BlobsWithCommitments(KZG_PROOF, List.of(), List.of(), List.of(), List.of()))
            .getMessage();
    final String expectedMessage =
        "There needs to be a minimum of one blob in a blob transaction with commitments";
    assertThat(actualMessage).isEqualTo(expectedMessage);
  }

  @Test
  public void shouldThrowExceptionWhenKzgCommitmentsSizeIsInvalid_V0() {
    List<KZGCommitment> wrongCommitments =
        List.of(mock(KZGCommitment.class)); // Only one commitment instead of two
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BlobsWithCommitments(
                    KZG_PROOF, wrongCommitments, blobs, kzgProofs, versionedHashes));

    assertEquals("Invalid number of kzgCommitments, expected 2, got 1", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenVersionedHashSizeIsInvalid_V0() {
    List<VersionedHash> wrongVersionedHashes =
        List.of(mock(VersionedHash.class)); // Only one versioned hash instead of two
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BlobsWithCommitments(
                    KZG_PROOF, kzgCommitments, blobs, kzgProofs, wrongVersionedHashes));
    assertEquals("Invalid number of versionedHashes, expected 2, got 1", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenKzgProofsSizeIsInvalid_V0() {
    List<KZGProof> wrongKzgProofs = List.of(mock(KZGProof.class)); // Only one proof instead of two
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BlobsWithCommitments(
                    KZG_PROOF, kzgCommitments, blobs, wrongKzgProofs, versionedHashes));
    String error = String.format("Invalid number of proofs (%s), expected 2, got 1", KZG_PROOF);
    assertEquals(error, exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenKzgCommitmentsSizeIsInvalid_V1() {
    List<KZGCommitment> wrongCommitments = List.of(mock(KZGCommitment.class));
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BlobsWithCommitments(
                    KZG_PROOF, wrongCommitments, blobs, kzgProofs, versionedHashes));
    assertEquals("Invalid number of kzgCommitments, expected 2, got 1", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenVersionedHashSizeIsInvalid_V1() {
    List<VersionedHash> wrongVersionedHashes = List.of(mock(VersionedHash.class));
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BlobsWithCommitments(
                    BlobType.KZG_CELL_PROOFS,
                    kzgCommitments,
                    blobs,
                    kzgProofs,
                    wrongVersionedHashes));
    String error = String.format("Invalid number of versionedHashes, expected 2, got 1");
    assertEquals(error, exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenProofsSizeIsInvalid_V1() {
    List<KZGProof> wrongKzgProofs =
        List.of(mock(KZGProof.class), mock(KZGProof.class)); // Only two proofs instead of 256
    BlobType blobType = BlobType.KZG_CELL_PROOFS;
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BlobsWithCommitments(
                    blobType, kzgCommitments, blobs, wrongKzgProofs, versionedHashes));
    String error = String.format("Invalid number of proofs (%s), expected 256, got 2", blobType);
    assertEquals(error, exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenBlobProofBundlesHaveDifferentTypes() {
    List<BlobProofBundle> invalidBundles =
        List.of(
            mockBlobProofBundle(BlobType.KZG_PROOF), mockBlobProofBundle(BlobType.KZG_CELL_PROOFS));
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> new BlobsWithCommitments(invalidBundles));
    assertEquals("BlobProofBundles must have the same BlobType", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenBlobProofBundlesListIsEmpty() {
    List<BlobProofBundle> emptyBundles = List.of();
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new BlobsWithCommitments(emptyBundles));
    assertEquals("BlobProofBundles list cannot be empty", exception.getMessage());
  }

  private BlobProofBundle mockBlobProofBundle(final BlobType blobType) {
    BlobProofBundle bundle = mock(BlobProofBundle.class);
    when(bundle.getBlobType()).thenReturn(blobType);
    return bundle;
  }
}
