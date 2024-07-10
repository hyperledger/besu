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
package org.hyperledger.besu.evmtool;

import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_PROTECTED_V_MIN;
import static org.hyperledger.besu.ethereum.core.Transaction.REPLAY_UNPROTECTED_V_BASE;
import static org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator.calculateExcessBlobGasForParent;
import static org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules.shouldClearEmptyAccounts;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.DepositRequest;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.requests.ProcessRequestContext;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestUtil;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.referencetests.BonsaiReferenceTestWorldState;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestEnv;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestWorldState;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedAccount;
import org.hyperledger.besu.ethereum.vm.CachingBlockHashLookup;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evmtool.exception.UnsupportedForkException;
import org.hyperledger.besu.evmtool.t8n.T8nBlockchain;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Stopwatch;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * The T8nExecutor class is responsible for executing transactions in the context of the Ethereum
 * Virtual Machine (EVM). It extracts transactions from a given input, runs tests on them, and
 * generates results including stateRoot, txRoot, receiptsRoot, and logsHash. It also handles block
 * rewards and withdrawal processing. This class is part of the EVM tooling within the Hyperledger
 * Besu project.
 */
public class T8nExecutor {

  private static final Set<Address> EMPTY_ADDRESS_SET = Set.of();

  /**
   * A record that represents a transaction that has been rejected. It contains the index of the
   * transaction and the error message explaining why it was rejected.
   *
   * @param index The index of the rejected transaction.
   * @param error The error message explaining why the transaction was rejected.
   */
  public record RejectedTransaction(int index, String error) {}

  /**
   * Default constructor for the T8nExecutor class. This constructor does not perform any
   * operations.
   */
  public T8nExecutor() {
    // Default constructor required for Javadoc linting
  }

