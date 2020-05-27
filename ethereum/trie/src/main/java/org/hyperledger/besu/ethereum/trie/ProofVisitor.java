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
package org.hyperledger.besu.ethereum.trie;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;

class ProofVisitor extends GetVisitor implements PathNodeVisitor {

  private final Node rootNode;
  private final List<Node> proof = new ArrayList<>();

  ProofVisitor(final Node rootNode) {
    this.rootNode = rootNode;
  }

  @Override
  public Node visit(final ExtensionNode extensionNode, final Bytes path) {
    maybeTrackNode(extensionNode);
    return super.visit(extensionNode, path);
  }

  @Override
  public Node visit(final BranchNode branchNode, final Bytes path) {
    maybeTrackNode(branchNode);
    return super.visit(branchNode, path);
  }

  @Override
  public Node visit(final LeafNode leafNode, final Bytes path) {
    maybeTrackNode(leafNode);
    return super.visit(leafNode, path);
  }

  @Override
  public Node visit(final NullNode nullNode, final Bytes path) {
    return super.visit(nullNode, path);
  }

  public List<Node> getProof() {
    return proof;
  }

  private void maybeTrackNode(final Node node) {
    if (node.equals(rootNode) || node.isReferencedByHash()) {
      proof.add(node);
    }
  }
}
