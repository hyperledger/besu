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

import org.apache.tuweni.bytes.Bytes;

public class RemoveVisitor<V> implements PathNodeVisitor<V> {
  private final Node<V> NULL_NODE_RESULT = NullNode.instance();

  private final boolean allowFlatten;

  public RemoveVisitor() {
    this(true);
  }

  public RemoveVisitor(final boolean allowFlatten) {
    this.allowFlatten = allowFlatten;
  }

  @Override
  public Node<V> visit(
      final ExtensionNode<V> extensionNode, final Bytes location, final Bytes path) {
    final Bytes extensionPath = extensionNode.getPath();
    final int commonPathLength = extensionPath.commonPrefixLength(path);
    assert commonPathLength < path.size()
        : "Visiting path doesn't end with a non-matching terminator";

    if (commonPathLength == extensionPath.size()) {
      final Node<V> newChild =
          extensionNode
              .getChild()
              .accept(
                  this, Bytes.concatenate(location, extensionPath), path.slice(commonPathLength));
      final Node<V> updatedNode = extensionNode.replaceChild(newChild);

      if (updatedNode instanceof LeafNode) {
        extensionNode.getChild().setPos(1);
        remove(extensionNode, updatedNode, extensionNode.getChild());
      } else if (updatedNode instanceof BranchNode) {
        if (updatedNode.getChildren().get(extensionNode.getPath().get(0)) instanceof NullNode) {

          extensionNode.getChild().setPos(2);
          remove(extensionNode, updatedNode, extensionNode.getChild());
        }
      } else if (updatedNode instanceof ExtensionNode) {
        if (extensionNode.getPath().size() < updatedNode.getPath().size()) {

          extensionNode.getChild().setPos(3);
          remove(extensionNode, updatedNode, extensionNode.getChild());
        }
      }
      return updatedNode;
    }

    return extensionNode;
  }

  @Override
  public Node<V> visit(final BranchNode<V> branchNode, final Bytes location, final Bytes path) {
    assert path.size() > 0 : "Visiting path doesn't end with a non-matching terminator";
    final byte childIndex = path.get(0);

    // System.out.println("remove visitor "+branchNode.print());

    if (childIndex == CompactEncoding.LEAF_TERMINATOR) {
      final Node<V> updatedNode = branchNode.removeValue();
      if (updatedNode instanceof LeafNode) {
        for (int i = 0; i < BranchNode.RADIX; i++) {
          if (!(branchNode.getChildren().get(i) instanceof NullNode)) {

            branchNode.child((byte) i).setPos(4);
            remove(branchNode, updatedNode, branchNode.child((byte) i));
          }
        }
      } else if (updatedNode instanceof BranchNode) {
        for (int i = 0; i < BranchNode.RADIX; i++) {
          if (!(branchNode.getChildren().get(i) instanceof NullNode)
              && updatedNode.getChildren().get(i) instanceof NullNode) {
            branchNode.child((byte) i).setPos(5);
            remove(branchNode, updatedNode, branchNode.child((byte) i));
          }
        }
      } else if (updatedNode instanceof ExtensionNode) {
        for (int i = 0; i < BranchNode.RADIX; i++) {
          final Node<V> child = branchNode.getChildren().get(i);
          if (!(child instanceof NullNode)) {
            if (updatedNode.getPath().get(0) != (byte) i) {
              branchNode.child((byte) i).setPos(6);
              remove(branchNode, updatedNode, branchNode.child((byte) i));
            } else if (updatedNode.getPath().size() > 1) {
              branchNode.child((byte) i).setPos(7);
              remove(branchNode, updatedNode, branchNode.child((byte) i));
            }
          }
        }
      }
      return updatedNode;
    }

    final Node<V> updatedChild =
        branchNode
            .child(childIndex)
            .accept(this, Bytes.concatenate(location, Bytes.of(childIndex)), path.slice(1));
    final Node<V> updatedNode = branchNode.replaceChild(childIndex, updatedChild, allowFlatten);
    if (updatedNode instanceof LeafNode) {
      for (int i = 0; i < BranchNode.RADIX; i++) {
        if (!(branchNode.getChildren().get(i) instanceof NullNode)) {
          branchNode.child((byte) i).setPos(8);
          remove(branchNode, updatedNode, branchNode.child((byte) i));
        }
      }
    } else if (updatedNode instanceof BranchNode) {
      for (int i = 0; i < BranchNode.RADIX; i++) {
        if (!(branchNode.getChildren().get(i) instanceof NullNode)
            && updatedNode.getChildren().get(i) instanceof NullNode) {
          branchNode.child((byte) i).setPos(9);
          remove(branchNode, updatedNode, branchNode.child((byte) i));
        }
      }
    } else if (updatedNode instanceof ExtensionNode) {
      for (int i = 0; i < BranchNode.RADIX; i++) {
        final Node<V> child = branchNode.getChildren().get(i);
        if (!(child instanceof NullNode)) {
          if (updatedNode.getPath().get(0) != (byte) i) {
            branchNode.child((byte) i).setPos(10);
            remove(branchNode, updatedNode, branchNode.child((byte) i));
          } else if (updatedNode.getPath().size() > 1) {
            branchNode.child((byte) i).setPos(11);
            remove(branchNode, updatedNode, branchNode.child((byte) i));
          }
        }
      }
    }
    return updatedNode;
  }

  @Override
  public Node<V> visit(final LeafNode<V> leafNode, final Bytes location, final Bytes path) {
    final Bytes leafPath = leafNode.getPath();

    // System.out.println("remove visitor "+leafNode.print());
    final int commonPathLength = leafPath.commonPrefixLength(path);
    if (commonPathLength == leafPath.size()) {

      leafNode.setPos(12);
      remove(leafNode, NULL_NODE_RESULT, leafNode);
      return NULL_NODE_RESULT;
    }
    return leafNode;
  }

  @Override
  public Node<V> visit(final NullNode<V> nullNode, final Bytes location, final Bytes path) {
    return NULL_NODE_RESULT;
  }

  public void remove(final Node<V> node, final Node<V> updatedNode, final Node<V> child) {
    // nothing to do
  }
}
