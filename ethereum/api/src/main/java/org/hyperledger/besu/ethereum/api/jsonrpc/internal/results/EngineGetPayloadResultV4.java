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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.DepositParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalRequestParameter;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tuweni.bytes.Bytes32;

/** The type Engine get payload result v4. */
@JsonPropertyOrder({"executionPayload", "blockValue", "blobsBundle", "shouldOverrideBuilder"})
public class EngineGetPayloadResultV4 {
  /** The Execution payload. */
  protected final PayloadResult executionPayload;

  private final String blockValue;
  private final BlobsBundleV1 blobsBundle;
  private final boolean shouldOverrideBuilder;

  /**
   * Instantiates a new Engine get payload result v4.
   *
   * @param header the header
   * @param transactions the transactions
   * @param withdrawals the withdrawals
   * @param deposits the deposits
   * @param withdrawalRequests the withdrawal requests
   * @param blockValue the block value
   * @param blobsBundle the blobs bundle
   */
  public EngineGetPayloadResultV4(
      final BlockHeader header,
      final List<String> transactions,
      final Optional<List<Withdrawal>> withdrawals,
      final Optional<List<Deposit>> deposits,
      final Optional<List<WithdrawalRequest>> withdrawalRequests,
      final String blockValue,
      final BlobsBundleV1 blobsBundle) {
    this.executionPayload =
        new PayloadResult(header, transactions, withdrawals, deposits, withdrawalRequests);
    this.blockValue = blockValue;
    this.blobsBundle = blobsBundle;
    this.shouldOverrideBuilder = false;
  }

  /**
   * Gets execution payload.
   *
   * @return the execution payload
   */
  @JsonGetter(value = "executionPayload")
  public PayloadResult getExecutionPayload() {
    return executionPayload;
  }

  /**
   * Gets block value.
   *
   * @return the block value
   */
  @JsonGetter(value = "blockValue")
  public String getBlockValue() {
    return blockValue;
  }

  /**
   * Gets blobs bundle.
   *
   * @return the blobs bundle
   */
  @JsonGetter(value = "blobsBundle")
  public BlobsBundleV1 getBlobsBundle() {
    return blobsBundle;
  }

  /**
   * Should override builder boolean.
   *
   * @return the boolean
   */
  @JsonGetter(value = "shouldOverrideBuilder")
  public boolean shouldOverrideBuilder() {
    return shouldOverrideBuilder;
  }

  /** The type Payload result. */
  public static class PayloadResult {

    /** The Block hash. */
    protected final String blockHash;

    private final String parentHash;
    private final String feeRecipient;
    private final String stateRoot;
    private final String receiptsRoot;
    private final String logsBloom;
    private final String prevRandao;
    private final String blockNumber;
    private final String gasLimit;
    private final String gasUsed;
    private final String timestamp;
    private final String extraData;
    private final String baseFeePerGas;
    private final String excessBlobGas;
    private final String blobGasUsed;
    private final String parentBeaconBlockRoot;

    /** The Transactions. */
    protected final List<String> transactions;

    private final List<WithdrawalParameter> withdrawals;
    private final List<DepositParameter> deposits;
    private final List<WithdrawalRequestParameter> withdrawalRequests;

    /**
     * Instantiates a new Payload result.
     *
     * @param header the header
     * @param transactions the transactions
     * @param withdrawals the withdrawals
     * @param deposits the deposits
     * @param withdrawalRequests the withdrawal requests
     */
    public PayloadResult(
        final BlockHeader header,
        final List<String> transactions,
        final Optional<List<Withdrawal>> withdrawals,
        final Optional<List<Deposit>> deposits,
        final Optional<List<WithdrawalRequest>> withdrawalRequests) {
      this.blockNumber = Quantity.create(header.getNumber());
      this.blockHash = header.getHash().toString();
      this.parentHash = header.getParentHash().toString();
      this.logsBloom = header.getLogsBloom().toString();
      this.stateRoot = header.getStateRoot().toString();
      this.receiptsRoot = header.getReceiptsRoot().toString();
      this.extraData = header.getExtraData().toString();
      this.baseFeePerGas = header.getBaseFee().map(Quantity::create).orElse(null);
      this.gasLimit = Quantity.create(header.getGasLimit());
      this.gasUsed = Quantity.create(header.getGasUsed());
      this.timestamp = Quantity.create(header.getTimestamp());
      this.transactions = transactions;
      this.feeRecipient = header.getCoinbase().toString();
      this.prevRandao = header.getPrevRandao().map(Bytes32::toHexString).orElse(null);
      this.withdrawals =
          withdrawals
              .map(
                  ws ->
                      ws.stream()
                          .map(WithdrawalParameter::fromWithdrawal)
                          .collect(Collectors.toList()))
              .orElse(null);
      this.deposits =
          deposits
              .map(
                  ds -> ds.stream().map(DepositParameter::fromDeposit).collect(Collectors.toList()))
              .orElse(null);
      this.withdrawalRequests =
          withdrawalRequests
              .map(
                  wr ->
                      wr.stream()
                          .map(WithdrawalRequestParameter::fromWithdrawalRequest)
                          .collect(Collectors.toList()))
              .orElse(null);
      this.blobGasUsed = header.getBlobGasUsed().map(Quantity::create).orElse(Quantity.HEX_ZERO);
      this.excessBlobGas =
          header.getExcessBlobGas().map(Quantity::create).orElse(Quantity.HEX_ZERO);
      this.parentBeaconBlockRoot =
          header.getParentBeaconBlockRoot().map(Bytes32::toHexString).orElse(null);
    }

