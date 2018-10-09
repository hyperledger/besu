package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.ethereum.p2p.NetworkMemoryPool;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.wire.RawMessage;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPInput;
import net.consensys.pantheon.ethereum.rlp.RLPInput;
import net.consensys.pantheon.ethereum.rlp.RlpUtils;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.io.Resources;
import io.netty.buffer.ByteBuf;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Tests for {@link NewBlockHashesMessage}. */
public final class NewBlockHashesMessageTest {

  @Test
  public void blockHeadersRoundTrip() throws IOException {
    final List<NewBlockHashesMessage.NewBlockHash> hashes = new ArrayList<>();
    final ByteBuffer buffer =
        ByteBuffer.wrap(Resources.toByteArray(Resources.getResource("50.blocks")));
    for (int i = 0; i < 50; ++i) {
      final byte[] block = new byte[RlpUtils.decodeLength(buffer, 0)];
      buffer.get(block);
      buffer.compact().position(0);
      final RLPInput oneBlock = new BytesValueRLPInput(BytesValue.wrap(block), false);
      oneBlock.enterList();
      final BlockHeader header =
          BlockHeader.readFrom(oneBlock, MainnetBlockHashFunction::createHash);
      hashes.add(new NewBlockHashesMessage.NewBlockHash(header.getHash(), header.getNumber()));
      // We don't care about the bodies, just the header hashes
      oneBlock.skipNext();
      oneBlock.skipNext();
    }
    final MessageData initialMessage = NewBlockHashesMessage.create(hashes);
    final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
    initialMessage.writeTo(rawBuffer);
    final MessageData raw = new RawMessage(EthPV62.NEW_BLOCK_HASHES, rawBuffer);
    final NewBlockHashesMessage message = NewBlockHashesMessage.readFrom(raw);
    try {
      final Iterator<NewBlockHashesMessage.NewBlockHash> readHeaders = message.getNewHashes();
      for (int i = 0; i < 50; ++i) {
        Assertions.assertThat(readHeaders.next()).isEqualTo(hashes.get(i));
      }
    } finally {
      message.release();
      initialMessage.release();
      raw.release();
    }
  }
}
