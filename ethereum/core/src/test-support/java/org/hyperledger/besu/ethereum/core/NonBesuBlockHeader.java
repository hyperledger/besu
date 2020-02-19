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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.Hash;
import org.hyperledger.besu.plugin.data.Quantity;

import org.apache.tuweni.bytes.Bytes;

public class NonBesuBlockHeader implements BlockHeader {

  final Hash blockHash;
  final Bytes extraData;

  public NonBesuBlockHeader(final Hash blockHash, final Bytes extraData) {
    this.blockHash = blockHash;
    this.extraData = extraData;
  }

  @Override
  public Hash getParentHash() {
    return null;
  }

  @Override
  public Hash getOmmersHash() {
    return null;
  }

  @Override
  public Address getCoinbase() {
    return null;
  }

  @Override
  public Hash getStateRoot() {
    return null;
  }

  @Override
  public Hash getTransactionsRoot() {
    return null;
  }

  @Override
  public Hash getReceiptsRoot() {
    return null;
  }

  @Override
  public Bytes getLogsBloom() {
    return null;
  }

  @Override
  public Quantity getDifficulty() {
    return null;
  }

  @Override
  public long getNumber() {
    return 0;
  }

  @Override
  public long getGasLimit() {
    return 0;
  }

  @Override
  public long getGasUsed() {
    return 0;
  }

  @Override
  public long getTimestamp() {
    return 0;
  }

  @Override
  public Bytes getExtraData() {
    return extraData;
  }

  @Override
  public Hash getMixHash() {
    return null;
  }

  @Override
  public long getNonce() {
    return 0;
  }

  @Override
  public Hash getBlockHash() {
    return blockHash;
  }
}
