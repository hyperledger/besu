/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.proof;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;

public class StorageEntryProof {

  private final UInt256 key;

  private final UInt256 value;

  private final List<BytesValue> storageProof;

  public StorageEntryProof(
      final UInt256 key, final UInt256 value, final List<BytesValue> storageProof) {
    this.key = key;
    this.value = value;
    this.storageProof = storageProof;
  }

  @JsonGetter(value = "key")
  public String getKey() {
    return key.getBytes().toString();
  }

  @JsonGetter(value = "value")
  public String getValue() {
    return Quantity.create(value);
  }

  @JsonGetter(value = "proof")
  public List<String> getStorageProof() {
    return storageProof.stream().map(BytesValue::toString).collect(Collectors.toList());
  }
}
