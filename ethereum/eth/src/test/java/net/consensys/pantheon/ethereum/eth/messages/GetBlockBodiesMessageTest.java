package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
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

/** Tests for {@link GetBlockBodiesMessage}. */
public final class GetBlockBodiesMessageTest {

  @Test
  public void getBlockBodiesRoundTrip() throws IOException {
    final List<Hash> hashes = new ArrayList<>();
    final ByteBuffer buffer =
        ByteBuffer.wrap(Resources.toByteArray(Resources.getResource("50.blocks")));
    for (int i = 0; i < 50; ++i) {
      final byte[] block = new byte[RlpUtils.decodeLength(buffer, 0)];
      buffer.get(block);
      buffer.compact().position(0);
      final RLPInput oneBlock = new BytesValueRLPInput(BytesValue.wrap(block), false);
      oneBlock.enterList();
      hashes.add(BlockHeader.readFrom(oneBlock, MainnetBlockHashFunction::createHash).getHash());
      // We don't care about the bodies, just the headers
      oneBlock.skipNext();
      oneBlock.skipNext();
    }
    final MessageData initialMessage = GetBlockBodiesMessage.create(hashes);
    final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
    initialMessage.writeTo(rawBuffer);
    final MessageData raw = new RawMessage(EthPV62.GET_BLOCK_BODIES, rawBuffer);
    final GetBlockBodiesMessage message = GetBlockBodiesMessage.readFrom(raw);
    try {
      final Iterator<Hash> readHeaders = message.hashes().iterator();
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
