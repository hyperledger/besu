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
package org.hyperledger.besu.ethereum.eth.messages;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.Collection;

public final class GetNodeDataMessage extends AbstractMessageData {

  public static GetNodeDataMessage readFrom(final MessageData message) {
    if (message instanceof GetNodeDataMessage) {
      return (GetNodeDataMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV63.GET_NODE_DATA) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a GetNodeDataMessage.", code));
    }
    return new GetNodeDataMessage(message.getData());
  }

  public static GetNodeDataMessage create(final Iterable<Hash> hashes) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    hashes.forEach(tmp::writeBytesValue);
    tmp.endList();
    return new GetNodeDataMessage(tmp.encoded());
  }

  private GetNodeDataMessage(final BytesValue data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV63.GET_NODE_DATA;
  }

  public Iterable<Hash> hashes() {
    final RLPInput input = new BytesValueRLPInput(data, false);
    input.enterList();
    final Collection<Hash> hashes = new ArrayList<>();
    while (!input.isEndOfCurrentList()) {
      hashes.add(Hash.wrap(input.readBytes32()));
    }
    input.leaveList();
    return hashes;
  }
}
