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

import static org.hyperledger.besu.evmtool.PrettyPrintSubCommand.COMMAND_NAME;

import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.code.EOFLayout;
import org.hyperledger.besu.util.LogConfigurator;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine;

/**
 * This class, PrettyPrintSubCommand, is a command-line interface (CLI) command that pretty prints
 * EOF (Ethereum Object Format) code. It implements the Runnable interface, meaning it can be used
 * in a thread of execution.
 *
 * <p>The class is annotated with {@code @CommandLine.Command}, which is a PicoCLI annotation that
 * designates this class as a command-line command. The annotation parameters define the command's
 * name, description, whether it includes standard help options, and the version provider.
 *
 * <p>The command's functionality is defined in the run() method, which is overridden from the
 * Runnable interface.
 */
@CommandLine.Command(
    name = COMMAND_NAME,
    description = "Pretty Prints EOF Code",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class PrettyPrintSubCommand implements Runnable {
  /**
   * The name of the command for the PrettyPrintSubCommand. This constant is used as the name
   * parameter in the @CommandLine.Command annotation. It defines the command name that users should
   * enter on the command line to invoke this command.
   */
  public static final String COMMAND_NAME = "pretty-print";

  @CommandLine.ParentCommand private final EvmToolCommand parentCommand;

  @CommandLine.Option(
      names = {"-f", "--force"},
      description = "Always print well formated code, even if there is an error",
      paramLabel = "<boolean>")
  private final Boolean force = false;

  // picocli does it magically
  @CommandLine.Parameters private final List<String> codeList = new ArrayList<>();

  /**
   * Default constructor for the PrettyPrintSubCommand class. This constructor initializes the
   * parentCommand to null.
   */
  public PrettyPrintSubCommand() {
    this(null);
  }

  /**
   * Constructs a new PrettyPrintSubCommand with the specified parent command.
   *
   * @param parentCommand The parent command for this subcommand. This is typically an instance of
   *     EvmToolCommand.
   */
  public PrettyPrintSubCommand(final EvmToolCommand parentCommand) {
    this.parentCommand = parentCommand;
  }

  @Override
  public void run() {
    LogConfigurator.setLevel("", "OFF");

    for (var hexCode : codeList) {
      Bytes container;
      try {
        container = Bytes.fromHexString(hexCode);
      } catch (IllegalArgumentException e) {
        parentCommand.out.println("Invalid hex string: " + e.getMessage());
        continue;
      }
      if (container.get(0) != ((byte) 0xef) && container.get(1) != 0) {
        parentCommand.out.println(
            "Pretty printing of legacy EVM is not supported. Patches welcome!");

      } else {
        String fork = EvmSpecVersion.OSAKA.getName();
        if (parentCommand.hasFork()) {
          fork = parentCommand.getFork();
        }
        ProtocolSpec protocolSpec = ReferenceTestProtocolSchedules.getInstance().geSpecByName(fork);
        EVM evm = protocolSpec.getEvm();
        EOFLayout layout = evm.parseEOF(container);
        if (layout.isValid()) {
          var validatedCode = evm.getCodeUncached(container);
          if (validatedCode.isValid() || force) {
            layout.prettyPrint(parentCommand.out);
          }
          if (validatedCode instanceof CodeInvalid codeInvalid) {
            parentCommand.out.println("EOF code is invalid - " + codeInvalid.getInvalidReason());
          }
          if (layout.container().size() != container.size()) {
            parentCommand.out.println(
                "EOF code is invalid - dangling data after container - "
                    + container.slice(layout.container().size()).toHexString());
          }
        } else {
          parentCommand.out.println("EOF layout is invalid - " + layout.invalidReason());
        }
      }
    }
  }
}