  /**
   * Extracts transactions from a given JSON iterator and adds them to the provided transactions
   * list. If a transaction cannot be parsed or is invalid, it is added to the rejections list with
   * its index and error message.
   *
   * @param out PrintWriter used for outputting information or errors.
   * @param it Iterator over JSON nodes, each representing a transaction.
   * @param transactions List of transactions to which parsed transactions are added.
   * @param rejections List of RejectedTransaction records to which rejected transactions are added.
   * @return The updated list of transactions after parsing and validation.
   */
  protected static List<Transaction> extractTransactions(
      final PrintWriter out,
      final Iterator<JsonNode> it,
      final List<Transaction> transactions,
      final List<RejectedTransaction> rejections) {
    int i = 0;
    while (it.hasNext()) {
      try {
        JsonNode txNode = it.next();
        if (txNode.isTextual()) {
          BytesValueRLPInput rlpInput =
              new BytesValueRLPInput(Bytes.fromHexString(txNode.asText()), false);
          rlpInput.enterList();
          while (!rlpInput.isEndOfCurrentList()) {
            Transaction tx = Transaction.readFrom(rlpInput);
            transactions.add(tx);
          }
        } else if (txNode.isObject()) {
          if (txNode.has("txBytes")) {
            Transaction tx =
                Transaction.readFrom(Bytes.fromHexString(txNode.get("txbytes").asText()));
            transactions.add(tx);
          } else {
            Transaction.Builder builder = Transaction.builder();
            int type = Bytes.fromHexStringLenient(txNode.get("type").textValue()).toInt();
            BigInteger chainId =
                Bytes.fromHexStringLenient(txNode.get("chainId").textValue())
                    .toUnsignedBigInteger();
            TransactionType transactionType = TransactionType.of(type == 0 ? 0xf8 : type);
            builder.type(transactionType);
            builder.nonce(Bytes.fromHexStringLenient(txNode.get("nonce").textValue()).toLong());
            builder.gasLimit(Bytes.fromHexStringLenient(txNode.get("gas").textValue()).toLong());
            builder.value(Wei.fromHexString(txNode.get("value").textValue()));
            builder.payload(Bytes.fromHexString(txNode.get("input").textValue()));

            if (txNode.has("gasPrice")) {
              builder.gasPrice(Wei.fromHexString(txNode.get("gasPrice").textValue()));
            }
            if (txNode.has("maxPriorityFeePerGas")) {
              builder.maxPriorityFeePerGas(
                  Wei.fromHexString(txNode.get("maxPriorityFeePerGas").textValue()));
            }
            if (txNode.has("maxFeePerGas")) {
              builder.maxFeePerGas(Wei.fromHexString(txNode.get("maxFeePerGas").textValue()));
            }
            if (txNode.has("maxFeePerBlobGas")) {
              builder.maxFeePerBlobGas(
                  Wei.fromHexString(txNode.get("maxFeePerBlobGas").textValue()));
            }

            if (txNode.has("to")) {
              builder.to(Address.fromHexString(txNode.get("to").textValue()));
            }
            BigInteger v =
                Bytes.fromHexStringLenient(txNode.get("v").textValue()).toUnsignedBigInteger();
            if (transactionType.requiresChainId() || (v.compareTo(REPLAY_PROTECTED_V_MIN) > 0)) {
              // chainid if protected
              builder.chainId(chainId);
            }

            if (txNode.has("accessList")) {
              JsonNode accessList = txNode.get("accessList");
              if (!accessList.isArray()) {
                out.printf(
                    "TX json node unparseable: expected accessList to be an array - %s%n", txNode);
                continue;
              }
              List<AccessListEntry> entries = new ArrayList<>(accessList.size());
              for (JsonNode entryAsJson : accessList) {
                Address address = Address.fromHexString(entryAsJson.get("address").textValue());
                List<String> storageKeys =
                    StreamSupport.stream(
                            Spliterators.spliteratorUnknownSize(
                                entryAsJson.get("storageKeys").elements(), Spliterator.ORDERED),
                            false)
                        .map(JsonNode::textValue)
                        .toList();
                AccessListEntry accessListEntry =
                    AccessListEntry.createAccessListEntry(address, storageKeys);
                entries.add(accessListEntry);
              }
              builder.accessList(entries);
            }

            if (txNode.has("blobVersionedHashes")) {
              JsonNode blobVersionedHashes = txNode.get("blobVersionedHashes");
              if (!blobVersionedHashes.isArray()) {
                out.printf(
                    "TX json node unparseable: expected blobVersionedHashes to be an array - %s%n",
                    txNode);
                continue;
              }

              List<VersionedHash> entries = new ArrayList<>(blobVersionedHashes.size());
              for (JsonNode versionedHashNode : blobVersionedHashes) {
                entries.add(
                    new VersionedHash(Bytes32.fromHexString(versionedHashNode.textValue())));
              }
              builder.versionedHashes(entries);
            }

            if (txNode.has("secretKey")) {
              SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
              KeyPair keys =
                  signatureAlgorithm.createKeyPair(
                      signatureAlgorithm.createPrivateKey(
                          Bytes32.fromHexString(txNode.get("secretKey").textValue())));

              transactions.add(builder.signAndBuild(keys));
            } else {
              if (transactionType == TransactionType.FRONTIER) {
                if (v.compareTo(REPLAY_PROTECTED_V_MIN) > 0) {
                  v =
                      v.subtract(REPLAY_PROTECTED_V_BASE)
                          .subtract(chainId.multiply(BigInteger.TWO));
                } else {
                  v = v.subtract(REPLAY_UNPROTECTED_V_BASE);
                }
              }
              builder.signature(
                  SignatureAlgorithmFactory.getInstance()
                      .createSignature(
                          Bytes.fromHexStringLenient(txNode.get("r").textValue())
                              .toUnsignedBigInteger(),
                          Bytes.fromHexStringLenient(txNode.get("s").textValue())
                              .toUnsignedBigInteger(),
                          v.byteValueExact()));
              transactions.add(builder.build());
            }
          }
        } else {
          out.printf("TX json node unparseable: %s%n", txNode);
        }
      } catch (IllegalArgumentException iae) {
        rejections.add(new RejectedTransaction(i, iae.getMessage()));
      }
      i++;
    }
    return transactions;
  }

