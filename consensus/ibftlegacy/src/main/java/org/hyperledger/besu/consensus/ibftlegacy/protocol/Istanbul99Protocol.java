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
package org.hyperledger.besu.consensus.ibftlegacy.protocol;

import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.EthPV63;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.Arrays;
import java.util.List;

/**
 * Represents the istanbul/64 protocol as used by Quorum (effectively an extension of eth/65, which
 * adds a single message type (0x11) to encapsulate all communications required for IBFT block
 * mining.
 */
public class Istanbul99Protocol implements SubProtocol {

  public static final String NAME = "istanbul";
  private static final int VERSION = 99;

  static final Capability ISTANBUL99 = Capability.create(NAME, 99);
  static final int INSTANBUL_MSG = 0x11;

  private static final Istanbul99Protocol INSTANCE = new Istanbul99Protocol();

  private static final List<Integer> istanbul64Messages =
      Arrays.asList(
          EthPV62.STATUS,
          EthPV62.NEW_BLOCK_HASHES,
          EthPV62.TRANSACTIONS,
          EthPV62.GET_BLOCK_HEADERS,
          EthPV62.BLOCK_HEADERS,
          EthPV62.GET_BLOCK_BODIES,
          EthPV62.BLOCK_BODIES,
          EthPV62.NEW_BLOCK,
          EthPV65.NEW_POOLED_TRANSACTION_HASHES,
          EthPV65.GET_POOLED_TRANSACTIONS,
          EthPV65.POOLED_TRANSACTIONS,
          EthPV63.GET_NODE_DATA,
          EthPV63.NODE_DATA,
          EthPV63.GET_RECEIPTS,
          EthPV63.RECEIPTS,
          INSTANBUL_MSG);

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int messageSpace(final int protocolVersion) {
    return INSTANBUL_MSG + 1;
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    if (protocolVersion == VERSION) {
      return istanbul64Messages.contains(code);
    }
    return false;
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
      case INSTANBUL_MSG:
        return "InstanbulMsg";
      default:
        return INVALID_MESSAGE_NAME;
    }
  }

  public static Istanbul99Protocol get() {
    return INSTANCE;
  }
}
