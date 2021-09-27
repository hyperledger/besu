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
package org.hyperledger.besu.ethereum.eth.messages;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Tests for {@link GetAccountRangeMessage}. */
public final class GetAccountRangeMessageTest {

  private static final Bytes VALUE1 = Bytes.of(1);
  private static final Bytes VALUE2 = Bytes.of(2);
  private static final Bytes VALUE3 = Bytes.of(3);
  private static final Hash STATEROOT = Hash.hash(VALUE1);
  private static final Hash START_HASH = Hash.hash(VALUE2);
  private static final Hash END_HASH = Hash.hash(VALUE3);

  @Test
  public void roundTripTest() {

    // Perform round-trip transformation
    // Create GetAccountRangeMessage, copy it to a generic message, then read back into a
    // GetAccountRangeMessage message
    final MessageData initialMessage =
        GetAccountRangeMessage.create(STATEROOT, START_HASH, END_HASH, BigInteger.ONE);
    final MessageData raw = new RawMessage(SnapV1.GET_ACCOUNT_RANGE, initialMessage.getData());
    final GetAccountRangeMessage message = GetAccountRangeMessage.readFrom(raw);

    // Read range back out after round trip and check they match originals.
    final GetAccountRangeMessage.Range range = message.range(false);
    Assertions.assertThat(range.worldStateRootHash().toArray()).isEqualTo(STATEROOT.toArray());
    Assertions.assertThat(range.startKeyHash().toArray()).isEqualTo(START_HASH.toArray());
    Assertions.assertThat(range.endKeyHash().toArray()).isEqualTo(END_HASH.toArray());
    Assertions.assertThat(range.responseBytes()).isEqualTo(BigInteger.ONE);
  }
}
