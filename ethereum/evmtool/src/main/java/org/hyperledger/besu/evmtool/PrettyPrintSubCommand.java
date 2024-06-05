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

import org.hyperledger.besu.evm.code.CodeV1Validation;
import org.hyperledger.besu.evm.code.EOFLayout;
import org.hyperledger.besu.util.LogConfigurator;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine;

@CommandLine.Command(
    name = COMMAND_NAME,
    description = "Pretty Prints EOF Code",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class PrettyPrintSubCommand implements Runnable {
  public static final String COMMAND_NAME = "pretty-print";
  @CommandLine.ParentCommand private final EvmToolCommand parentCommand;

  @CommandLine.Option(
      names = {"-f", "--force"},
      description = "Always print well formated code, even if there is an error",
      paramLabel = "<boolean>")
  private final Boolean force = false;

  // picocli does it magically
  @CommandLine.Parameters private final List<String> codeList = new ArrayList<>();

  public PrettyPrintSubCommand() {
    this(null);
  }

  public PrettyPrintSubCommand(final EvmToolCommand parentCommand) {
    this.parentCommand = parentCommand;
  }

  @Override
  public void run() {
    LogConfigurator.setLevel("", "OFF");

    for (var hexCode : codeList) {
      Bytes container = Bytes.fromHexString(hexCode);
      if (container.get(0) != ((byte) 0xef) && container.get(1) != 0) {
        parentCommand.out.println(
            "Pretty printing of legacy EVM is not supported. Patches welcome!");

      } else {
        EOFLayout layout = EOFLayout.parseEOF(container);
        if (layout.isValid()) {
          String validation = CodeV1Validation.validate(layout);
          if (validation == null || force) {
            layout.prettyPrint(parentCommand.out);
          }
          if (validation != null) {
            parentCommand.out.println("EOF code is invalid - " + validation);
          }
        } else {
          parentCommand.out.println("EOF layout is invalid - " + layout.invalidReason());
        }
      }
    }
  }
}
