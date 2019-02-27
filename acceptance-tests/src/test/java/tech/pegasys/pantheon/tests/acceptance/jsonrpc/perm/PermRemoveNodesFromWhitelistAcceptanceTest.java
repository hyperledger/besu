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
package tech.pegasys.pantheon.tests.acceptance.jsonrpc.perm;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class PermRemoveNodesFromWhitelistAcceptanceTest extends AcceptanceTestBase {

  private Node node;

  private final String enode1 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:4567";
  private final String enode2 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.2:4567";
  private final String enode3 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.3:4567";
  private final ArrayList<URI> nodesWhitelist =
      Lists.newArrayList(URI.create(enode1), URI.create(enode2), URI.create(enode3));

  @Before
  public void setUp() throws Exception {
    node =
        pantheon.createNodeWithBootnodeAndNodesWhitelist(
            "node1", Collections.emptyList(), nodesWhitelist);
    cluster.start(node);
  }

  @Test
  public void shouldRemoveSinglePeer() {
    node.verify(perm.removeNodesFromWhitelist(Lists.newArrayList(enode1)));
  }

  @Test
  public void shouldRemoveMultiplePeers() {
    node.verify(perm.removeNodesFromWhitelist(Lists.newArrayList(enode1, enode2, enode3)));
  }
}
