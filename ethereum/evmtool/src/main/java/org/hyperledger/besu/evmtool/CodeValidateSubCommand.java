package org.hyperledger.besu.evmtool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.evmtool.CodeValidateSubCommand.COMMAND_NAME;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.code.EOFLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine;

@CommandLine.Command(
    name = COMMAND_NAME,
    description = "Execute an Ethereum State Test.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class CodeValidateSubCommand implements Runnable {
  public static final String COMMAND_NAME = "code-validate";
  private final InputStream input;
  private final PrintStream output;

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // picocli does it magically
  @CommandLine.Parameters
  private final List<String> cliCode = new ArrayList<>();

  @SuppressWarnings("unused")
  public CodeValidateSubCommand() {
    // PicoCLI requires this
    this(System.in, System.out);
  }

  CodeValidateSubCommand(final InputStream input, final PrintStream output) {
    this.input = input;
    this.output = output;
  }

  @Override
  public void run() {
    if (cliCode.isEmpty()) {
      BufferedReader in = new BufferedReader(new InputStreamReader(input, UTF_8));
      try {
        for (String code = in.readLine(); code != null; code = in.readLine()) {
          output.println(considerCode(code));
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      for (String code : cliCode) {
        output.println(considerCode(code));
      }
    }
  }

  public String considerCode(final String hexCode) {
    Bytes codeBytes;
    try {
      codeBytes = Bytes.fromHexString(hexCode.replaceAll("\\s+", ""));
    } catch (RuntimeException re) {
      return "err: hex string -" + re;
    }

    var layout = EOFLayout.parseEOF(codeBytes);
    if (!layout.isValid()) {
      return "err: layout - " + layout.getInvalidReason();
    }

    var code = CodeFactory.createCode(codeBytes, Hash.hash(codeBytes), 1, true);
    if (!code.isValid()) {
      return "err: " + ((CodeInvalid) code).getInvalidReason();
    }

    return "OK " + code.getCodeBytes(0).toUnprefixedHexString();
  }
}
