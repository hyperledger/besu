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
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import static java.lang.String.format;

public class NodeHashStreamer {

  final NodeLoader nodeLoader;

  NodeHashStreamer(final NodeLoader nodeLoader) {
    this.nodeLoader = nodeLoader;
  }

  public Stream<Bytes32> streamHashes(final Bytes32 hash) {
    final List<Bytes32> childrenHashes = new ArrayList<>();
    final Bytes nodeRLPs =
        nodeLoader
            .getNode(hash)
            .map(
                bytes -> {
                  final RLPInput rlpInput = RLP.input(bytes);
                  final int nodesCount = rlpInput.enterList();
                  try {
                    switch (nodesCount) {
                      case 1:
                        break;

                      case 2:
                        final Bytes encodedPath = rlpInput.readBytes();
                        final Bytes path;
                        try {
                          path = CompactEncoding.decode(encodedPath);
                        } catch (final IllegalArgumentException ex) {
                          throw new MerkleTrieException("");
                        }

                        final int size = path.size();
                        if (size > 0 && path.get(size - 1) == CompactEncoding.LEAF_TERMINATOR) {
                          return decodeLeaf(path, rlpInput);
                        } else {
                          return decodeExtension(path, rlpInput);
                        }

                      case (BranchNode.RADIX + 1):
                        return decodeBranch(rlpInput);

                      default:
                        throw new MerkleTrieException(
                            errMessage.get() + format(": invalid list size %s", nodesCount));
                    }
                  } finally {
                    rlpInput.leaveList();
                  }
                });
    return Stream.concat(Stream.of(extensionNode), extensionNode.getChild().accept(this));
  }
}
