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

import static java.util.stream.Collectors.toUnmodifiableList;

import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.EthPV63;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Eth protocol messages as defined in
 * https://github.com/ethereum/wiki/wiki/Ethereum-Wire-Protocol#new-model-syncing-pv62}
 */
public class EthProtocol implements SubProtocol {
  public static final String NAME = "eth";
  public static final Capability ETH62 = Capability.create(NAME, EthVersion.V62);
  public static final Capability ETH63 = Capability.create(NAME, EthVersion.V63);
  public static final Capability ETH64 = Capability.create(NAME, EthVersion.V64);
  public static final Capability ETH65 = Capability.create(NAME, EthVersion.V65);
  public static final Capability ETH66 = Capability.create(NAME, EthVersion.V66);

  private static final EthProtocol INSTANCE = new EthProtocol();

  private static final List<Integer> eth62Messages =
      List.of(
          EthPV62.STATUS,
          EthPV62.NEW_BLOCK_HASHES,
          EthPV62.TRANSACTIONS,
          EthPV62.GET_BLOCK_HEADERS,
          EthPV62.BLOCK_HEADERS,
          EthPV62.GET_BLOCK_BODIES,
          EthPV62.BLOCK_BODIES,
          EthPV62.NEW_BLOCK);

  private static final List<Integer> eth63Messages =
      Stream.concat(
              eth62Messages.stream(),
              Stream.of(
                  EthPV63.GET_NODE_DATA, EthPV63.NODE_DATA, EthPV63.GET_RECEIPTS, EthPV63.RECEIPTS))
          .collect(toUnmodifiableList());

  private static final List<Integer> eth65Messages =
      Stream.concat(
              eth63Messages.stream(),
              Stream.of(
                  EthPV65.NEW_POOLED_TRANSACTION_HASHES,
                  EthPV65.GET_POOLED_TRANSACTIONS,
                  EthPV65.POOLED_TRANSACTIONS))
          .collect(toUnmodifiableList());

  public static boolean requestIdCompatible(final int code) {
    return Set.of(
            EthPV62.GET_BLOCK_HEADERS,
            EthPV62.BLOCK_HEADERS,
            EthPV62.GET_BLOCK_BODIES,
            EthPV62.BLOCK_BODIES,
            EthPV65.GET_POOLED_TRANSACTIONS,
            EthPV65.POOLED_TRANSACTIONS,
            EthPV63.GET_NODE_DATA,
            EthPV63.NODE_DATA,
            EthPV63.GET_RECEIPTS,
            EthPV63.RECEIPTS)
        .contains(code);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int messageSpace(final int protocolVersion) {
    switch (protocolVersion) {
      case EthVersion.V62:
        return 8;
      case EthVersion.V63:
      case EthVersion.V64:
      case EthVersion.V65:
      case EthVersion.V66:
        // same number of messages in each range, eth65 defines messages in the middle of the
        // range defined by eth63 and eth64 defines no new ranges.
        return 17;
      default:
        return 0;
    }
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    switch (protocolVersion) {
      case EthVersion.V62:
        return eth62Messages.contains(code);
      case EthVersion.V63:
      case EthVersion.V64:
        return eth63Messages.contains(code);
      case EthVersion.V65:
      case EthVersion.V66:
        return eth65Messages.contains(code);
      default:
        return false;
    }
  }

  @Override
  public String messageName(final int protocolVersion, final int code) {
    switch (code) {
      case EthPV62.STATUS:
        return "Status";
      case EthPV62.NEW_BLOCK_HASHES:
        return "NewBlockHashes";
      case EthPV62.TRANSACTIONS:
        return "Transactions";
      case EthPV62.GET_BLOCK_HEADERS:
        return "GetBlockHeaders";
      case EthPV62.BLOCK_HEADERS:
        return "BlockHeaders";
      case EthPV62.GET_BLOCK_BODIES:
        return "GetBlockBodies";
      case EthPV62.BLOCK_BODIES:
        return "BlockBodies";
      case EthPV62.NEW_BLOCK:
        return "NewBlock";
      case EthPV65.NEW_POOLED_TRANSACTION_HASHES:
        return "NewPooledTransactionHashes";
      case EthPV65.GET_POOLED_TRANSACTIONS:
        return "GetPooledTransactions";
      case EthPV65.POOLED_TRANSACTIONS:
        return "PooledTransactions";
      case EthPV63.GET_NODE_DATA:
        return "GetNodeData";
      case EthPV63.NODE_DATA:
        return "NodeData";
      case EthPV63.GET_RECEIPTS:
        return "GetReceipts";
      case EthPV63.RECEIPTS:
        return "Receipts";
      default:
        return INVALID_MESSAGE_NAME;
    }
  }

  public static EthProtocol get() {
    return INSTANCE;
  }

  public static class EthVersion {
    public static final int V62 = 62;
    public static final int V63 = 63;
    public static final int V64 = 64;
    public static final int V65 = 65;
    public static final int V66 = 66;
  }

  public static boolean isEth66Compatible(final Capability capability) {
    return NAME.equals(capability.getName()) && capability.getVersion() >= ETH66.getVersion();
  }
}