    /**
     * Gets number.
     *
     * @return the number
     */
    @JsonGetter(value = "blockNumber")
    public String getNumber() {
      return blockNumber;
    }

    /**
     * Gets hash.
     *
     * @return the hash
     */
    @JsonGetter(value = "blockHash")
    public String getHash() {
      return blockHash;
    }

    /**
     * Gets parent hash.
     *
     * @return the parent hash
     */
    @JsonGetter(value = "parentHash")
    public String getParentHash() {
      return parentHash;
    }

    /**
     * Gets logs bloom.
     *
     * @return the logs bloom
     */
    @JsonGetter(value = "logsBloom")
    public String getLogsBloom() {
      return logsBloom;
    }

    /**
     * Gets prev randao.
     *
     * @return the prev randao
     */
    @JsonGetter(value = "prevRandao")
    public String getPrevRandao() {
      return prevRandao;
    }

    /**
     * Gets state root.
     *
     * @return the state root
     */
    @JsonGetter(value = "stateRoot")
    public String getStateRoot() {
      return stateRoot;
    }

    /**
     * Gets receipt root.
     *
     * @return the receipt root
     */
    @JsonGetter(value = "receiptsRoot")
    public String getReceiptRoot() {
      return receiptsRoot;
    }

    /**
     * Gets extra data.
     *
     * @return the extra data
     */
    @JsonGetter(value = "extraData")
    public String getExtraData() {
      return extraData;
    }

    /**
     * Gets base fee per gas.
     *
     * @return the base fee per gas
     */
    @JsonGetter(value = "baseFeePerGas")
    public String getBaseFeePerGas() {
      return baseFeePerGas;
    }

    /**
     * Gets gas limit.
     *
     * @return the gas limit
     */
    @JsonGetter(value = "gasLimit")
    public String getGasLimit() {
      return gasLimit;
    }

    /**
     * Gets gas used.
     *
     * @return the gas used
     */
    @JsonGetter(value = "gasUsed")
    public String getGasUsed() {
      return gasUsed;
    }

    /**
     * Gets timestamp.
     *
     * @return the timestamp
     */
    @JsonGetter(value = "timestamp")
    public String getTimestamp() {
      return timestamp;
    }

    /**
     * Gets transactions.
     *
     * @return the transactions
     */
    @JsonGetter(value = "transactions")
    public List<String> getTransactions() {
      return transactions;
    }

    /**
     * Gets withdrawals.
     *
     * @return the withdrawals
     */
    @JsonGetter(value = "withdrawals")
    public List<WithdrawalParameter> getWithdrawals() {
      return withdrawals;
    }

    /**
     * Gets deposits.
     *
     * @return the deposits
     */
    @JsonGetter(value = "depositReceipts")
    public List<DepositParameter> getDeposits() {
      return deposits;
    }

    /**
     * Gets withdrawal requests.
     *
     * @return the withdrawal requests
     */
    @JsonGetter(value = "withdrawalRequests")
    public List<WithdrawalRequestParameter> getWithdrawalRequests() {
      return withdrawalRequests;
    }

    /**
     * Gets fee recipient.
     *
     * @return the fee recipient
     */
    @JsonGetter(value = "feeRecipient")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getFeeRecipient() {
      return feeRecipient;
    }

    /**
     * Gets excess blob gas.
     *
     * @return the excess blob gas
     */
    @JsonGetter(value = "excessBlobGas")
    public String getExcessBlobGas() {
      return excessBlobGas;
    }

    /**
     * Gets blob gas useds.
     *
     * @return the blob gas useds
     */
    @JsonGetter(value = "blobGasUsed")
    public String getBlobGasUseds() {
      return blobGasUsed;
    }

    /**
     * Gets parent beacon block root.
     *
     * @return the parent beacon block root
     */
    @JsonGetter(value = "parentBeaconBlockRoot")
    public String getParentBeaconBlockRoot() {
      return parentBeaconBlockRoot;
    }
  }
}
