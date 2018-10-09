package net.consensys.pantheon.ethereum.eth.messages;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.eth.EthProtocol.EthVersion;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.Random;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

public class StatusMessageTest {

  @Test
  public void getters() {
    final int version = EthVersion.V62;
    final int networkId = 1;
    final UInt256 td = UInt256.of(1000L);
    final Hash bestHash = randHash(1L);
    final Hash genesisHash = randHash(2L);

    final StatusMessage msg = StatusMessage.create(version, networkId, td, bestHash, genesisHash);

    assertThat(msg.protocolVersion()).isEqualTo(version);
    assertThat(msg.networkId()).isEqualTo(networkId);
    assertThat(msg.totalDifficulty()).isEqualTo(td);
    assertThat(msg.bestHash()).isEqualTo(bestHash);
    assertThat(msg.genesisHash()).isEqualTo(genesisHash);
  }

  @Test
  public void serializeDeserialize() {
    final int version = EthVersion.V62;
    final int networkId = 1;
    final UInt256 td = UInt256.of(1000L);
    final Hash bestHash = randHash(1L);
    final Hash genesisHash = randHash(2L);

    final MessageData msg = StatusMessage.create(version, networkId, td, bestHash, genesisHash);

    // Make a message copy from serialized data and check deserialized results
    final ByteBuf buffer = Unpooled.buffer(msg.getSize(), msg.getSize());
    msg.writeTo(buffer);
    final StatusMessage copy = new StatusMessage(buffer);

    assertThat(copy.protocolVersion()).isEqualTo(version);
    assertThat(copy.networkId()).isEqualTo(networkId);
    assertThat(copy.totalDifficulty()).isEqualTo(td);
    assertThat(copy.bestHash()).isEqualTo(bestHash);
    assertThat(copy.genesisHash()).isEqualTo(genesisHash);
  }

  private Hash randHash(final long seed) {
    final Random random = new Random(seed);
    final byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Hash.wrap(Bytes32.wrap(bytes));
  }
}
