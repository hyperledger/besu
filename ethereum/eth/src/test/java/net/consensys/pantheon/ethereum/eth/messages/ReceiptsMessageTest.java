package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.core.TransactionReceipt;
import net.consensys.pantheon.ethereum.p2p.NetworkMemoryPool;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.wire.RawMessage;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.netty.buffer.ByteBuf;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class ReceiptsMessageTest {

  @Test
  public void roundTripTest() throws IOException {
    // Generate some data
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final List<List<TransactionReceipt>> receipts = new ArrayList<>();
    final int dataCount = 20;
    final int receiptsPerSet = 3;
    for (int i = 0; i < dataCount; ++i) {
      final List<TransactionReceipt> receiptSet = new ArrayList<>();
      for (int j = 0; j < receiptsPerSet; j++) {
        receiptSet.add(gen.receipt());
      }
      receipts.add(receiptSet);
    }

    // Perform round-trip transformation
    // Create specific message, copy it to a generic message, then read back into a specific format
    final MessageData initialMessage = ReceiptsMessage.create(receipts);
    final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
    initialMessage.writeTo(rawBuffer);
    final MessageData raw = new RawMessage(EthPV63.RECEIPTS, rawBuffer);
    final ReceiptsMessage message = ReceiptsMessage.readFrom(raw);

    // Read data back out after round trip and check they match originals.
    try {
      final Iterator<List<TransactionReceipt>> readData = message.receipts().iterator();
      for (int i = 0; i < dataCount; ++i) {
        Assertions.assertThat(readData.next()).isEqualTo(receipts.get(i));
      }
      Assertions.assertThat(readData.hasNext()).isFalse();
    } finally {
      message.release();
      initialMessage.release();
      raw.release();
    }
  }
}
