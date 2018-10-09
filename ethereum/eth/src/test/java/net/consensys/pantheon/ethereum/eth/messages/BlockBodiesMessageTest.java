package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Transaction;
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

/** Tests for {@link BlockBodiesMessage}. */
public final class BlockBodiesMessageTest {

  @Test
  public void blockBodiesRoundTrip() throws IOException {
    final List<BlockBody> bodies = new ArrayList<>();
    final ByteBuffer buffer =
        ByteBuffer.wrap(Resources.toByteArray(Resources.getResource("50.blocks")));
    for (int i = 0; i < 50; ++i) {
      final byte[] block = new byte[RlpUtils.decodeLength(buffer, 0)];
      buffer.get(block);
      buffer.compact().position(0);
      final RLPInput oneBlock = new BytesValueRLPInput(BytesValue.wrap(block), false);
      oneBlock.enterList();
      // We don't care about the header, just the body
      oneBlock.skipNext();
      bodies.add(
          // We know the test data to only contain Frontier blocks
          new BlockBody(
              oneBlock.readList(Transaction::readFrom),
              oneBlock.readList(
                  rlp -> BlockHeader.readFrom(rlp, MainnetBlockHashFunction::createHash))));
    }
    final MessageData initialMessage = BlockBodiesMessage.create(bodies);
    final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
    initialMessage.writeTo(rawBuffer);
    final MessageData raw = new RawMessage(EthPV62.BLOCK_BODIES, rawBuffer);
    final BlockBodiesMessage message = BlockBodiesMessage.readFrom(raw);
    try {
      final Iterator<BlockBody> readBodies =
          message.bodies(DevelopmentProtocolSchedule.create(new JsonObject())).iterator();
      for (int i = 0; i < 50; ++i) {
        Assertions.assertThat(readBodies.next()).isEqualTo(bodies.get(i));
      }
    } finally {
      message.release();
      initialMessage.release();
      raw.release();
    }
  }
}
