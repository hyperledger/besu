/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.p2p.rlpx.wire;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.CapabilityMultiplexer.ProtocolMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

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
            MockSubProtocol.create(eth61.getName(), 8),
            MockSubProtocol.create(shh1.getName(), 11),
            MockSubProtocol.create(bzz1.getName(), 25));

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
    final Bytes ethData = Bytes.of(1, 2, 3, 4, 5);
    final int ethCode = 1;
    final MessageData ethMessage = new RawMessage(ethCode, ethData);
    // Check offset
    final int expectedOffset = CapabilityMultiplexer.WIRE_PROTOCOL_MESSAGE_SPACE + 25;
    assertThat(multiplexerA.multiplex(eth62, ethMessage).getCode())
        .isEqualTo(ethCode + expectedOffset);
    assertThat(multiplexerB.multiplex(eth62, ethMessage).getCode())
        .isEqualTo(ethCode + expectedOffset);
    // Check data is unchanged
    final Bytes multiplexedData = multiplexerA.multiplex(eth62, ethMessage).getData();
    assertThat(multiplexedData).isEqualTo(ethData);

    // Demultiplex and check value
    final MessageData multiplexedEthMessage = new RawMessage(ethCode + expectedOffset, ethData);
    ProtocolMessage demultiplexed = multiplexerA.demultiplex(multiplexedEthMessage);
    final Bytes demultiplexedData = ethMessage.getData();
    // Check returned result
    assertThat(demultiplexed.getMessage().getCode()).isEqualTo(ethCode);
    assertThat(demultiplexed.getCapability()).isEqualTo(eth62);
    assertThat(demultiplexedData).isEqualTo(ethData);
    demultiplexed = multiplexerB.demultiplex(multiplexedEthMessage);
    assertThat(demultiplexed.getMessage().getCode()).isEqualTo(ethCode);
    assertThat(demultiplexed.getCapability()).isEqualTo(eth62);
    assertThat(demultiplexedData).isEqualTo(ethData);
  }
}
