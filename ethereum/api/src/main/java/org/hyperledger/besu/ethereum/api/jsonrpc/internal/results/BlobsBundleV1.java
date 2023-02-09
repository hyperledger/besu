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

package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.Hash;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"blockHash", "kzgs", "blobs"})
public class BlobsBundleV1 {

  private final String blockHash;

  private final List<String> kzgs;

  private final List<String> blobs;

  public BlobsBundleV1(final Hash blockHash, final List<String> kzgs, final List<String> blobs) {
    this.blockHash = blockHash.toString();
    this.kzgs = kzgs;
    this.blobs = blobs;
  }

  @JsonGetter("blockHash")
  public String getBlockHash() {
    return blockHash;
  }

  @JsonGetter("kzgs")
  public List<String> getKzgs() {
    return kzgs;
  }

  @JsonGetter("blobs")
  public List<String> getBlobs() {
    return blobs;
  }
}