  static T8nResult runTest(
      final Long chainId,
      final String fork,
      final String rewardString,
      final ObjectMapper objectMapper,
      final ReferenceTestEnv referenceTestEnv,
      final ReferenceTestWorldState initialWorldState,
      final List<Transaction> transactions,
      final List<RejectedTransaction> rejections,
      final TracerManager tracerManager) {

    final ReferenceTestProtocolSchedules referenceTestProtocolSchedules =
        ReferenceTestProtocolSchedules.create(
            new StubGenesisConfigOptions().chainId(BigInteger.valueOf(chainId)));

    final BonsaiReferenceTestWorldState worldState =
        (BonsaiReferenceTestWorldState) initialWorldState.copy();

    final ProtocolSchedule protocolSchedule = referenceTestProtocolSchedules.getByName(fork);
    if (protocolSchedule == null) {
      throw new UnsupportedForkException(fork);
    }

    ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(referenceTestEnv);
    Blockchain blockchain = new T8nBlockchain(referenceTestEnv, protocolSpec);
    final BlockHeader blockHeader = referenceTestEnv.parentBlockHeader(protocolSpec);
    final MainnetTransactionProcessor processor = protocolSpec.getTransactionProcessor();
    final Wei blobGasPrice =
        protocolSpec
            .getFeeMarket()
            .blobGasPricePerGas(calculateExcessBlobGasForParent(protocolSpec, blockHeader));
    long blobGasLimit = protocolSpec.getGasLimitCalculator().currentBlobGasLimit();

    if (!referenceTestEnv.isStateTest()) {
      protocolSpec
          .getBlockHashProcessor()
          .processBlockHashes(blockchain, worldState, referenceTestEnv);
    }

    final WorldUpdater rootWorldStateUpdater = worldState.updater();
    List<TransactionReceipt> receipts = new ArrayList<>();
    List<RejectedTransaction> invalidTransactions = new ArrayList<>(rejections);
    List<Transaction> validTransactions = new ArrayList<>();
    ArrayNode receiptsArray = objectMapper.createArrayNode();
    long gasUsed = 0;
    long blobGasUsed = 0;
    final WorldUpdater worldStateUpdater = rootWorldStateUpdater.updater();
    for (int transactionIndex = 0; transactionIndex < transactions.size(); transactionIndex++) {
      worldStateUpdater.markTransactionBoundary();
      Transaction transaction = transactions.get(transactionIndex);
      final Stopwatch timer = Stopwatch.createStarted();

      GasCalculator gasCalculator = protocolSpec.getGasCalculator();
      int blobCount = transaction.getBlobCount();
      blobGasUsed += gasCalculator.blobGasCost(blobCount);
      if (blobGasUsed > blobGasLimit) {
        invalidTransactions.add(
            new RejectedTransaction(
                transactionIndex,
                String.format(
                    "blob gas (%d) would exceed block maximum %d", blobGasUsed, blobGasLimit)));
        continue;
      }
      final OperationTracer tracer; // You should have picked Mercy.

      final TransactionProcessingResult result;
      try {
        tracer = tracerManager.getManagedTracer(transactionIndex, transaction.getHash());
        tracer.tracePrepareTransaction(worldStateUpdater, transaction);
        tracer.traceStartTransaction(worldStateUpdater, transaction);
        result =
            processor.processTransaction(
                worldStateUpdater,
                blockHeader,
                transaction,
                blockHeader.getCoinbase(),
                number -> referenceTestEnv.getBlockhashByNumber(number).orElse(Hash.ZERO),
                false,
                TransactionValidationParams.processingBlock(),
                tracer,
                blobGasPrice);
        tracerManager.disposeTracer(tracer);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      timer.stop();

      if (shouldClearEmptyAccounts(fork)) {
        var entries = new ArrayList<>(worldState.getAccumulator().getAccountsToUpdate().entrySet());
        for (var entry : entries) {
          DiffBasedAccount updated = entry.getValue().getUpdated();
          if (updated != null && updated.isEmpty()) {
            worldState.getAccumulator().deleteAccount(entry.getKey());
          }
        }
      }
      if (result.isInvalid()) {
        invalidTransactions.add(
            new RejectedTransaction(
                transactionIndex, result.getValidationResult().getErrorMessage()));
        continue;
      }
      validTransactions.add(transaction);

      long transactionGasUsed = transaction.getGasLimit() - result.getGasRemaining();

      gasUsed += transactionGasUsed;
      long intrinsicGas =
          gasCalculator.transactionIntrinsicGasCost(
              transaction.getPayload(), transaction.getTo().isEmpty());
      TransactionReceipt receipt =
          protocolSpec
              .getTransactionReceiptFactory()
              .create(transaction.getType(), result, worldState, gasUsed);
      tracer.traceEndTransaction(
          worldStateUpdater,
          transaction,
          result.isSuccessful(),
          result.getOutput(),
          result.getLogs(),
          gasUsed - intrinsicGas,
          EMPTY_ADDRESS_SET,
          timer.elapsed(TimeUnit.NANOSECONDS));
      Bytes gasUsedInTransaction = Bytes.ofUnsignedLong(transactionGasUsed);
      receipts.add(receipt);
      ObjectNode receiptObject = receiptsArray.addObject();
      receiptObject.put(
          "root", receipt.getStateRoot() == null ? "0x" : receipt.getStateRoot().toHexString());
      int status = receipt.getStatus();
      receiptObject.put("status", "0x" + Math.max(status, 0));
      receiptObject.put("cumulativeGasUsed", Bytes.ofUnsignedLong(gasUsed).toQuantityHexString());
      receiptObject.put("logsBloom", receipt.getBloomFilter().toHexString());
      if (result.getLogs().isEmpty()) {
        receiptObject.putNull("logs");
      } else {
        ArrayNode logsArray = receiptObject.putArray("logs");
        List<Log> logs = result.getLogs();
        for (int logIndex = 0; logIndex < logs.size(); logIndex++) {
          Log log = logs.get(logIndex);
          var obj = logsArray.addObject();
          obj.put("address", log.getLogger().toHexString());
          var topics = obj.putArray("topics");
          log.getTopics().forEach(topic -> topics.add(topic.toHexString()));
          obj.put("data", log.getData().toHexString());
          obj.put("blockNumber", blockHeader.getNumber());
          obj.put("transactionHash", transaction.getHash().toHexString());
          obj.put("transactionIndex", String.format("0x%x", transactionIndex));
          obj.put("blockHash", blockHeader.getHash().toHexString());
          obj.put("logIndex", String.format("0x%x", logIndex));
          obj.put("removed", "false");
        }
      }
      receiptObject.put("transactionHash", transaction.getHash().toHexString());
      receiptObject.put(
          "contractAddress", transaction.contractAddress().orElse(Address.ZERO).toHexString());
      receiptObject.put("gasUsed", gasUsedInTransaction.toQuantityHexString());
      receiptObject.put("blockHash", Hash.ZERO.toHexString());
      receiptObject.put(
          "transactionIndex", Bytes.ofUnsignedLong(transactionIndex).toQuantityHexString());
      worldStateUpdater.commit();
    }

    final ObjectNode resultObject = objectMapper.createObjectNode();

    // block reward
    // The max production reward was 5 Eth, longs can hold over 18 Eth.
    if (!referenceTestEnv.isStateTest()
        && !validTransactions.isEmpty()
        && (rewardString == null || Long.decode(rewardString) > 0)) {
      Wei reward =
          (rewardString == null)
              ? protocolSpec.getBlockReward()
              : Wei.of(Long.decode(rewardString));
      rootWorldStateUpdater
          .getOrCreateSenderAccount(blockHeader.getCoinbase())
          .incrementBalance(reward);
    }

    rootWorldStateUpdater.commit();

    if (referenceTestEnv.isStateTest()) {
      if (!referenceTestEnv.getWithdrawals().isEmpty()) {
        resultObject.put("exception", "withdrawals are not supported in state tests");
      }
    } else {
      // Invoke the withdrawal processor to handle CL withdrawals.
      if (!referenceTestEnv.getWithdrawals().isEmpty()) {
        try {
          protocolSpec
              .getWithdrawalsProcessor()
              .ifPresent(
                  p ->
                      p.processWithdrawals(
                          referenceTestEnv.getWithdrawals(), worldState.updater()));
        } catch (RuntimeException re) {
          resultObject.put("exception", re.getMessage());
        }
      }
    }

    var requestProcessorCoordinator = protocolSpec.getRequestProcessorCoordinator();
    if (requestProcessorCoordinator.isPresent()) {
      var rpc = requestProcessorCoordinator.get();
      ProcessRequestContext context =
          new ProcessRequestContext(
              blockHeader,
              worldState,
              protocolSpec,
              receipts,
              new CachingBlockHashLookup(blockHeader, blockchain),
              OperationTracer.NO_TRACING);
      Optional<List<Request>> maybeRequests = rpc.process(context);
      Hash requestRoot = BodyValidation.requestsRoot(maybeRequests.orElse(List.of()));

      resultObject.put("requestsRoot", requestRoot.toHexString());
      var deposits = resultObject.putArray("depositRequests");
      RequestUtil.filterRequestsOfType(maybeRequests.orElse(List.of()), DepositRequest.class)
          .forEach(
              deposit -> {
                var obj = deposits.addObject();
                obj.put("pubkey", deposit.getPubkey().toHexString());
                obj.put("withdrawalCredentials", deposit.getWithdrawalCredentials().toHexString());
                obj.put("amount", deposit.getAmount().toHexString());
                obj.put("signature", deposit.getSignature().toHexString());
                obj.put("index", deposit.getIndex().toHexString());
              });

      var withdrawlRequests = resultObject.putArray("withdrawalRequests");
      RequestUtil.filterRequestsOfType(maybeRequests.orElse(List.of()), WithdrawalRequest.class)
          .forEach(
              wr -> {
                var obj = withdrawlRequests.addObject();
                obj.put("sourceAddress", wr.getSourceAddress().toHexString());
                obj.put("validatorPubkey", wr.getValidatorPubkey().toHexString());
                obj.put("amount", wr.getAmount().toHexString());
              });
    }

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
        !blockHeader.getDifficultyBytes().trimLeadingZeros().isEmpty()
            ? blockHeader.getDifficultyBytes().toShortHexString()
            : null);
    resultObject.put("gasUsed", Bytes.ofUnsignedLong(gasUsed).toQuantityHexString());
    blockHeader
        .getBaseFee()
        .ifPresent(bf -> resultObject.put("currentBaseFee", bf.toQuantityHexString()));
    blockHeader
        .getWithdrawalsRoot()
        .ifPresent(wr -> resultObject.put("withdrawalsRoot", wr.toHexString()));
    var maybeExcessBlobGas = blockHeader.getExcessBlobGas();
    if (maybeExcessBlobGas.isPresent()) {
      resultObject.put(
          "currentExcessBlobGas",
          calculateExcessBlobGasForParent(protocolSpec, blockHeader)
              .toBytes()
              .toQuantityHexString());
      resultObject.put("blobGasUsed", Bytes.ofUnsignedLong(blobGasUsed).toQuantityHexString());
    }

    ObjectNode allocObject = objectMapper.createObjectNode();
    worldState
        .streamAccounts(Bytes32.ZERO, Integer.MAX_VALUE)
        .sorted(Comparator.comparing(o -> o.getAddress().get().toHexString()))
        .forEach(
            a -> {
              Account account = worldState.get(a.getAddress().get());
              ObjectNode accountObject = allocObject.putObject(account.getAddress().toHexString());
              if (account.getCode() != null && !account.getCode().isEmpty()) {
                accountObject.put("code", account.getCode().toHexString());
              }
              List<Entry<UInt256, UInt256>> storageEntries =
                  account.storageEntriesFrom(Bytes32.ZERO, Integer.MAX_VALUE).values().stream()
                      .map(
                          e ->
                              Map.entry(
                                  e.getKey().get(),
                                  account.getStorageValue(UInt256.fromBytes(e.getKey().get()))))
                      .filter(e -> !e.getValue().isZero())
                      .sorted(Map.Entry.comparingByKey())
                      .toList();
              if (!storageEntries.isEmpty()) {
                ObjectNode storageObject = accountObject.putObject("storage");
                storageEntries.forEach(
                    accountStorageEntry ->
                        storageObject.put(
                            accountStorageEntry.getKey().toHexString(),
                            accountStorageEntry.getValue().toHexString()));
              }
              accountObject.put("balance", account.getBalance().toShortHexString());
              if (account.getNonce() != 0) {
                accountObject.put(
                    "nonce", Bytes.ofUnsignedLong(account.getNonce()).toShortHexString());
              }
            });

    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    rlpOut.writeList(transactions, Transaction::writeTo);
    TextNode bodyBytes = TextNode.valueOf(rlpOut.encoded().toHexString());
    return new T8nResult(allocObject, bodyBytes, resultObject);
  }

