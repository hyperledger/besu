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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.KZGProof;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder.Decoder;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.List;

public class BlobNetworkPackageDecoder implements Decoder {
  @Override
  public Transaction decode(final RLPInput input) {
    input.enterList();
    final Transaction.Builder builder = Transaction.builder();
    input.enterList();
    BlobTransactionDecoder.readTransactionPayloadInner(builder, input);
    input.leaveList();
    List<Blob> blobs = input.readList(Blob::readFrom);
    List<KZGCommitment> commitments = input.readList(KZGCommitment::readFrom);
    List<KZGProof> proofs = input.readList(KZGProof::readFrom);
    input.leaveList();
    builder.kzgBlobs(commitments, blobs, proofs);
    return builder.build();
  }
}
