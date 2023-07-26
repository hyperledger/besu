/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.KZGProof;
import org.hyperledger.besu.datatypes.VersionedHash;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.digests.SHA256Digest;

public class BlobTestFixture {

  public BlobTestFixture() {
    try {
      CKZG4844JNI.loadNativeLibrary(CKZG4844JNI.Preset.MAINNET);
      CKZG4844JNI.loadTrustedSetupFromResource(
          "/kzg-trusted-setups/mainnet.txt", BlobTestFixture.class);

    } catch (Exception e) {
      fail("Failed to compute commitment", e);
    }
  }

  record BlobTriplet(
      Blob blob, KZGCommitment kzgCommitment, KZGProof kzgProof, VersionedHash versionedHash) {}
  ;

  public BlobTriplet createBlobTriplet() {
    byte[] rawMaterial = {};
    try (InputStream readme =
        BlobTestFixture.class.getResourceAsStream(
            "/org/hyperledger/besu/ethereum/core/encoding/BlobDataFixture.bin")) {
      rawMaterial = readme.readAllBytes();
    } catch (IOException e) {
      fail("Failed to read blob file", e);
    }

    Bytes commitment = Bytes.wrap(CKZG4844JNI.blobToKzgCommitment(rawMaterial));

    assertThat(commitment.size()).isEqualTo(48);
    Bytes proof = Bytes.wrap(CKZG4844JNI.computeBlobKzgProof(rawMaterial, commitment.toArray()));
    VersionedHash versionedHash = hashCommitment(new KZGCommitment(commitment));
    return new BlobTriplet(
        new Blob(Bytes.wrap(rawMaterial)),
        new KZGCommitment(commitment),
        new KZGProof(proof),
        versionedHash);
  }

  public BlobsWithCommitments createBlobsWithCommitments(final int blobCount) {
    List<Blob> blobs = new ArrayList<>();
    List<KZGCommitment> commitments = new ArrayList<>();
    List<KZGProof> proofs = new ArrayList<>();
    List<VersionedHash> versionedHashes = new ArrayList<>();
    for (int i = 0; i < blobCount; i++) {
      BlobTriplet blobTriplet = createBlobTriplet();
      blobs.add(blobTriplet.blob());
      commitments.add(blobTriplet.kzgCommitment());
      proofs.add(blobTriplet.kzgProof());
      versionedHashes.add(blobTriplet.versionedHash());
    }
    return new BlobsWithCommitments(commitments, blobs, proofs, versionedHashes);
  }

  private VersionedHash hashCommitment(final KZGCommitment commitment) {
    final SHA256Digest digest = new SHA256Digest();
    digest.update(commitment.getData().toArrayUnsafe(), 0, commitment.getData().size());

    final byte[] dig = new byte[digest.getDigestSize()];

    digest.doFinal(dig, 0);

    dig[0] = VersionedHash.SHA256_VERSION_ID;
    return new VersionedHash(Bytes32.wrap(dig));
  }
}