  /**
   * The TracerManager interface provides methods for managing OperationTracer instances. It is used
   * in the context of Ethereum Virtual Machine (EVM) execution to trace operations.
   *
   * <p>The interface defines two methods: - getManagedTracer: This method is used to get a managed
   * OperationTracer instance for a specific transaction. - disposeTracer: This method is used to
   * dispose of an OperationTracer instance when it is no longer needed.
   */
  public interface TracerManager {

    /**
     * Retrieves a managed OperationTracer instance for a specific transaction.
     *
     * @param txIndex The index of the transaction for which the tracer is to be retrieved.
     * @param txHash The hash of the transaction for which the tracer is to be retrieved.
     * @return The managed OperationTracer instance.
     * @throws Exception If an error occurs while retrieving the tracer.
     */
    OperationTracer getManagedTracer(int txIndex, Hash txHash) throws Exception;

    /**
     * Disposes of an OperationTracer instance when it is no longer needed.
     *
     * @param tracer The OperationTracer instance to be disposed.
     * @throws IOException If an error occurs while disposing the tracer.
     */
    void disposeTracer(OperationTracer tracer) throws IOException;
  }

  /**
   * A record that represents the result of a transaction test run in the Ethereum Virtual Machine
   * (EVM). It contains the final state of the accounts (allocObject), the raw bytes of the
   * transactions (bodyBytes), and the result of the test run (resultObject).
   *
   * @param allocObject The final state of the accounts after the test run.
   * @param bodyBytes The raw bytes of the transactions that were run.
   * @param resultObject The result of the test run, including stateRoot, txRoot, receiptsRoot,
   *     logsHash, and other details.
   */
  @SuppressWarnings("unused")
  record T8nResult(ObjectNode allocObject, TextNode bodyBytes, ObjectNode resultObject) {}
}
