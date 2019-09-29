/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.privacy.storage;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.ArrayList;
import java.util.List;

public class PrivateBlockMetadata {

  public static PrivateBlockMetadata empty() {
    return new PrivateBlockMetadata(new ArrayList<>());
  }

  private final List<PrivateTransactionMetadata> privateTransactionMetadataList;

  public PrivateBlockMetadata(
      final List<PrivateTransactionMetadata> privateTransactionMetadataList) {
    this.privateTransactionMetadataList = privateTransactionMetadataList;
  }

  public List<PrivateTransactionMetadata> getPrivateTransactionMetadataList() {
    return privateTransactionMetadataList;
  }

  public void addPrivateTransactionMetadata(
      final PrivateTransactionMetadata privateTransactionMetadata) {
    privateTransactionMetadataList.add(privateTransactionMetadata);
  }

  public void writeTo(final RLPOutput out) {
    out.writeList(privateTransactionMetadataList, PrivateTransactionMetadata::writeTo);
  }

  public static PrivateBlockMetadata readFrom(final RLPInput in) {
    final List<PrivateTransactionMetadata> privateTransactionMetadataList =
        in.readList(PrivateTransactionMetadata::readFrom);
    return new PrivateBlockMetadata(privateTransactionMetadataList);
  }
}
