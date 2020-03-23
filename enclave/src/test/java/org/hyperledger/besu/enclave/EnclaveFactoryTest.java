package org.hyperledger.besu.enclave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.hyperledger.besu.util.InvalidConfigurationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EnclaveFactoryTest {
  @ClassRule public static final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private static final String EMPTY_FILE_ERROR_MSG = "Keystore password file is empty: %s";

  @Test
  public void passwordCanBeReadFromFile() throws IOException {
    final File passwordFile = temporaryFolder.newFile();
    Files.writeString(passwordFile.toPath(), "test" + System.lineSeparator() + "test2");
    assertThat(EnclaveFactory.readSecretFromFile(passwordFile.toPath())).isEqualTo("test");
  }

  @Test
  public void emptyFileThrowsException() throws IOException {
    final File passwordFile = temporaryFolder.newFile();
    assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(() -> EnclaveFactory.readSecretFromFile(passwordFile.toPath()))
        .withMessage(EMPTY_FILE_ERROR_MSG, passwordFile);
  }

  @Test
  public void fileWithOnlyEoLThrowsException() throws IOException {
    final File passwordFile = temporaryFolder.newFile();
    Files.writeString(passwordFile.toPath(), System.lineSeparator());
    assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(() -> EnclaveFactory.readSecretFromFile(passwordFile.toPath()))
        .withMessage(EMPTY_FILE_ERROR_MSG, passwordFile);
  }
}
