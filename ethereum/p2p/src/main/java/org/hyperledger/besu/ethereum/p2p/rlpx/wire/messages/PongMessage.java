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
package org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import org.apache.tuweni.bytes.Bytes;

public final class PongMessage extends AbstractMessageData {

  private static final PongMessage INSTANCE = PongMessage.create();

  public static PongMessage get() {
    return INSTANCE;
  }

  private PongMessage(final Bytes data) {
    super(data);
  }

  public static PongMessage create() {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeEmptyList();
    return new PongMessage(out.encoded());
  }

  @Override
  public int getCode() {
    return WireMessageCodes.PONG;
  }
}
