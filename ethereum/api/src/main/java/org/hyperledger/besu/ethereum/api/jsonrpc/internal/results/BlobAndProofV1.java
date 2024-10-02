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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The result of the eth_getBlobAndProofV1 JSON-RPC method contains an array of BlobAndProofV1.
 * BlobAndProofV1 contains the blob data and the kzg proof for the blob.
 */
@JsonPropertyOrder({"blob", "proof"})
public class BlobAndProofV1 {

  private final String blob;

  private final String proof;

  public BlobAndProofV1(final String blob, final String proof) {
    this.blob = blob;
    this.proof = proof;
  }

  public String getProof() {
    return proof;
  }

  public String getBlob() {
    return blob;
  }
}
