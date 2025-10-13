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
package org.hyperledger.besu.evmtool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.evmtool.CodeValidateSubCommand.COMMAND_NAME;

import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.code.CodeV1;
import org.hyperledger.besu.evm.code.EOFLayout;
import org.hyperledger.besu.evm.code.EOFLayout.EOFContainerMode;
import org.hyperledger.besu.util.LogConfigurator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.web3j.utils.Strings;
import picocli.CommandLine;
import picocli.CommandLine.ParentCommand;

/**
 * This class represents the CodeValidateSubCommand. It is responsible for validating EVM code for
 * fuzzing. It implements the Runnable interface and is annotated with the {@code
 * CommandLine.Command} annotation.
 */
@SuppressWarnings({"ConstantValue"})
@CommandLine.Command(
    name = COMMAND_NAME,
    description = "Validates EVM code for fuzzing",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class CodeValidateSubCommand implements Runnable {
  /**
   * The command name for the CodeValidateSubCommand. This constant is used as the name attribute in
   * the CommandLine.Command annotation.
   */
  public static final String COMMAND_NAME = "code-validate";

  @ParentCommand EvmToolCommand parentCommand;

  private final Supplier<EVM> evm;

  @CommandLine.Option(
      names = {"--file"},
      description = "A file containing a set of inputs")
  private final File codeFile = null;

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // picocli does it magically
  @CommandLine.Parameters
  private final List<String> cliCode = new ArrayList<>();

  /** Default constructor for the CodeValidateSubCommand class. This is required by PicoCLI. */
  @SuppressWarnings("unused")
  public CodeValidateSubCommand() {
    // PicoCLI requires this
    this(null);
  }

  CodeValidateSubCommand(final EvmToolCommand parentCommand) {
    this.parentCommand = parentCommand;
    String fork =
        parentCommand != null && parentCommand.hasFork()
            ? parentCommand.getFork()
            : EvmSpecVersion.OSAKA.getName();

    evm =
        Suppliers.memoize(
            () -> {
              ProtocolSpec protocolSpec =
                  ReferenceTestProtocolSchedules.getInstance().geSpecByName(fork);
              return protocolSpec.getEvm();
            });
  }

  @Override
  public void run() {
    LogConfigurator.setLevel("", "OFF");
    if (cliCode.isEmpty() && codeFile == null) {
      try (BufferedReader in = new BufferedReader(new InputStreamReader(parentCommand.in, UTF_8))) {
        checkCodeFromBufferedReader(in);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else if (codeFile != null) {
      try (BufferedReader in = new BufferedReader(new FileReader(codeFile, UTF_8))) {
        checkCodeFromBufferedReader(in);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      for (String code : cliCode) {
        String validation = considerCode(code);
        if (!Strings.isBlank(validation)) {
          parentCommand.out.println(validation);
        }
      }
    }
    parentCommand.out.flush();
  }

  private void checkCodeFromBufferedReader(final BufferedReader in) {
    try {
      for (String code = in.readLine(); code != null; code = in.readLine()) {
        try {
          String validation = considerCode(code);
          if (!Strings.isBlank(validation)) {
            parentCommand.out.println(validation);
          }
        } catch (RuntimeException e) {
          parentCommand.out.println("fail: " + e.getMessage());
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * This method is responsible for validating the EVM code. It takes a hexadecimal string
   * representation of the EVM code as input. The method first converts the hexadecimal string to
   * Bytes. It then checks if the code follows the EOF layout. If the layout is valid, it retrieves
   * the code from the EVM. If the code is invalid, it returns an error message with the reason for
   * the invalidity. If the code is valid, it returns a string with "OK" followed by the hexadecimal
   * string representation of each code section.
   *
   * @param hexCode the hexadecimal string representation of the EVM code
   * @return a string indicating whether the code is valid or not, and in case of validity, the
   *     hexadecimal string representation of each code section
   */
  public String considerCode(final String hexCode) {
    Bytes codeBytes;
    try {
      String strippedString =
          hexCode.replaceAll("(^|\n)#[^\n]*($|\n)", "").replaceAll("[^0-9A-Za-z]", "");
      if (Strings.isEmpty(strippedString)) {
        return "";
      }
      codeBytes = Bytes.fromHexString(strippedString);
    } catch (RuntimeException re) {
      return "err: hex string -" + re;
    }
    if (codeBytes.isEmpty()) {
      return "err: empty container";
    }

    EOFLayout layout = evm.get().parseEOF(codeBytes);
    if (!layout.isValid()) {
      return "err: layout - " + layout.invalidReason();
    }

    Code code = evm.get().getCodeUncached(codeBytes);
    if (code instanceof CodeInvalid codeInvalid) {
      return "err: " + codeInvalid.getInvalidReason();
    } else if (EOFContainerMode.INITCODE.equals(
        ((CodeV1) code).getEofLayout().containerMode().get())) {
      return "err: code is valid initcode.  Runtime code expected";
    } else {
      return "OK %d/%d/%d"
          .formatted(code.getCodeSectionCount(), code.getSubcontainerCount(), code.getDataSize());
    }
  }
}
