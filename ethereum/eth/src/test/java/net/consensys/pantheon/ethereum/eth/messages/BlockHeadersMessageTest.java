package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.development.DevelopmentProtocolSchedule;
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
import io.vertx.core.json.JsonObject;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Tests for {@link BlockHeadersMessage}. */
public final class BlockHeadersMessageTest {

  @Test
  public void blockHeadersRoundTrip() throws IOException {
    final List<BlockHeader> headers = new ArrayList<>();
    final ByteBuffer buffer =
        ByteBuffer.wrap(Resources.toByteArray(Resources.getResource("50.blocks")));
    for (int i = 0; i < 50; ++i) {
      final byte[] block = new byte[RlpUtils.decodeLength(buffer, 0)];
      buffer.get(block);
      buffer.compact().position(0);
      final RLPInput oneBlock = new BytesValueRLPInput(BytesValue.wrap(block), false);
      oneBlock.enterList();
      headers.add(BlockHeader.readFrom(oneBlock, MainnetBlockHashFunction::createHash));
      // We don't care about the bodies, just the headers
      oneBlock.skipNext();
      oneBlock.skipNext();
    }
    final MessageData initialMessage = BlockHeadersMessage.create(headers);
    final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
    initialMessage.writeTo(rawBuffer);
    final MessageData raw = new RawMessage(EthPV62.BLOCK_HEADERS, rawBuffer);
    final BlockHeadersMessage message = BlockHeadersMessage.readFrom(raw);
    try {
      final Iterator<BlockHeader> readHeaders =
          message.getHeaders(DevelopmentProtocolSchedule.create(new JsonObject()));
      for (int i = 0; i < 50; ++i) {
        Assertions.assertThat(readHeaders.next()).isEqualTo(headers.get(i));
      }
    } finally {
      message.release();
      initialMessage.release();
      raw.release();
    }
  }
}
