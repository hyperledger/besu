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

import java.util.Collections;
import java.util.List;

/**
 * Eth protocol messages as defined in <a
 * href="https://github.com/ethereum/devp2p/blob/master/caps/eth.md">Ethereum Wire Protocol
 * (ETH)</a>}
 */
public class EthProtocolVersion {
  public static final int V62 = 62;
  public static final int V63 = 63;
  public static final int V64 = 64;
  public static final int V65 = 65;
  public static final int V66 = 66;
  public static final int V67 = 67;
  public static final int V68 = 68;

  /** eth/62 (2015) */
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

  /**
   * eth/63 (2016)
   *
   * <p>Version 63 added the GetNodeData, NodeData, GetReceipts and Receipts messages which allow
   * synchronizing transaction execution results.
   */
  private static final List<Integer> eth63Messages =
      List.of(
          EthPV62.STATUS,
          EthPV62.NEW_BLOCK_HASHES,
          EthPV62.TRANSACTIONS,
          EthPV62.GET_BLOCK_HEADERS,
          EthPV62.BLOCK_HEADERS,
          EthPV62.GET_BLOCK_BODIES,
          EthPV62.BLOCK_BODIES,
          EthPV62.NEW_BLOCK,
          EthPV63.GET_NODE_DATA,
          EthPV63.NODE_DATA,
          EthPV63.GET_RECEIPTS,
          EthPV63.RECEIPTS);

  /**
   * eth/65 (EIP-2464, January 2020)
   *
   * <p>Version 65 improved transaction exchange, introducing three additional messages:
   * NewPooledTransactionHashes, GetPooledTransactions, and PooledTransactions.
   */
  private static final List<Integer> eth65Messages =
      List.of(
          EthPV62.STATUS,
          EthPV62.NEW_BLOCK_HASHES,
          EthPV62.TRANSACTIONS,
          EthPV62.GET_BLOCK_HEADERS,
          EthPV62.BLOCK_HEADERS,
          EthPV62.GET_BLOCK_BODIES,
          EthPV62.BLOCK_BODIES,
          EthPV62.NEW_BLOCK,
          EthPV63.GET_NODE_DATA,
          EthPV63.NODE_DATA,
          EthPV63.GET_RECEIPTS,
          EthPV63.RECEIPTS,
          EthPV65.NEW_POOLED_TRANSACTION_HASHES,
          EthPV65.GET_POOLED_TRANSACTIONS,
          EthPV65.POOLED_TRANSACTIONS);

  /**
   * eth/67 (EIP-4938, March 2022)
   *
   * <p>Version 67 removed the GetNodeData and NodeData messages.
   */
  private static final List<Integer> eth67Messages =
      List.of(
          EthPV62.STATUS,
          EthPV62.NEW_BLOCK_HASHES,
          EthPV62.TRANSACTIONS,
          EthPV62.GET_BLOCK_HEADERS,
          EthPV62.BLOCK_HEADERS,
          EthPV62.GET_BLOCK_BODIES,
          EthPV62.BLOCK_BODIES,
          EthPV62.NEW_BLOCK,
          EthPV63.GET_RECEIPTS,
          EthPV63.RECEIPTS,
          EthPV65.NEW_POOLED_TRANSACTION_HASHES,
          EthPV65.GET_POOLED_TRANSACTIONS,
          EthPV65.POOLED_TRANSACTIONS);

  /**
   * Returns a list of integers containing the supported messages given the protocol version
   *
   * @param protocolVersion the protocol version
   * @return a list containing the codes of supported messages
   */
  public static List<Integer> getSupportedMessages(final int protocolVersion) {
    switch (protocolVersion) {
      case EthProtocolVersion.V62:
        return eth62Messages;
      case EthProtocolVersion.V63:
      case EthProtocolVersion.V64:
        return eth63Messages;
      case EthProtocolVersion.V65:
      case EthProtocolVersion.V66:
        return eth65Messages;
      case EthProtocolVersion.V67:
      case EthProtocolVersion.V68:
        return eth67Messages;
      default:
        return Collections.emptyList();
    }
  }
}
