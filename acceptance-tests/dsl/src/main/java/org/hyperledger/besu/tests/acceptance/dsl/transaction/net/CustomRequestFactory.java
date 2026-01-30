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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.net;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class CustomRequestFactory {
  private final Web3jService web3jService;

  public static class NetServicesResponse extends Response<Map<String, Map<String, String>>> {}

  public static class TransactionReceiptWithRevertReason extends TransactionReceipt {
    private String revertReason;

    public TransactionReceiptWithRevertReason() {}

    @Override
    public void setRevertReason(final String revertReason) {
      this.revertReason = revertReason;
    }

    @Override
    public String getRevertReason() {
      return revertReason;
    }
  }

  public static class EthGetTransactionReceiptWithRevertReasonResponse
      extends Response<TransactionReceiptWithRevertReason> {}

  /**
   * Transaction receipt that includes the gasSpent field introduced by EIP-7778 (Amsterdam+). This
   * extends the standard web3j TransactionReceipt to include the new field.
   */
  public static class TransactionReceiptWithGasSpent extends TransactionReceipt {
    private String gasSpent;

    public TransactionReceiptWithGasSpent() {}

    public void setGasSpent(final String gasSpent) {
      this.gasSpent = gasSpent;
    }

    public String getGasSpent() {
      return gasSpent;
    }
  }

  public static class EthGetTransactionReceiptWithGasSpentResponse
      extends Response<TransactionReceiptWithGasSpent> {}

  /**
   * Block result that includes the slotNumber field introduced by EIP-7843 (Amsterdam+). This
   * provides access to the slot number field that is not available in standard web3j EthBlock.
   */
  public static class BlockWithSlotNumber {
    private String number;
    private String hash;
    private String slotNumber;

    public BlockWithSlotNumber() {}

    public String getNumber() {
      return number;
    }

    public void setNumber(final String number) {
      this.number = number;
    }

    public String getHash() {
      return hash;
    }

    public void setHash(final String hash) {
      this.hash = hash;
    }

    public String getSlotNumber() {
      return slotNumber;
    }

    public void setSlotNumber(final String slotNumber) {
      this.slotNumber = slotNumber;
    }
  }

  public static class EthGetBlockWithSlotNumberResponse extends Response<BlockWithSlotNumber> {}

  public CustomRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
  }

  public Request<?, NetServicesResponse> netServices() {
    return new Request<>(
        "net_services", Collections.emptyList(), web3jService, NetServicesResponse.class);
  }

  public Request<?, EthGetTransactionReceiptWithRevertReasonResponse>
      ethGetTransactionReceiptWithRevertReason(final String transactionHash) {
    return new Request<>(
        "eth_getTransactionReceipt",
        Collections.singletonList(transactionHash),
        web3jService,
        EthGetTransactionReceiptWithRevertReasonResponse.class);
  }

  /**
   * Fetches a transaction receipt with gasSpent field (EIP-7778, Amsterdam+).
   *
   * @param transactionHash the hash of the transaction
   * @return the request that returns a receipt with gasSpent
   */
  public Request<?, EthGetTransactionReceiptWithGasSpentResponse>
      ethGetTransactionReceiptWithGasSpent(final String transactionHash) {
    return new Request<>(
        "eth_getTransactionReceipt",
        Collections.singletonList(transactionHash),
        web3jService,
        EthGetTransactionReceiptWithGasSpentResponse.class);
  }

  /**
   * Fetches a block by number with slotNumber field (EIP-7843, Amsterdam+).
   *
   * @param blockNumber the block number (hex string like "0x1" or "latest")
   * @param fullTransactionObjects whether to return full transaction objects
   * @return the request that returns a block with slotNumber
   */
  public Request<?, EthGetBlockWithSlotNumberResponse> ethGetBlockByNumberWithSlotNumber(
      final String blockNumber, final boolean fullTransactionObjects) {
    return new Request<>(
        "eth_getBlockByNumber",
        Arrays.asList(blockNumber, fullTransactionObjects),
        web3jService,
        EthGetBlockWithSlotNumberResponse.class);
  }
}
