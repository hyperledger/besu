package tech.pegasys.pantheon.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ImportSubCommandTest extends CommandTestAbstract {

  @Test
  public void callingImportSubCommandWithoutPathMustDisplayErrorAndUsage() {
    parseCommand("import");
    final String expectedErrorOutputStart = "Missing required parameter: PATH";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingImportSubCommandHelpMustDisplayImportUsage() {
    parseCommand("import", "--help");
    final String expectedOutputStart = "Usage: pantheon import [-hV] PATH";
    assertThat(commandOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingImportSubCommandWithPathMustImportBlocksWithThisPath() throws Exception {
    final Path path = Paths.get(".");
    parseCommand("import", path.toString());

    verify(mockBlockImporter).importBlockchain(pathArgumentCaptor.capture(), any());

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(path);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }
}
