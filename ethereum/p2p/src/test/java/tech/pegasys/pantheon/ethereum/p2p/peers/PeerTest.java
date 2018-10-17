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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexString;

import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryStatus;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.junit.Test;

public class PeerTest {

  @Test
  public void createPeer() {
    final BytesValue id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final String host = "127.0.0.1";
    final int port = 30303;

    final DiscoveryPeer peer = new DiscoveryPeer(id, host, port);
    assertEquals(id, peer.getId());
    assertEquals(host, peer.getEndpoint().getHost());
    assertEquals(port, peer.getEndpoint().getUdpPort());
  }

  @Test(expected = IllegalArgumentException.class)
  public void createPeer_NullHost() {
    final BytesValue id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final String host = null;
    final int port = 30303;

    new DiscoveryPeer(id, host, port);
  }

  @Test(expected = IllegalArgumentException.class)
  public void createPeer_NegativePort() {
    final BytesValue id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final String host = "127.0.0.1";
    final int port = -1;

    new DiscoveryPeer(id, host, port);
  }

  @Test(expected = IllegalArgumentException.class)
  public void createPeer_ZeroPort() {
    final BytesValue id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final String host = "127.0.0.1";
    final int port = 0;

    new DiscoveryPeer(id, host, port);
  }

  @Test(expected = IllegalArgumentException.class)
  public void createPeer_TooBigPort() {
    final BytesValue id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final String host = "127.0.0.1";
    final int port = 70000;

    new DiscoveryPeer(id, host, port);
  }

  @Test
  public void notEquals() {
    final BytesValue id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final Peer peer = new DiscoveryPeer(id, "127.0.0.1", 5000);
    final Peer peer2 = new DiscoveryPeer(id, "127.0.0.1", 5001);
    assertNotEquals(peer, peer2);
  }

  @Test
  public void differentHashCode() {
    final BytesValue id =
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b");
    final DiscoveryPeer peer = new DiscoveryPeer(id, "127.0.0.1", 5000);
    final DiscoveryPeer peer2 = new DiscoveryPeer(id, "127.0.0.1", 5001);
    assertNotEquals(peer.hashCode(), peer2.hashCode());
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullId() {
    new DiscoveryPeer(null, "127.0.0.1", 5000);
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyId() {
    new DiscoveryPeer(BytesValue.wrap(new byte[0]), "127.0.0.1", 5000);
  }

  @Test
  public void getStatus() {
    final DiscoveryPeer peer = new DiscoveryPeer(Peer.randomId(), "127.0.0.1", 5000);
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
    assertEquals("172.20.0.4", peer.getEndpoint().getHost());
    assertEquals(30403, peer.getEndpoint().getUdpPort());
    assertEquals(30403, peer.getEndpoint().getTcpPort().getAsInt());
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
  public void createFromURIWithoutPortUsesDefault() {
    final Peer peer =
        DefaultPeer.fromURI(
            "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@172.20.0.4");
    assertEquals(
        fromHexString(
            "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b"),
        peer.getId());
    assertEquals("172.20.0.4", peer.getEndpoint().getHost());
    assertEquals(30303, peer.getEndpoint().getUdpPort());
    assertEquals(30303, peer.getEndpoint().getTcpPort().getAsInt());
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
    assertEquals("172.20.0.4", peer.getEndpoint().getHost());
    assertEquals(22222, peer.getEndpoint().getUdpPort());
    assertEquals(12345, peer.getEndpoint().getTcpPort().getAsInt());
  }

  @Test
  public void createPeerFromURIWithDifferentUdpAndTcpPorts_InvalidTcpPort() {
    final Peer[] peers =
        new Peer[] {
          DefaultPeer.fromURI(
              "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@172.20.0.4:12345?discport=99999"),
          DefaultPeer.fromURI(
              "enode://c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b@172.20.0.4:12345?discport=99999000")
        };

    for (final Peer peer : peers) {
      assertEquals(
          fromHexString(
              "c7849b663d12a2b5bf05b1ebf5810364f4870d5f1053fbd7500d38bc54c705b453d7511ca8a4a86003d34d4c8ee0bbfcd387aa724f5b240b3ab4bbb994a1e09b"),
          peer.getId());
      assertEquals("172.20.0.4", peer.getEndpoint().getHost());
      assertEquals(12345, peer.getEndpoint().getUdpPort());
      assertEquals(12345, peer.getEndpoint().getTcpPort().getAsInt());
    }
  }
}
