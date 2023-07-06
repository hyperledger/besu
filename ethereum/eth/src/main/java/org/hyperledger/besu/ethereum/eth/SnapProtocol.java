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
package org.hyperledger.besu.ethereum.eth;

import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.List;

/**
 * Snap protocol messages as defined in https://github.com/ethereum/devp2p/blob/master/caps/snap.md}
 */
public class SnapProtocol implements SubProtocol {
  public static final String NAME = "snap";
  public static final Capability SNAP1 = Capability.create(NAME, SnapVersion.V1);

  private static final SnapProtocol INSTANCE = new SnapProtocol();

  private static final List<Integer> snap1Messages =
      List.of(
          SnapV1.GET_ACCOUNT_RANGE,
          SnapV1.ACCOUNT_RANGE,
          SnapV1.GET_STORAGE_RANGE,
          SnapV1.STORAGE_RANGE,
          SnapV1.GET_BYTECODES,
          SnapV1.BYTECODES,
          SnapV1.GET_TRIE_NODES,
          SnapV1.TRIE_NODES);

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int messageSpace(final int protocolVersion) {
    switch (protocolVersion) {
      case SnapVersion.V1:
        return 17;
      default:
        return 0;
    }
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    switch (protocolVersion) {
      case SnapVersion.V1:
        return snap1Messages.contains(code);
      default:
        return false;
    }
  }

  @Override
  public String messageName(final int protocolVersion, final int code) {
    switch (code) {
      case SnapV1.GET_ACCOUNT_RANGE:
        return "GetAccountRange";
      case SnapV1.ACCOUNT_RANGE:
        return "AccountRange";
      case SnapV1.GET_STORAGE_RANGE:
        return "GetStorageRange";
      case SnapV1.STORAGE_RANGE:
        return "StorageRange";
      case SnapV1.GET_BYTECODES:
        return "GetBytecodes";
      case SnapV1.BYTECODES:
        return "Bytecodes";
      case SnapV1.GET_TRIE_NODES:
        return "GetTrieNodes";
      case SnapV1.TRIE_NODES:
        return "TrieNodes";
      default:
        return INVALID_MESSAGE_NAME;
    }
  }

  public static SnapProtocol get() {
    return INSTANCE;
  }

  public static class SnapVersion {
    public static final int V1 = 1;
  }
}
