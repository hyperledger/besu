package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.core.Hash;
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

public final class GetReceiptsMessageTest {

  @Test
  public void roundTripTest() throws IOException {
    // Generate some hashes
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final List<Hash> hashes = new ArrayList<>();
    final int hashCount = 20;
    for (int i = 0; i < hashCount; ++i) {
      hashes.add(gen.hash());
    }

    // Perform round-trip transformation
    // Create GetReceipts message, copy it to a generic message, then read back into a GetReceipts
    // message
    final MessageData initialMessage = GetReceiptsMessage.create(hashes);
    final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
    initialMessage.writeTo(rawBuffer);
    final MessageData raw = new RawMessage(EthPV63.GET_RECEIPTS, rawBuffer);
    final GetReceiptsMessage message = GetReceiptsMessage.readFrom(raw);

    // Read hashes back out after round trip and check they match originals.
    try {
      final Iterator<Hash> readData = message.hashes().iterator();
      for (int i = 0; i < hashCount; ++i) {
        Assertions.assertThat(readData.next()).isEqualTo(hashes.get(i));
      }
      Assertions.assertThat(readData.hasNext()).isFalse();
    } finally {
      message.release();
      initialMessage.release();
      raw.release();
    }
  }
}
