package net.consensys.pantheon.ethereum.p2p.wire;

import static io.netty.buffer.ByteBufUtil.decodeHexDump;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.rlp.RLP;
import net.consensys.pantheon.util.bytes.BytesValue;

import io.netty.buffer.ByteBuf;
import org.junit.Test;

public class WireMessagesSedesTest {

  @Test
  public void deserializeHello() {
    byte[] rlp =
        decodeHexDump(
            "f88105af476573632f76302e322e302d737461626c652d61383430646534302"
                + "f6c696e75782d616d6436342f676f312e392e34ccc5836574683fc5836574683e80b84067d910939be40f3"
                + "b35761b0fe3f0de19cb96092be29a0d0c033a1629d3cf270345586679aba8bbda61069532e3ac7551fc3a9"
                + "7766c30037184a5bed48a821861");

    assertSedesWorks(rlp);

    rlp =
        decodeHexDump(
            "f87b05af476574682f76312e372e332d737461626c652d34626233633839642f6c696e"
                + "75782d616d6436342f676f312e392e32c6c5836574683f80b8406a68f89fbfa11ca6dbe13a8c09300047b2"
                + "dd83a6a6580b2559c3c2d87527b83ea8f232ddeed2fff3263949105761ab5d0fe3733046e0e75aaa83cada"
                + "3b1e5d41");

    assertSedesWorks(rlp);
  }

  private static void assertSedesWorks(final byte[] data) {
    final PeerInfo peerInfo = PeerInfo.readFrom(RLP.input(BytesValue.wrap(data)));

    assertThat(peerInfo.getClientId()).isNotBlank();
    assertThat(peerInfo.getCapabilities()).isNotEmpty();
    assertThat(peerInfo.getNodeId().extractArray().length).isEqualTo(64);
    assertThat(peerInfo.getPort()).isGreaterThanOrEqualTo(0);
    assertThat(peerInfo.getVersion()).isEqualTo(5);

    // Re-serialize and check that data matches
    final ByteBuf buffer = peerInfo.toByteBuf();
    final byte[] serialized = new byte[buffer.readableBytes()];
    buffer.getBytes(buffer.readerIndex(), serialized);
    assertThat(serialized).isEqualTo(data);
  }
}
