/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.consensus.ibft.support;

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;
import org.hyperledger.besu.util.bytes.BytesValue;

public class StubbedPeerConnection {

  public static PeerConnection create(final BytesValue nodeId) {
    PeerConnection peerConnection = mock(PeerConnection.class);
    PeerInfo peerInfo = new PeerInfo(0, "IbftIntTestPeer", emptyList(), 0, nodeId);
    when(peerConnection.getPeerInfo()).thenReturn(peerInfo);
    return peerConnection;
  }
}
