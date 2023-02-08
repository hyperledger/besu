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
 *
 */

package org.hyperledger.besu.evmtool;

import static org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules.shouldClearEmptyAccounts;
import static org.hyperledger.besu.evmtool.T8nSubCommand.COMMAND_ALIAS;
import static org.hyperledger.besu.evmtool.T8nSubCommand.COMMAND_NAME;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.HeaderBasedProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestEnv;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestWorldState;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutableWorldState;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evmtool.exception.UnsupportedForkException;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.util.Log4j2ConfiguratorUtil;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
    name = COMMAND_NAME,
    aliases = COMMAND_ALIAS,
    description = "Execute an Ethereum State Test.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class T8nSubCommand implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(T8nSubCommand.class);

  static final String COMMAND_NAME = "transition";
  static final String COMMAND_ALIAS = "t8n";
  private static final Path stdoutPath = Path.of("stdout");
  private static final Path stdinPath = Path.of("stdin");

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @Option(
      names = {"--state.fork"},
      paramLabel = "fork name",
      description = "The fork to run the transition against")
  private String fork = null;

  @Option(
      names = {"--input.env"},
      paramLabel = "full path",
      description = "The block environment for the transition")
  private final Path env = stdinPath;

  @Option(
      names = {"--input.alloc"},
      paramLabel = "full path",
      description = "The account state for the transition")
  private final Path alloc = stdinPath;

  @Option(
      names = {"--input.txs"},
      paramLabel = "full path",
      description = "The transactions to transition")
  private final Path txs = stdinPath;

  @Option(
      names = {"--output.basedir"},
      paramLabel = "full path",
      description = "The output ")
  private final Path outDir = Path.of(".");

  @Option(
      names = {"--output.alloc"},
      paramLabel = "file name",
      description = "The account state after the transition")
  private final Path outAlloc = Path.of("alloc.json");

  @Option(
      names = {"--output.result"},
      paramLabel = "file name",
      description = "The summary of the transition")
  private final Path outResult = Path.of("result.json");

  @SuppressWarnings("UnusedVariable")
  @Option(
      names = {"--output.body"},
      paramLabel = "file name",
      description = "RLP of transactions considered")
  private final Path outBody = Path.of("txs.rlp");

  @Option(
      names = {"--state.chainid"},
      paramLabel = "chain ID",
      description = "The chain Id to use")
  private final Long chainId = 1L;

  @SuppressWarnings("UnusedVariable")
  @Option(
      names = {"--state.reward"},
      paramLabel = "block mining reward",
      description = "The block reward to use in block tess")
  private final Wei reward = null;

  @ParentCommand private final EvmToolCommand parentCommand;

  @SuppressWarnings("unused")
  public T8nSubCommand() {
    // PicoCLI requires this
    parentCommand = null;
  }

  @SuppressWarnings("unused")
  public T8nSubCommand(final EvmToolCommand parentCommand) {
    // PicoCLI requires this too
    this.parentCommand = parentCommand;
  }

  @Override
  public void run() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setDefaultPrettyPrinter(
        (new DefaultPrettyPrinter())
            .withSpacesInObjectEntries()
            .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent(" "))
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent(" ")));
    final ObjectReader t8nReader = objectMapper.reader();
    objectMapper.disable(Feature.AUTO_CLOSE_SOURCE);

    MutableWorldState initialWorldState;
    ReferenceTestEnv referenceTestEnv;
    List<Transaction> transactions;
    try {
      ObjectNode config;
      if (env.equals(stdinPath) || alloc.equals(stdinPath) || txs.equals(stdinPath)) {
        config =
            (ObjectNode)
                t8nReader.readTree(new InputStreamReader(parentCommand.in, StandardCharsets.UTF_8));
      } else {
        config = objectMapper.createObjectNode();
      }

      if (!env.equals(stdinPath)) {
        config.set("env", t8nReader.readTree(new FileReader(env.toFile(), StandardCharsets.UTF_8)));
      }
      if (!alloc.equals(stdinPath)) {
        config.set(
            "alloc", t8nReader.readTree(new FileReader(alloc.toFile(), StandardCharsets.UTF_8)));
      }
      if (!txs.equals(stdinPath)) {
        config.set("txs", t8nReader.readTree(new FileReader(txs.toFile(), StandardCharsets.UTF_8)));
      }

      referenceTestEnv = objectMapper.convertValue(config.get("env"), ReferenceTestEnv.class);
      initialWorldState =
          objectMapper.convertValue(config.get("alloc"), ReferenceTestWorldState.class);
      initialWorldState.persist(null);
      Iterator<JsonNode> it = config.get("txs").elements();

      transactions = extractTransactions(it);
      if (!outDir.toString().isBlank()) {
        outDir.toFile().mkdirs();
      }
    } catch (final JsonProcessingException jpe) {
      parentCommand.out.println("File content error: " + jpe);
      jpe.printStackTrace();
      return;
    } catch (final IOException e) {
      LOG.error("Unable to read state file", e);
      return;
    }

    Log4j2ConfiguratorUtil.setLevel(
        "org.hyperledger.besu.ethereum.mainnet.AbstractProtocolScheduleBuilder", Level.OFF);
    final var referenceTestProtocolSchedules =
        ReferenceTestProtocolSchedules.create(
            new StubGenesisConfigOptions().chainId(BigInteger.valueOf(chainId)));
    Log4j2ConfiguratorUtil.setLevel(
        "org.hyperledger.besu.ethereum.mainnet.AbstractProtocolScheduleBuilder", null);

    final MutableWorldState worldState = new DefaultMutableWorldState(initialWorldState);

    final HeaderBasedProtocolSchedule protocolSchedule =
        referenceTestProtocolSchedules.getByName(fork);
    if (protocolSchedule == null) {
      throw new UnsupportedForkException(fork);
    }

    ProtocolSpec protocolSpec =
        protocolSchedule.getByBlockHeader(BlockHeaderBuilder.createDefault().buildBlockHeader());
    final BlockHeader blockHeader = referenceTestEnv.updateFromParentValues(protocolSpec);
    final MainnetTransactionProcessor processor = protocolSpec.getTransactionProcessor();
    final WorldUpdater worldStateUpdater = worldState.updater();
    final ReferenceTestBlockchain blockchain = new ReferenceTestBlockchain(blockHeader.getNumber());

    List<TransactionReceipt> receipts = new ArrayList<>();
    var receiptsArray = objectMapper.createArrayNode();
    long gasUsed = 0;
    for (int i = 0; i < transactions.size(); i++) {
      Transaction transaction = transactions.get(i);
      final Stopwatch timer = Stopwatch.createStarted();
      final OperationTracer tracer; // You should have picked Mercy.
      if (parentCommand.showJsonResults) {
        Path tracePath =
            outDir.resolve(
                String.format("trace-%d-%s.jsonl", i, transaction.getHash().toHexString()));
        try {
          tracer =
              new StandardJsonTracer(
                  new PrintStream(new FileOutputStream(tracePath.toFile())),
                  parentCommand.showMemory,
                  true);
        } catch (FileNotFoundException e) {
          throw new RuntimeException(e);
        }
      } else {
        tracer = OperationTracer.NO_TRACING;
      }
      final TransactionProcessingResult result =
          processor.processTransaction(
              blockchain,
              worldStateUpdater,
              blockHeader,
              transaction,
              blockHeader.getCoinbase(),
              new BlockHashLookup(referenceTestEnv, blockchain),
              false,
              TransactionValidationParams.processingBlock(),
              tracer);
      timer.stop();
      if (shouldClearEmptyAccounts(fork)) {
        final Account coinbase = worldStateUpdater.getOrCreate(blockHeader.getCoinbase());
        if (coinbase != null && coinbase.isEmpty()) {
          worldStateUpdater.deleteAccount(coinbase.getAddress());
        }
        final Account sender = worldStateUpdater.getAccount(transaction.getSender());
        if (sender != null && sender.isEmpty()) {
          worldStateUpdater.deleteAccount(sender.getAddress());
        }
      }
      long transactionGasUsed = transaction.getGasLimit() - result.getGasRemaining();

      gasUsed += transactionGasUsed;
      long intrinsicGas =
          protocolSpec
              .getGasCalculator()
              .transactionIntrinsicGasCost(transaction.getPayload(), transaction.getTo().isEmpty());
      tracer.traceEndTransaction(
          result.getOutput(), gasUsed - intrinsicGas, timer.elapsed(TimeUnit.NANOSECONDS));
      var receipt =
          protocolSpec
              .getTransactionReceiptFactory()
              .create(transaction.getType(), result, worldState, transactionGasUsed);
      Bytes gasUsedInTransaction = Bytes.ofUnsignedLong(transactionGasUsed);
      receipts.add(receipt);
      var receiptObject = receiptsArray.addObject();
      receiptObject.put(
          "root", receipt.getStateRoot() == null ? "0x" : receipt.getStateRoot().toHexString());
      receiptObject.put("status", "0x" + receipt.getStatus());
      receiptObject.put("cumulativeGasUsed", gasUsedInTransaction.toShortHexString());
      receiptObject.put("logsBloom", receipt.getBloomFilter().toHexString());
      if (result.getLogs().isEmpty()) {
        receiptObject.putNull("logs");
      } else {
        // FIXME do logs
      }
      receiptObject.put("transactionHash", transaction.getHash().toHexString());
      receiptObject.put(
          "contractAddress", transaction.contractAddress().orElse(Address.ZERO).toHexString());
      receiptObject.put("gasUsed", gasUsedInTransaction.toQuantityHexString());
      receiptObject.put("blockHash", Hash.ZERO.toHexString());
      receiptObject.put("transactionIndex", Bytes.ofUnsignedLong(i).toQuantityHexString());
    }
    worldStateUpdater.commit();
    worldState.persist(blockHeader);

    final ObjectNode resultObject = objectMapper.createObjectNode();
    resultObject.put("stateRoot", worldState.rootHash().toHexString());
    resultObject.put("txRoot", BodyValidation.transactionsRoot(transactions).toHexString());
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
    resultObject.put("currentDifficulty", blockHeader.getDifficultyBytes().toShortHexString());
    resultObject.put("gasUsed", Bytes.ofUnsignedLong(gasUsed).toQuantityHexString());

    var allocObject = objectMapper.createObjectNode();
    worldState
        .streamAccounts(Bytes32.ZERO, Integer.MAX_VALUE)
        .sorted(Comparator.comparing(o -> o.getAddress().get().toHexString()))
        .forEach(
            account -> {
              var accountObject =
                  allocObject.putObject(
                      account.getAddress().map(Address::toHexString).orElse("0x"));
              if (account.getNonce() > 0) {
                accountObject.put(
                    "nonce", Bytes.ofUnsignedLong(account.getNonce()).toShortHexString());
              }
              accountObject.put("balance", account.getBalance().toShortHexString());
              if (account.getCode() != null && account.getCode().size() > 0) {
                accountObject.put("code", account.getCode().toHexString());
              }
              var storageEntries = account.storageEntriesFrom(Bytes32.ZERO, Integer.MAX_VALUE);
              var storageObject = accountObject.putObject("storage");
              storageEntries
                  .values()
                  .forEach(
                      accountStorageEntry ->
                          storageObject.put(
                              accountStorageEntry.getKey().map(UInt256::toHexString).orElse("0x"),
                              accountStorageEntry.getValue().toHexString()));
            });

    try {
      var writer = objectMapper.writerWithDefaultPrettyPrinter();
      ObjectNode outputObject = objectMapper.createObjectNode();

      if (outAlloc.equals(stdoutPath)) {
        outputObject.set("alloc", allocObject);
      } else {
        try (var fileOut =
            new PrintStream(new FileOutputStream(outDir.resolve(outAlloc).toFile()))) {
          fileOut.println(writer.writeValueAsString(allocObject));
        }
      }

      BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
      rlpOut.writeList(transactions, Transaction::writeTo);
      Bytes bodyBytes = rlpOut.encoded();

      if (outBody.equals((stdoutPath))) {
        outputObject.set("body", TextNode.valueOf(bodyBytes.toHexString()));
      } else {
        try (var fileOut =
            new PrintStream(new FileOutputStream(outDir.resolve(outBody).toFile()))) {
          fileOut.print(bodyBytes.toHexString());
        }
      }

      if (outResult.equals(stdoutPath)) {
        outputObject.set("result", resultObject);
      } else {
        try (var fileOut =
            new PrintStream(new FileOutputStream(outDir.resolve(outResult).toFile()))) {
          fileOut.println(writer.writeValueAsString(resultObject));
        }
      }

      if (outputObject.size() > 0) {
        parentCommand.out.println(writer.writeValueAsString(outputObject));
      }
    } catch (IOException ioe) {
      LOG.error("Could not write results", ioe);
    }
  }

  private static List<Transaction> extractTransactions(final Iterator<JsonNode> it) {
    List<Transaction> transactions = new ArrayList<>();
    while (it.hasNext()) {
      JsonNode txNode = it.next();
      if (txNode.has("txBytes")) {
        var tx = Transaction.readFrom(Bytes.fromHexString(txNode.get("txbytes").asText()));
        transactions.add(tx);
      } else {
        var builder = Transaction.builder();
        int type = Bytes.fromHexStringLenient(txNode.get("type").textValue()).toInt();
        TransactionType transactionType = TransactionType.of(type == 0 ? 0xf8 : type);
        builder.type(transactionType);
        builder.nonce(Bytes.fromHexStringLenient(txNode.get("nonce").textValue()).toLong());
        builder.gasPrice(Wei.fromHexString(txNode.get("gasPrice").textValue()));
        builder.gasLimit(Bytes.fromHexStringLenient(txNode.get("gas").textValue()).toLong());
        builder.value(Wei.fromHexString(txNode.get("value").textValue()));
        builder.payload(Bytes.fromHexString(txNode.get("input").textValue()));
        if (txNode.has("to")) {
          builder.to(Address.fromHexString(txNode.get("to").textValue()));
        }

        if (transactionType.requiresChainId()
            || !txNode.has("protected")
            || txNode.get("protected").booleanValue()) {
          // chainid if protected
          builder.chainId(
              new BigInteger(
                  1,
                  Bytes.fromHexStringLenient(txNode.get("chainId").textValue()).toArrayUnsafe()));
        }

        if (txNode.has("secretKey")) {
          SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
          var keys =
              signatureAlgorithm.createKeyPair(
                  signatureAlgorithm.createPrivateKey(
                      Bytes32.fromHexString(txNode.get("secretKey").textValue())));

          transactions.add(builder.signAndBuild(keys));
        } else {
          builder.signature(
              SignatureAlgorithmFactory.getInstance()
                  .createSignature(
                      Bytes.fromHexString(txNode.get("r").textValue()).toUnsignedBigInteger(),
                      Bytes.fromHexString(txNode.get("s").textValue()).toUnsignedBigInteger(),
                      Bytes.fromHexString(txNode.get("v").textValue())
                          .toUnsignedBigInteger()
                          .subtract(Transaction.REPLAY_UNPROTECTED_V_BASE)
                          .byteValueExact()));
          transactions.add(builder.build());
        }
      }
    }
    return transactions;
  }
}
