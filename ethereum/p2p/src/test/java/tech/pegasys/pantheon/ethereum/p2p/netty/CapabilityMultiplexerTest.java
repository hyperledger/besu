package net.consensys.pantheon.ethereum.p2p.netty;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.p2p.NetworkMemoryPool;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.netty.CapabilityMultiplexer.ProtocolMessage;
import net.consensys.pantheon.ethereum.p2p.wire.Capability;
import net.consensys.pantheon.ethereum.p2p.wire.RawMessage;
import net.consensys.pantheon.ethereum.p2p.wire.SubProtocol;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.netty.buffer.ByteBuf;
import org.junit.Test;

public class CapabilityMultiplexerTest {

  @Test
  public void multiplexer() {
    // Set up some capabilities
    final Capability eth61 = Capability.create("eth", 61);
    final Capability eth62 = Capability.create("eth", 62);
    final Capability eth63 = Capability.create("eth", 63);
    final Capability shh1 = Capability.create("shh", 1);
    final Capability shh2 = Capability.create("shh", 2);
    final Capability bzz1 = Capability.create("bzz", 1);
    final Capability fake = Capability.create("bla", 10);

    // Define protocols with message spaces
    final List<SubProtocol> subProtocols =
        Arrays.asList(
            subProtocol(eth61.getName(), 8),
            subProtocol(shh1.getName(), 11),
            subProtocol(bzz1.getName(), 25));

    // Calculate capabilities
    final List<Capability> capSetA = Arrays.asList(eth61, eth62, bzz1, shh2);
    final List<Capability> capSetB = Arrays.asList(eth61, eth62, eth63, bzz1, shh1, fake);
    final CapabilityMultiplexer multiplexerA =
        new CapabilityMultiplexer(subProtocols, capSetA, capSetB);
    final CapabilityMultiplexer multiplexerB =
        new CapabilityMultiplexer(subProtocols, capSetB, capSetA);

    // Check expected overlap
    final Set<Capability> expectedCaps = new HashSet<>(Arrays.asList(eth62, bzz1));
    assertThat(multiplexerA.getAgreedCapabilities()).isEqualTo(expectedCaps);
    assertThat(multiplexerB.getAgreedCapabilities()).isEqualTo(expectedCaps);

    // Multiplex a message and check the value
    final ByteBuf ethData = NetworkMemoryPool.allocate(5);
    ethData.writeBytes(new byte[] {1, 2, 3, 4, 5});
    final int ethCode = 1;
    final MessageData ethMessage = new RawMessage(ethCode, ethData);
    // Check offset
    final int expectedOffset = CapabilityMultiplexer.WIRE_PROTOCOL_MESSAGE_SPACE + 25;
    assertThat(multiplexerA.multiplex(eth62, ethMessage).getCode())
        .isEqualTo(ethCode + expectedOffset);
    assertThat(multiplexerB.multiplex(eth62, ethMessage).getCode())
        .isEqualTo(ethCode + expectedOffset);
    // Check data is unchanged
    final ByteBuf multiplexedData = NetworkMemoryPool.allocate(ethMessage.getSize());
    multiplexerA.multiplex(eth62, ethMessage).writeTo(multiplexedData);
    assertThat(multiplexedData).isEqualTo(ethData);

    // Demultiplex and check value
    final MessageData multiplexedEthMessage = new RawMessage(ethCode + expectedOffset, ethData);
    ProtocolMessage demultiplexed = multiplexerA.demultiplex(multiplexedEthMessage);
    final ByteBuf demultiplexedData = NetworkMemoryPool.allocate(ethMessage.getSize());
    demultiplexed.getMessage().writeTo(demultiplexedData);
    // Check returned result
    assertThat(demultiplexed.getMessage().getCode()).isEqualTo(ethCode);
    assertThat(demultiplexed.getCapability()).isEqualTo(eth62);
    assertThat(demultiplexedData).isEqualTo(ethData);
    demultiplexed = multiplexerB.demultiplex(multiplexedEthMessage);
    assertThat(demultiplexed.getMessage().getCode()).isEqualTo(ethCode);
    assertThat(demultiplexed.getCapability()).isEqualTo(eth62);
    assertThat(demultiplexedData).isEqualTo(ethData);
  }

  private SubProtocol subProtocol(final String name, final int messageSpace) {
    return new SubProtocol() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public int messageSpace(final int protocolVersion) {
        return messageSpace;
      }

      @Override
      public boolean isValidMessageCode(final int protocolVersion, final int code) {
        return true;
      }
    };
  }
}
