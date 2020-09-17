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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DumpVisitor<V> implements NodeVisitor<V> {
  private static final Logger LOG = LogManager.getFormatterLogger();

  private String prefix = "";

  @Override
  public void visit(final ExtensionNode<V> extensionNode) {
    //noinspection PlaceholderCountMatchesArgumentCount
    LOG.trace(
        "%s Extension %s%n%1$s \tPath %s",
        prefix, extensionNode.getHash().toHexString(), extensionNode.getPath().toHexString());
    final String oldPrefix = prefix;
    prefix += extensionNode.getPath().toHexString().substring(2);
    extensionNode.getChild().accept(this);
    prefix = oldPrefix;
  }

  @Override
  public void visit(final BranchNode<V> branchNode) {
    //noinspection PlaceholderCountMatchesArgumentCount
    LOG.trace(
        "%s Branch %s%n%1$s \tPath %s",
        prefix, branchNode.getHash().toHexString(), branchNode.getPath());
    final String oldPrefix = prefix;
    for (byte i = 0; i < 16; i++) {
      //noinspection StringConcatenationInLoop
      prefix += Integer.toHexString(i);
      branchNode.child(i).accept(this);
      prefix = oldPrefix;
    }
  }

  @Override
  public void visit(final LeafNode<V> leafNode) {
    //noinspection PlaceholderCountMatchesArgumentCount
    LOG.trace(
        "%s Leaf %s%n%1$s \tPath %s%n%1$s \tContent %s",
        prefix, leafNode.getHash().toHexString(), leafNode.getPath(), leafNode.getValue().get());
  }

  @Override
  public void visit(final NullNode<V> nullNode) {
    //noinspection PlaceholderCountMatchesArgumentCount
    LOG.trace("%s Null", prefix);
  }
}
