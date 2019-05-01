/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.p2p.peers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexString;

import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryStatus;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.OptionalInt;

import com.google.common.net.InetAddresses;
import org.junit.Test;

public class PeerTest {

  @Test
  public void notEquals() {
    final BytesValue id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final Peer peer =
        DiscoveryPeer.fromEnode(
            EnodeURL.builder().nodeId(id).ipAddress("127.0.0.1").listeningPort(5000).build());
    final Peer peer2 =
        DiscoveryPeer.fromEnode(
            EnodeURL.builder().nodeId(id).ipAddress("127.0.0.1").listeningPort(5001).build());
    assertNotEquals(peer, peer2);
  }

  @Test
  public void differentHashCode() {
    final BytesValue id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final Peer peer =
        DiscoveryPeer.fromEnode(
            EnodeURL.builder().nodeId(id).ipAddress("127.0.0.1").listeningPort(5000).build());
    final Peer peer2 =
        DiscoveryPeer.fromEnode(
            EnodeURL.builder().nodeId(id).ipAddress("127.0.0.1").listeningPort(5001).build());
    assertNotEquals(peer.hashCode(), peer2.hashCode());
  }

  @Test
  public void getStatus() {
    final DiscoveryPeer peer =
        DiscoveryPeer.fromEnode(
            EnodeURL.builder()
                .nodeId(Peer.randomId())
                .ipAddress("127.0.0.1")
                .listeningPort(5000)
                .build());
    assertEquals(PeerDiscoveryStatus.KNOWN, peer.getStatus());
  }

  @Test
  public void createFromURI() {
    final Peer peer =
        DefaultPeer.fromURI(
            "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@172.20.0.4:30403");
    assertEquals(
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b"),
        peer.getId());
    assertEquals("172.20.0.4", peer.getEnodeURL().getIpAsString());
    assertEquals(30403, peer.getEnodeURL().getListeningPort());
    assertEquals(OptionalInt.empty(), peer.getEnodeURL().getDiscoveryPort());
  }

  @Test
  public void createFromIpv6URI() {
    final Peer peer =
        DefaultPeer.fromURI(
            "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@[2001:0DB8:85A3:0000::8A2E:370:7334]:30403");
    assertEquals(
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b"),
        peer.getId());
    assertEquals(
        InetAddresses.forString("2001:db8:85a3::8a2e:370:7334"), peer.getEnodeURL().getIp());
    assertEquals(30403, peer.getEnodeURL().getListeningPort());
    assertEquals(OptionalInt.empty(), peer.getEnodeURL().getDiscoveryPort());
  }

  @Test(expected = IllegalArgumentException.class)
  public void createFromURIFailsForWrongScheme() {
    DefaultPeer.fromURI("http://user@foo:80");
  }

  @Test(expected = IllegalArgumentException.class)
  public void createFromURIFailsForMissingId() {
    DefaultPeer.fromURI("enode://172.20.0.4:30303");
  }

  @Test(expected = IllegalArgumentException.class)
  public void createFromURIFailsForMissingHost() {
    DefaultPeer.fromURI(
        "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@:30303");
  }

  @Test
  public void createPeerFromURIWithDifferentUdpAndTcpPorts() {
    final Peer peer =
        DefaultPeer.fromURI(
            "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@172.20.0.4:12345?discport=22222");
    assertEquals(
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b"),
        peer.getId());
    assertEquals("172.20.0.4", peer.getEnodeURL().getIpAsString());
    assertEquals(22222, peer.getEnodeURL().getEffectiveDiscoveryPort());
    assertEquals(12345, peer.getEnodeURL().getListeningPort());
  }

  @Test
  public void fromURI_invalidDiscoveryPort() {
    String invalidEnode =
        "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@172.20.0.4:12345?discport=99999";
    assertThatThrownBy(() -> DefaultPeer.fromURI(invalidEnode))
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }
}
