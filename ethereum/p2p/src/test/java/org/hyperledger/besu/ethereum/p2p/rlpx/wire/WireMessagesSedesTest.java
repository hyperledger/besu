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

import static io.netty.buffer.ByteBufUtil.decodeHexDump;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

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

    rlp =
        decodeHexDump(
            "f8a305b74e65746865726d696e642f76312e31332e342d302d3365353937326332342d32303232303831312f5836342d4c696e75782f362e302e36e4c5836574683ec5836574683fc58365746840c58365746841c58365746842c5837769748082765db84005c95b2618ba1ca53f0f019d1750d12769267705e46b4dbfb77f73998b21d30973161542a2090bcaa5876e6aed99436009f3f646029bb723b8dff75feec27374");
    // This test contains a hello message from Nethermind. The Capability version of the wit
    // capability is encoded as 0x80, which is an empty string
    assertSedesWorks(rlp);
  }

  private static void assertSedesWorks(final byte[] data) {
    final Bytes input = Bytes.wrap(data);
    final PeerInfo peerInfo = PeerInfo.readFrom(RLP.input(input));

    assertThat(peerInfo.getClientId()).isNotBlank();
    assertThat(peerInfo.getCapabilities()).isNotEmpty();
    assertThat(peerInfo.getNodeId().toArray().length).isEqualTo(64);
    assertThat(peerInfo.getPort()).isGreaterThanOrEqualTo(0);
    assertThat(peerInfo.getVersion()).isEqualTo(5);

    // Re-serialize and check that data matches
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    peerInfo.writeTo(out);
    assertThat(out.encoded()).isEqualTo(input);
  }
}
