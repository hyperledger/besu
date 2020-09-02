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
 *
 */

package org.hyperledger.besu.ethereum.trie;

import java.io.PrintStream;

public class DumpVisitor<V> implements NodeVisitor<V> {
  private final PrintStream out;
  String prefix = "";

  public DumpVisitor(final PrintStream out) {
    this.out = out;
  }

  @Override
  public void visit(final ExtensionNode<V> extensionNode) {
    out.printf(
        "%s Extension %s%n%1$s \tPath %s%n",
        prefix, extensionNode.getHash().toHexString(), extensionNode.getPath().toHexString());
    final String oldPrefix = prefix;
    prefix += extensionNode.getPath().toHexString().substring(2);
    extensionNode.getChild().accept(this);
    prefix = oldPrefix;
  }

  @Override
  public void visit(final BranchNode<V> branchNode) {
    out.printf(
        "%s Branch %s%n%1$s \tPath %s%n",
        prefix, branchNode.getHash().toHexString(), branchNode.getPath());
    final String oldPrefix = prefix;
    for (byte i = 0; i < 16; i++) {
      prefix += Integer.toHexString(i);
      branchNode.child(i).accept(this);
      prefix = oldPrefix;
    }
  }

  @Override
  public void visit(final LeafNode<V> leafNode) {
    out.printf(
        "%s Leaf %s%n%1$s \tPath %s%n%1$s \tContent %s%n",
        prefix, leafNode.getHash().toHexString(), leafNode.getPath(), leafNode.getValue().get());
  }

  @Override
  public void visit(final NullNode<V> nullNode) {
    out.printf("%s Null%n", prefix);
  }
}
