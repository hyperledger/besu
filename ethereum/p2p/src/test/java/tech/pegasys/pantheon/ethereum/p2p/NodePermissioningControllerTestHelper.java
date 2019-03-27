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
package tech.pegasys.pantheon.ethereum.p2p;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.permissioning.node.NodePermissioningController;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import org.mockito.ArgumentCaptor;

public class NodePermissioningControllerTestHelper {

  private final EnodeURL localNode;
  private final Collection<EnodeURL> permittedNodes = new ArrayList<>();
  private final Collection<EnodeURL> notPermittedNodes = new ArrayList<>();
  private boolean allowAll = false;
  private boolean denyAll = false;

  public NodePermissioningControllerTestHelper(final Peer localPeer) {
    this.localNode = localPeer.getEnodeURL();
  }

  public NodePermissioningControllerTestHelper withPermittedPeers(final Peer... peers) {
    this.permittedNodes.addAll(
        Arrays.stream(peers).map(Peer::getEnodeURL).collect(Collectors.toList()));
    return this;
  }

  public NodePermissioningControllerTestHelper withForbiddenPeers(final Peer... peers) {
    this.notPermittedNodes.addAll(
        Arrays.stream(peers).map(Peer::getEnodeURL).collect(Collectors.toList()));
    return this;
  }

  public NodePermissioningControllerTestHelper allowAll() {
    this.allowAll = true;
    return this;
  }

  public NodePermissioningControllerTestHelper denyAll() {
    this.denyAll = true;
    return this;
  }

  public NodePermissioningController build() {
    final NodePermissioningController nodePermissioningController =
        spy(new NodePermissioningController(Optional.empty(), new ArrayList<>()));

    if (allowAll && denyAll) {
      throw new IllegalArgumentException(
          "Can't allow all nodes and deny all nodes in the same NodePermissioningController");
    } else if (allowAll) {
      when(nodePermissioningController.isPermitted(any(), any())).thenReturn(true);
    } else if (denyAll) {
      when(nodePermissioningController.isPermitted(any(), any())).thenReturn(false);
    } else {
      permittedNodes.forEach(
          node -> {
            when(nodePermissioningController.isPermitted(eq(localNode), eq(node))).thenReturn(true);
            when(nodePermissioningController.isPermitted(eq(node), eq(localNode))).thenReturn(true);
          });

      notPermittedNodes.forEach(
          node -> {
            when(nodePermissioningController.isPermitted(eq(localNode), eq(node)))
                .thenReturn(false);
            when(nodePermissioningController.isPermitted(eq(node), eq(localNode)))
                .thenReturn(false);
          });
    }

    ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
    doAnswer(
            (i) -> {
              callback.getValue().run();
              return null;
            })
        .when(nodePermissioningController)
        .startPeerDiscoveryCallback(callback.capture());

    return nodePermissioningController;
  }
}
