package org.hyperledger.besu.ethereum.eth.manager;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class RequestId {
  public static MessageData wrapRequestId(final long requestId, final MessageData messageData) {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    rlpOutput.startList();
    rlpOutput.writeLongScalar(requestId);
    rlpOutput.writeRaw(messageData.getData());
    rlpOutput.endList();
    return new RawMessage(messageData.getCode(), rlpOutput.encoded());
  }

  static Map.Entry<Long, MessageData> unwrapRequestId(final MessageData messageData) {
    final RLPInput messageDataRLP = RLP.input(messageData.getData());
    messageDataRLP.enterList();
    final long requestId = messageDataRLP.readLongScalar();
    final Bytes unwrappedMessageData = messageDataRLP.readBytes();
    messageDataRLP.leaveList();

    return new AbstractMap.SimpleImmutableEntry<>(
        requestId, new RawMessage(messageData.getCode(), unwrappedMessageData));
  }
}
