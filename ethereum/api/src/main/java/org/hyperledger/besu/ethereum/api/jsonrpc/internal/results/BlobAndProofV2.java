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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The result of the eth_getBlobAndProofV2 JSON-RPC method contains an array of BlobAndProofV2.
 * BlobAndProofV2 contains the blob data and a list of kzg proof for the cells.
 */
@JsonPropertyOrder({"blob", "proofs"})
public class BlobAndProofV2 {

  private final String blob;

  private final List<String> cellProofs;

  public BlobAndProofV2(final String blob, final List<String> cellProofs) {
    this.blob = blob;
    this.cellProofs = cellProofs;
  }

  public List<String> getProofs() {
    return cellProofs;
  }

  public String getBlob() {
    return blob;
  }
}
