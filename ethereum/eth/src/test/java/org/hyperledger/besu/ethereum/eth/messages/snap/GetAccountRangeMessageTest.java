/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.messages.snap;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class GetAccountRangeMessageTest {

  @Test
  public void roundTripTest() {
    final Hash rootHash = Hash.wrap(Bytes32.random());
    final Hash startKeyHash = RangeManager.MIN_RANGE;
    final Hash endKeyHash = RangeManager.MAX_RANGE;

    // Perform round-trip transformation
    final MessageData initialMessage =
        GetAccountRangeMessage.create(rootHash, startKeyHash, endKeyHash);
    final MessageData raw = new RawMessage(SnapV1.GET_ACCOUNT_RANGE, initialMessage.getData());

    final GetAccountRangeMessage message = GetAccountRangeMessage.readFrom(raw);

    // check match originals.
    final GetAccountRangeMessage.Range range = message.range(false);
    Assertions.assertThat(range.worldStateRootHash()).isEqualTo(rootHash);
    Assertions.assertThat(range.startKeyHash()).isEqualTo(startKeyHash);
    Assertions.assertThat(range.responseBytes()).isEqualTo(AbstractSnapMessageData.SIZE_REQUEST);
  }
}
