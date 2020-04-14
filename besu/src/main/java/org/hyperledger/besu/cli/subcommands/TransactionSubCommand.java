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
package org.hyperledger.besu.cli.subcommands;

import static com.google.common.base.Preconditions.checkNotNull;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Spec;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1.PrivateKey;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
    name = TransactionSubCommand.COMMAND_NAME,
    description = "This command provides transactions related actions.",
    mixinStandardHelpOptions = true,
    subcommands = {TransactionSubCommand.SignSubCommand.class})
public class TransactionSubCommand implements Runnable {
  private static final Logger LOG = LogManager.getLogger();

  public static final String COMMAND_NAME = "tx";

  private final PrintStream out;

  @SuppressWarnings("unused")
  @ParentCommand
  private BesuCommand parentCommand;

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec;

  public TransactionSubCommand(final PrintStream out) {
    this.out = out;
  }

  @Override
  public void run() {
    spec.commandLine().usage(out);
  }

  /**
   * Transaction sign sub command
   *
   * <p>Sign a transaction
   */
  @Command(
      name = "sign",
      description = "This command signs a transaction.",
      mixinStandardHelpOptions = true)
  static class SignSubCommand implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private TransactionSubCommand parentCommand; // Picocli injects reference to parent command

    @SuppressWarnings("unused")
    @Spec
    private CommandSpec spec;

    @Option(names = "--nonce", description = "nonce", arity = "1")
    private final Long nonce = 0L;

    @Option(names = "--gas-price", description = "gas price", arity = "1")
    final Wei gasPrice = null;

    @Option(names = "--gas-premium", description = "gas premium", arity = "1")
    final Wei gasPremium = null;

    @Option(names = "--fee-cap", description = "fee cap", arity = "1")
    final Wei feeCap = null;

    @Option(names = "--gas-limit", description = "gas limit", arity = "1")
    final Long gasLimit = 0L;

    @Option(names = "--to", description = "to", arity = "1")
    final Address to = null;

    @Option(names = "--value", description = "value", arity = "1")
    final Wei value = Wei.ZERO;

    @Option(names = "--chain-id", description = "chain id", arity = "1")
    final BigInteger chainId = null;

    @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
    @Option(names = "--payload", description = "payload hex string", arity = "1")
    private String payload = "0x00";

    @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
    @Option(names = "--private-key", description = "hex string private key", arity = "1")
    private String privateKey;

    @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
    @Option(names = "--rpc-url", description = "RPC URL", arity = "1")
    private String rpcUrl = "http://127.0.0.1:8545";

    @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
    @Option(names = "--rpc-send", description = "Send RPC request", arity = "1")
    private Boolean send = false;

    @Override
    public void run() {
      checkNotNull(parentCommand);
      try {
        sign();
      } catch (IOException e) {
        LOG.error(e);
      }
    }

    private void sign() throws IOException {
      final Transaction.Builder builder =
          new Transaction.Builder()
              .nonce(nonce)
              .gasPrice(gasPrice)
              .gasLimit(gasLimit)
              .value(value)
              .payload(Bytes.fromHexString(payload));
      if (to != null) {
        builder.to(to);
      }
      if (gasPrice != null) {
        builder.gasPrice(gasPrice);
      } else if (gasPremium != null && feeCap != null) {
        builder.gasPremium(gasPremium).feeCap(feeCap);
      } else {
        throw new RuntimeException("Invalid transaction format");
      }
      final Transaction transaction =
          builder.signAndBuild(
              KeyPair.create(PrivateKey.create(Bytes32.fromHexString(privateKey))));
      System.out.println("is eip-1559: " + transaction.isEIP1559Transaction());
      final String txRlp = toRlp(transaction);
      System.out.println(txRlp);
      if (send) {
        System.out.println("Sending eth_sendRawTransaction");
        System.out.println(new RpcClient(rpcUrl).eth_sendRawTransaction(txRlp));
      }
    }

    private static String toRlp(final Transaction transaction) {
      final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
      transaction.writeTo(rlpOutput);
      return rlpOutput.encoded().toHexString();
    }
  }
}
