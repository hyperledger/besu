/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.dns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.ethereum.p2p.discovery.discv5.DiscV5TestHelper;

import java.net.InetAddress;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EthereumNodeRecordTest {

  @ParameterizedTest
  @MethodSource("validEnrNodes")
  void enrIsParsed(
      final String enr,
      final Bytes expectedPublicKey,
      final InetAddress expectedIp,
      final int expectedTcpPort,
      final int expectedUdpPort) {
    final EthereumNodeRecord record = EthereumNodeRecord.fromEnr(enr);
    assertThat(record.publicKey()).isEqualTo(expectedPublicKey);
    assertThat(record.ip().get()).isEqualTo(expectedIp);
    assertThat(record.tcp()).isEqualTo(Optional.of(expectedTcpPort));
    assertThat(record.udp()).isEqualTo(Optional.of(expectedUdpPort));
  }

  @Test
  void ipv6OnlyEnrIsParsed() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecordIpv6(nodeKey, "fd00::1", 30303, 30303);
    final EthereumNodeRecord enr = EthereumNodeRecord.fromNodeRecord(record);

    assertThat(enr.ipV6()).isPresent();
    assertThat(enr.ipV6().get().getHostAddress()).isEqualTo("fd00:0:0:0:0:0:0:1");
    assertThat(enr.tcpV6()).isEqualTo(Optional.of(30303));
    assertThat(enr.udpV6()).isEqualTo(Optional.of(30303));
    assertThat(enr.ip()).isEmpty();
  }

  @Test
  void dualStackEnrIsParsed() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final NodeRecord record =
        DiscV5TestHelper.createSignedDualStackNodeRecord(
            nodeKey, "10.0.0.1", 30303, 30303, "fd00::1", 30304, 30304);
    final EthereumNodeRecord enr = EthereumNodeRecord.fromNodeRecord(record);

    assertThat(enr.ip()).isPresent();
    assertThat(enr.ip().get().getHostAddress()).isEqualTo("10.0.0.1");
    assertThat(enr.tcp()).isEqualTo(Optional.of(30303));
    assertThat(enr.udp()).isEqualTo(Optional.of(30303));

    assertThat(enr.ipV6()).isPresent();
    assertThat(enr.ipV6().get().getHostAddress()).isEqualTo("fd00:0:0:0:0:0:0:1");
    assertThat(enr.tcpV6()).isEqualTo(Optional.of(30304));
    assertThat(enr.udpV6()).isEqualTo(Optional.of(30304));
  }

  @Test
  void constructorThrowsWhenNoIpAddress() {
    assertThatThrownBy(
            () ->
                new EthereumNodeRecord(
                    Bytes.of(1, 2, 3),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no IPv4 or IPv6 address");
  }

  @Test
  void missingUdpFallsBackToTcp() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecordTcpOnly(nodeKey, "10.0.0.1", 30303);
    final EthereumNodeRecord enr = EthereumNodeRecord.fromNodeRecord(record);

    // udp field is not set, so it should fall back to tcp
    assertThat(enr.udp()).isEqualTo(Optional.of(30303));
    assertThat(enr.tcp()).isEqualTo(Optional.of(30303));
  }

  @Test
  void missingUdp6FallsBackToTcp6() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    // Create IPv6-only record, but we need one without udp6. Use dual-stack helper without udp6.
    final NodeRecord record =
        DiscV5TestHelper.createSignedDualStackNodeRecordNoUdp6(
            nodeKey, "10.0.0.1", 30303, 30303, "fd00::1", 30304);
    final EthereumNodeRecord enr = EthereumNodeRecord.fromNodeRecord(record);

    // udp6 not set → falls back to tcp6
    assertThat(enr.tcpV6()).isEqualTo(Optional.of(30304));
    assertThat(enr.udpV6()).isEqualTo(Optional.of(30304));
  }

  @Test
  void missingBothUdpAndTcpReturnsEmpty() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    // NodeRecord with only UDP, no TCP — create via NoTcp helper
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecordNoTcp(nodeKey, "10.0.0.1", 30303);
    final EthereumNodeRecord enr = EthereumNodeRecord.fromNodeRecord(record);

    // tcp is empty (not set)
    assertThat(enr.tcp()).isEmpty();
    // udp is present because it was explicitly set
    assertThat(enr.udp()).isEqualTo(Optional.of(30303));
  }

  @Test
  void publicKeyIsUncompressed64Bytes() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecord(nodeKey, "10.0.0.1", 30303, 30303);
    final EthereumNodeRecord enr = EthereumNodeRecord.fromNodeRecord(record);

    assertThat(enr.publicKey().size()).isEqualTo(64);
  }

  @Test
  void nodeRecordFieldIsPreserved() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final NodeRecord record =
        DiscV5TestHelper.createSignedNodeRecord(nodeKey, "10.0.0.1", 30303, 30303);
    final EthereumNodeRecord enr = EthereumNodeRecord.fromNodeRecord(record);

    assertThat(enr.nodeRecord()).isSameAs(record);
  }

  private static Stream<Arguments> validEnrNodes() throws Exception {
    return Stream.of(
        Arguments.of(
            "enr:-KO4QK1ecw-CGrDDZ4YwFrhgqctD0tWMHKJhUVxsS4um3aUFe3yBHRtVL9uYKk16DurN1IdSKTOB1zNCvjBybjZ_KAq"
                + "GAYtJ5U8wg2V0aMfGhJsZKtCAgmlkgnY0gmlwhA_MtDmJc2VjcDI1NmsxoQNXD7fj3sscyOKBiHYy14igj1vJYWdKYZH7n3T8qRpIcYRzb"
                + "mFwwIN0Y3CCdl-DdWRwgnZf",
            Bytes.fromHexString(
                "0x570fb7e3decb1cc8e281887632d788a08f5bc961674a6191fb9f74fca91a4871957e3775d4bdfd4fdeff9bff92ad2f5965234d0e"
                    + "3c04ab4b85ab3eabd3193c35"),
            InetAddress.getByAddress(new byte[] {15, (byte) 204, (byte) 180, 57}),
            30303,
            30303),
        Arguments.of(
            "enr:-Jy4QK1ecw-CGrDDZ4YwFrhgqctD0tWMHKJhUVxsS4um3aUFe3yBHRtVL9uYKk16DurN1IdSKTOB1zNCvjBybjZ_KAq"
                + "GAYtJ5U8wg2V0aMfGhJsZKtCAgmlkgnY0gmlwhA_MtDmJc2VjcDI1NmsxoQNXD7fj3sscyOKBiHYy14igj1vJYWdKYZH7n3T8qRpIcYRzb"
                + "mFwwIN0Y3CCdl8=",
            Bytes.fromHexString(
                "0x570fb7e3decb1cc8e281887632d788a08f5bc961674a6191fb9f74fca91a4871957e3775d4bdfd4fdeff9bff92ad2f5965234d0e"
                    + "3c04ab4b85ab3eabd3193c35"),
            InetAddress.getByAddress(new byte[] {15, (byte) 204, (byte) 180, 57}),
            30303,
            30303));
  }
}
