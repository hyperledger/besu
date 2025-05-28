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
package org.hyperledger.besu.ethereum.eth.messages;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class BlockRangeUpdateMessageTest {

  @Test
  public void blockRangeUpdateMessageSerializationDeserialization() {
    final long earliestBlockNumber = 10;
    final long latestBlockNumber = 20;
    final Hash latestBlockHash = Hash.wrap(Bytes32.fromHexString("0x0A"));

    final BlockRangeUpdateMessage msg =
        BlockRangeUpdateMessage.create(earliestBlockNumber, latestBlockNumber, latestBlockHash);
    assertThat(msg.getCode()).isEqualTo(EthProtocolMessages.BLOCK_RANGE_UPDATE);
    assertThat(msg.getEarliestBlockNumber()).isEqualTo(earliestBlockNumber);
    assertThat(msg.getLatestBlockNumber()).isEqualTo(latestBlockNumber);
    assertThat(msg.getBlockHash()).isEqualTo(latestBlockHash);
  }

  @Test
  public void rawMessageConversionToBlockRangeUpdateMessage() {
    final long earliestBlockNumber = 10;
    final long latestBlockNumber = 20;
    final Hash latestBlockHash = Hash.wrap(Bytes32.fromHexString("0x0A"));

    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    tmp.writeLongScalar(earliestBlockNumber);
    tmp.writeLongScalar(latestBlockNumber);
    tmp.writeBytes(latestBlockHash);
    tmp.endList();

    final RawMessage rawMsg = new RawMessage(EthProtocolMessages.BLOCK_RANGE_UPDATE, tmp.encoded());
    final BlockRangeUpdateMessage msg = BlockRangeUpdateMessage.readFrom(rawMsg);
    assertThat(msg.getCode()).isEqualTo(EthProtocolMessages.BLOCK_RANGE_UPDATE);
    assertThat(msg.getEarliestBlockNumber()).isEqualTo(earliestBlockNumber);
    assertThat(msg.getLatestBlockNumber()).isEqualTo(latestBlockNumber);
    assertThat(msg.getBlockHash()).isEqualTo(latestBlockHash);
  }
}
