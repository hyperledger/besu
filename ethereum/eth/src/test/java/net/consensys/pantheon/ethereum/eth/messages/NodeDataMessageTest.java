package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.p2p.NetworkMemoryPool;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.wire.RawMessage;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.netty.buffer.ByteBuf;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class NodeDataMessageTest {

  @Test
  public void roundTripTest() throws IOException {
    // Generate some data
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final List<BytesValue> nodeData = new ArrayList<>();
    final int nodeCount = 20;
    for (int i = 0; i < nodeCount; ++i) {
      nodeData.add(gen.bytesValue());
    }

    // Perform round-trip transformation
    // Create specific message, copy it to a generic message, then read back into a specific format
    final MessageData initialMessage = NodeDataMessage.create(nodeData);
    final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
    initialMessage.writeTo(rawBuffer);
    final MessageData raw = new RawMessage(EthPV63.NODE_DATA, rawBuffer);
    final NodeDataMessage message = NodeDataMessage.readFrom(raw);

    // Read data back out after round trip and check they match originals.
    try {
      final Iterator<BytesValue> readData = message.nodeData().iterator();
      for (int i = 0; i < nodeCount; ++i) {
        Assertions.assertThat(readData.next()).isEqualTo(nodeData.get(i));
      }
      Assertions.assertThat(readData.hasNext()).isFalse();
    } finally {
      message.release();
      initialMessage.release();
      raw.release();
    }
  }
}
