/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.EthPV63;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.Set;

/**
 * Eth protocol messages as defined in <a
 * href="https://github.com/ethereum/devp2p/blob/master/caps/eth.md">Ethereum Wire Protocol
 * (ETH)</a>}
 */
public class EthProtocol implements SubProtocol {
  public static final String NAME = "eth";
  private static final EthProtocol INSTANCE = new EthProtocol();
  public static final Capability ETH62 = Capability.create(NAME, EthProtocolVersion.V62);
  public static final Capability ETH63 = Capability.create(NAME, EthProtocolVersion.V63);
  public static final Capability ETH64 = Capability.create(NAME, EthProtocolVersion.V64);
  public static final Capability ETH65 = Capability.create(NAME, EthProtocolVersion.V65);
  public static final Capability ETH66 = Capability.create(NAME, EthProtocolVersion.V66);
  public static final Capability ETH67 = Capability.create(NAME, EthProtocolVersion.V67);
  public static final Capability ETH68 = Capability.create(NAME, EthProtocolVersion.V68);

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
      case EthProtocolVersion.V62:
        return 8;
      case EthProtocolVersion.V63:
      case EthProtocolVersion.V64:
      case EthProtocolVersion.V65:
      case EthProtocolVersion.V66:
      case EthProtocolVersion.V67:
      case EthProtocolVersion.V68:
        // same number of messages in each range, eth65 defines messages in the middle of the
        // range defined by eth63 and eth64 defines no new ranges.
        return 17;
      default:
        return 0;
    }
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    return EthProtocolVersion.getSupportedMessages(protocolVersion).contains(code);
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

  public static boolean isEth66Compatible(final Capability capability) {
    return NAME.equals(capability.getName()) && capability.getVersion() >= ETH66.getVersion();
  }
}
