/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.evmtool;

import static org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules.shouldClearEmptyAccounts;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestEnv;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutableWorldState;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evmtool.exception.UnsupportedForkException;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Stopwatch;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class T8nExecutor {
  static T8nResult runTest(
      final Long chainId,
      final String fork,
      final String rewardString,
      final ObjectMapper objectMapper,
      final ReferenceTestEnv referenceTestEnv,
      final MutableWorldState initialWorldState,
      final List<Transaction> transactions,
      final TracerManager tracerManager) {

    final ReferenceTestProtocolSchedules referenceTestProtocolSchedules =
        ReferenceTestProtocolSchedules.create(
            new StubGenesisConfigOptions().chainId(BigInteger.valueOf(chainId)));

    final MutableWorldState worldState = new DefaultMutableWorldState(initialWorldState);

    final ProtocolSchedule protocolSchedule = referenceTestProtocolSchedules.getByName(fork);
    if (protocolSchedule == null) {
      throw new UnsupportedForkException(fork);
    }

    ProtocolSpec protocolSpec =
        protocolSchedule.getByBlockHeader(BlockHeaderBuilder.createDefault().buildBlockHeader());
    final BlockHeader blockHeader = referenceTestEnv.updateFromParentValues(protocolSpec);
    final MainnetTransactionProcessor processor = protocolSpec.getTransactionProcessor();
    final WorldUpdater worldStateUpdater = worldState.updater();
    final ReferenceTestBlockchain blockchain = new ReferenceTestBlockchain(blockHeader.getNumber());
    // Todo: EIP-4844 use the excessDataGas of the parent instead of DataGas.ZERO
    final Wei dataGasPrice = protocolSpec.getFeeMarket().dataPrice(DataGas.ZERO);

    List<TransactionReceipt> receipts = new ArrayList<>();
    List<T8nSubCommand.RejectedTransaction> invalidTransactions = new ArrayList<>();
    List<Transaction> validTransactions = new ArrayList<>();
    ArrayNode receiptsArray = objectMapper.createArrayNode();
    long gasUsed = 0;
    for (int i = 0; i < transactions.size(); i++) {
      Transaction transaction = transactions.get(i);

      final Stopwatch timer = Stopwatch.createStarted();
      final OperationTracer tracer; // You should have picked Mercy.

      final TransactionProcessingResult result;
      try {
        tracer = tracerManager.getManagedTracer(i, transaction.getHash());
        result =
            processor.processTransaction(
                blockchain,
                worldStateUpdater,
                blockHeader,
                transaction,
                blockHeader.getCoinbase(),
                blockNumber -> referenceTestEnv.getBlockhashByNumber(blockNumber).orElse(Hash.ZERO),
                false,
                TransactionValidationParams.processingBlock(),
                tracer,
                dataGasPrice);
        tracerManager.disposeTracer(tracer);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      timer.stop();

      if (shouldClearEmptyAccounts(fork)) {
        final Account coinbase = worldStateUpdater.getOrCreate(blockHeader.getCoinbase());
        if (coinbase != null && coinbase.isEmpty()) {
          worldStateUpdater.deleteAccount(coinbase.getAddress());
        }
        final Account txSender = worldStateUpdater.getAccount(transaction.getSender());
        if (txSender != null && txSender.isEmpty()) {
          worldStateUpdater.deleteAccount(txSender.getAddress());
        }
      }
      if (result.isInvalid()) {
        invalidTransactions.add(
            new T8nSubCommand.RejectedTransaction(
                i, result.getValidationResult().getErrorMessage()));
      } else {
        validTransactions.add(transaction);

        long transactionGasUsed = transaction.getGasLimit() - result.getGasRemaining();

        gasUsed += transactionGasUsed;
        long intrinsicGas =
            protocolSpec
                .getGasCalculator()
                .transactionIntrinsicGasCost(
                    transaction.getPayload(), transaction.getTo().isEmpty());
        tracer.traceEndTransaction(
            result.getOutput(), gasUsed - intrinsicGas, timer.elapsed(TimeUnit.NANOSECONDS));
        TransactionReceipt receipt =
            protocolSpec
                .getTransactionReceiptFactory()
                .create(transaction.getType(), result, worldState, gasUsed);
        Bytes gasUsedInTransaction = Bytes.ofUnsignedLong(transactionGasUsed);
        receipts.add(receipt);
        ObjectNode receiptObject = receiptsArray.addObject();
        receiptObject.put(
            "root", receipt.getStateRoot() == null ? "0x" : receipt.getStateRoot().toHexString());
        receiptObject.put("status", "0x" + receipt.getStatus());
        receiptObject.put("cumulativeGasUsed", Bytes.ofUnsignedLong(gasUsed).toQuantityHexString());
        receiptObject.put("logsBloom", receipt.getBloomFilter().toHexString());
        if (result.getLogs().isEmpty()) {
          receiptObject.putNull("logs");
        } else {
          ArrayNode logsArray = receiptObject.putArray("logs");
          for (Log log : result.getLogs()) {
            logsArray.addPOJO(log);
          }
        }
        receiptObject.put("transactionHash", transaction.getHash().toHexString());
        receiptObject.put(
            "contractAddress", transaction.contractAddress().orElse(Address.ZERO).toHexString());
        receiptObject.put("gasUsed", gasUsedInTransaction.toQuantityHexString());
        receiptObject.put("blockHash", Hash.ZERO.toHexString());
        receiptObject.put("transactionIndex", Bytes.ofUnsignedLong(i).toQuantityHexString());
      }
    }

    final ObjectNode resultObject = objectMapper.createObjectNode();

    // block reward
    // The max production reward was 5 Eth, longs can hold over 18 Eth.
    if (!validTransactions.isEmpty() && (rewardString == null || Long.decode(rewardString) > 0)) {
      Wei reward =
          (rewardString == null)
              ? protocolSpec.getBlockReward()
              : Wei.of(Long.decode(rewardString));
      worldStateUpdater
          .getOrCreateSenderAccount(blockHeader.getCoinbase())
          .getMutable()
          .incrementBalance(reward);
    }

    // Invoke the withdrawal processor to handle CL withdrawals.
    if (!referenceTestEnv.getWithdrawals().isEmpty()) {
      try {
        protocolSpec
            .getWithdrawalsProcessor()
            .ifPresent(
                p -> p.processWithdrawals(referenceTestEnv.getWithdrawals(), worldStateUpdater));
      } catch (RuntimeException re) {
        resultObject.put("exception", re.getMessage());
      }
    }

    worldStateUpdater.commit();
    worldState.persist(blockHeader);

    resultObject.put("stateRoot", worldState.rootHash().toHexString());
    resultObject.put("txRoot", BodyValidation.transactionsRoot(validTransactions).toHexString());
    resultObject.put("receiptsRoot", BodyValidation.receiptsRoot(receipts).toHexString());
    resultObject.put(
        "logsHash",
        Hash.hash(
                RLP.encode(
                    out ->
                        out.writeList(
                            receipts.stream().flatMap(r -> r.getLogsList().stream()).toList(),
                            Log::writeTo)))
            .toHexString());
    resultObject.put("logsBloom", BodyValidation.logsBloom(receipts).toHexString());
    resultObject.set("receipts", receiptsArray);
    if (!invalidTransactions.isEmpty()) {
      resultObject.putPOJO("rejected", invalidTransactions);
    }

    resultObject.put(
        "currentDifficulty",
        blockHeader.getDifficultyBytes().trimLeadingZeros().size() > 0
            ? blockHeader.getDifficultyBytes().toShortHexString()
            : null);
    resultObject.put("gasUsed", Bytes.ofUnsignedLong(gasUsed).toQuantityHexString());
    blockHeader
        .getBaseFee()
        .ifPresent(bf -> resultObject.put("currentBaseFee", bf.toQuantityHexString()));
    blockHeader
        .getWithdrawalsRoot()
        .ifPresent(wr -> resultObject.put("withdrawalsRoot", wr.toHexString()));

    ObjectNode allocObject = objectMapper.createObjectNode();
    worldState
        .streamAccounts(Bytes32.ZERO, Integer.MAX_VALUE)
        .sorted(Comparator.comparing(o -> o.getAddress().get().toHexString()))
        .forEach(
            account -> {
              ObjectNode accountObject =
                  allocObject.putObject(
                      account.getAddress().map(Address::toHexString).orElse("0x"));
              if (account.getCode() != null && account.getCode().size() > 0) {
                accountObject.put("code", account.getCode().toHexString());
              }
              NavigableMap<Bytes32, AccountStorageEntry> storageEntries =
                  account.storageEntriesFrom(Bytes32.ZERO, Integer.MAX_VALUE);
              if (!storageEntries.isEmpty()) {
                ObjectNode storageObject = accountObject.putObject("storage");
                storageEntries.values().stream()
                    .sorted(Comparator.comparing(a -> a.getKey().get()))
                    .forEach(
                        accountStorageEntry ->
                            storageObject.put(
                                accountStorageEntry.getKey().map(UInt256::toHexString).orElse("0x"),
                                accountStorageEntry.getValue().toHexString()));
              }
              accountObject.put("balance", account.getBalance().toShortHexString());
              if (account.getNonce() > 0) {
                accountObject.put(
                    "nonce", Bytes.ofUnsignedLong(account.getNonce()).toShortHexString());
              }
            });

    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    rlpOut.writeList(transactions, Transaction::writeTo);
    TextNode bodyBytes = TextNode.valueOf(rlpOut.encoded().toHexString());
    return new T8nResult(allocObject, bodyBytes, resultObject);
  }

  interface TracerManager {
    OperationTracer getManagedTracer(int txIndex, Hash txHash) throws Exception;

    void disposeTracer(OperationTracer tracer) throws IOException;
  }

  @SuppressWarnings("unused")
  record T8nResult(ObjectNode allocObject, TextNode bodyBytes, ObjectNode resultObject) {}
}
