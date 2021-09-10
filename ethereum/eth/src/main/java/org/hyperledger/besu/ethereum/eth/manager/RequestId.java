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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;

public class RequestId {
  public static MessageData wrapMessageData(
      final BigInteger requestId, final MessageData messageData) {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    rlpOutput.startList();
    rlpOutput.writeBigIntegerScalar(requestId);
    rlpOutput.writeRaw(messageData.getData());
    rlpOutput.endList();
    return new RawMessage(messageData.getCode(), rlpOutput.encoded());
  }

  static Map.Entry<BigInteger, MessageData> unwrapMessageData(
      final MessageData messageData, final String protocolName) {
    final RLPInput messageDataRLP = RLP.input(messageData.getData());
    messageDataRLP.enterList();
    final BigInteger requestId = messageDataRLP.readBigIntegerScalar();
    final Bytes message;
    if (protocolName.equalsIgnoreCase(EthProtocol.NAME)) {
      message = messageDataRLP.readAsRlp().raw();
    } else { /// SNAP
      message = messageData.getData();
    }
    messageDataRLP.leaveListLenient();
    return new AbstractMap.SimpleImmutableEntry<>(
        requestId, new RawMessage(messageData.getCode(), message));
  }
}
