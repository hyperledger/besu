package tech.pegasys.pantheon.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;

import java.io.File;

import org.junit.Test;

public class ExportPublicKeySubCommandTest extends CommandTestAbstract {

  @Test
  public void callingExportPublicKeySubCommandWithoutPathMustDisplayErrorAndUsage() {
    parseCommand("export-pub-key");
    final String expectedErrorOutputStart = "Missing required parameter: PATH";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingExportPublicKeySubCommandHelpMustDisplayImportUsage() {
    parseCommand("export-pub-key", "--help");
    final String expectedOutputStart = "Usage: pantheon export-pub-key [-hV] PATH";
    assertThat(commandOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingExportPublicKeySubCommandWithFilePathMustWritePublicKeyInThisFile()
      throws Exception {

    final KeyPair keyPair = KeyPair.generate();

    when(mockController.getLocalNodeKeyPair()).thenReturn(keyPair);

    final File file = File.createTempFile("public", "key");
    parseCommand("export-pub-key", file.getPath());

    assertThat(contentOf(file))
        .startsWith(keyPair.getPublicKey().toString())
        .endsWith(keyPair.getPublicKey().toString());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }
}
