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

import org.hyperledger.besu.ethereum.eth.messages.EthProtocolMessages;
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
  public static final Capability ETH66 = Capability.create(NAME, EthProtocolVersion.V66);
  public static final Capability ETH67 = Capability.create(NAME, EthProtocolVersion.V67);
  public static final Capability ETH68 = Capability.create(NAME, EthProtocolVersion.V68);

  // Latest version of the Eth protocol
  public static final Capability LATEST = ETH68;

  public static boolean requestIdCompatible(final int code) {
    return Set.of(
            EthProtocolMessages.GET_BLOCK_HEADERS,
            EthProtocolMessages.BLOCK_HEADERS,
            EthProtocolMessages.GET_BLOCK_BODIES,
            EthProtocolMessages.BLOCK_BODIES,
            EthProtocolMessages.GET_POOLED_TRANSACTIONS,
            EthProtocolMessages.POOLED_TRANSACTIONS,
            EthProtocolMessages.GET_NODE_DATA,
            EthProtocolMessages.NODE_DATA,
            EthProtocolMessages.GET_RECEIPTS,
            EthProtocolMessages.RECEIPTS)
        .contains(code);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int messageSpace(final int protocolVersion) {
    return switch (protocolVersion) {
      case EthProtocolVersion.V66, EthProtocolVersion.V67, EthProtocolVersion.V68 -> 17;
      default -> 0;
    };
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    return EthProtocolVersion.getSupportedMessages(protocolVersion).contains(code);
  }

  @Override
  public String messageName(final int protocolVersion, final int code) {
    switch (code) {
      case EthProtocolMessages.STATUS:
        return "Status";
      case EthProtocolMessages.NEW_BLOCK_HASHES:
        return "NewBlockHashes";
      case EthProtocolMessages.TRANSACTIONS:
        return "Transactions";
      case EthProtocolMessages.GET_BLOCK_HEADERS:
        return "GetBlockHeaders";
      case EthProtocolMessages.BLOCK_HEADERS:
        return "BlockHeaders";
      case EthProtocolMessages.GET_BLOCK_BODIES:
        return "GetBlockBodies";
      case EthProtocolMessages.BLOCK_BODIES:
        return "BlockBodies";
      case EthProtocolMessages.NEW_BLOCK:
        return "NewBlock";
      case EthProtocolMessages.NEW_POOLED_TRANSACTION_HASHES:
        return "NewPooledTransactionHashes";
      case EthProtocolMessages.GET_POOLED_TRANSACTIONS:
        return "GetPooledTransactions";
      case EthProtocolMessages.POOLED_TRANSACTIONS:
        return "PooledTransactions";
      case EthProtocolMessages.GET_NODE_DATA:
        return "GetNodeData";
      case EthProtocolMessages.NODE_DATA:
        return "NodeData";
      case EthProtocolMessages.GET_RECEIPTS:
        return "GetReceipts";
      case EthProtocolMessages.RECEIPTS:
        return "Receipts";
      default:
        return INVALID_MESSAGE_NAME;
    }
  }

  public static EthProtocol get() {
    return INSTANCE;
  }
}
