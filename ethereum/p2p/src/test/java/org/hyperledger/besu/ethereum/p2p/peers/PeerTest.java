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
package org.hyperledger.besu.ethereum.p2p.peers;

import static org.apache.tuweni.bytes.Bytes.fromHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryStatus;

import com.google.common.net.InetAddresses;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class PeerTest {

  @Test
  public void notEquals() {
    final Bytes id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final Peer peer =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .nodeId(id)
                .ipAddress("127.0.0.1")
                .discoveryAndListeningPorts(5000)
                .build());
    final Peer peer2 =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .nodeId(id)
                .ipAddress("127.0.0.1")
                .discoveryAndListeningPorts(5001)
                .build());
    assertThat(peer).isNotEqualTo(peer2);
  }

  @Test
  public void differentHashCode() {
    final Bytes id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final Peer peer =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .nodeId(id)
                .ipAddress("127.0.0.1")
                .discoveryAndListeningPorts(5000)
                .build());
    final Peer peer2 =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .nodeId(id)
                .ipAddress("127.0.0.1")
                .discoveryAndListeningPorts(5001)
                .build());
    assertThat(peer.hashCode()).isNotEqualTo(peer2.hashCode());
  }

  @Test
  public void getStatus() {
    final DiscoveryPeer peer =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .nodeId(Peer.randomId())
                .ipAddress("127.0.0.1")
                .discoveryAndListeningPorts(5000)
                .build());
    assertThat(peer.getStatus()).isEqualTo(PeerDiscoveryStatus.KNOWN);
  }

  @Test
  public void createFromURI() {
    final Peer peer =
        DefaultPeer.fromURI(
            "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@172.20.0.4:30403");
    assertThat(peer.getId())
        .isEqualTo(
            fromHexString(
                "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b"));
    assertThat(peer.getEnodeURL().getIpAsString()).isEqualTo("172.20.0.4");
    assertThat(peer.getEnodeURL().getListeningPortOrZero()).isEqualTo(30403);
    assertThat(peer.getEnodeURL().getDiscoveryPortOrZero()).isEqualTo(30403);
  }

  @Test
  public void createFromIpv6URI() {
    final Peer peer =
        DefaultPeer.fromURI(
            "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@[2001:0DB8:85A3:0000::8A2E:370:7334]:30403");
    assertThat(peer.getId())
        .isEqualTo(
            fromHexString(
                "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b"));
    assertThat(peer.getEnodeURL().getIp())
        .isEqualTo(InetAddresses.forString("2001:db8:85a3::8a2e:370:7334"));
    assertThat(peer.getEnodeURL().getListeningPortOrZero()).isEqualTo(30403);
    assertThat(peer.getEnodeURL().getDiscoveryPortOrZero()).isEqualTo(30403);
  }

  @Test
  public void createFromURIFailsForWrongScheme() {
    assertThrows(IllegalArgumentException.class, () -> DefaultPeer.fromURI("http://user@foo:80"));
  }

  @Test
  public void createFromURIFailsForMissingId() {
    assertThrows(
        IllegalArgumentException.class, () -> DefaultPeer.fromURI("enode://172.20.0.4:30303"));
  }

  @Test
  public void createFromURIFailsForMissingHost() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            DefaultPeer.fromURI(
                "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@:30303"));
  }

  @Test
  public void createPeerFromURIWithDifferentUdpAndTcpPorts() {
    final Peer peer =
        DefaultPeer.fromURI(
            "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@172.20.0.4:12345?discport=22222");
    assertThat(peer.getId())
        .isEqualTo(
            fromHexString(
                "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b"));
    assertThat(peer.getEnodeURL().getIpAsString()).isEqualTo("172.20.0.4");
    assertThat(peer.getEnodeURL().getDiscoveryPortOrZero()).isEqualTo(22222);
    assertThat(peer.getEnodeURL().getListeningPortOrZero()).isEqualTo(12345);
  }

  @Test
  public void fromURI_invalidDiscoveryPort() {
    String invalidEnode =
        "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@172.20.0.4:12345?discport=99999";
    assertThatThrownBy(() -> DefaultPeer.fromURI(invalidEnode))
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }
}
