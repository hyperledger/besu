/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.permissioning;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.permissioning.WhitelistPersistor.WHITELIST_TYPE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WhitelistPersistorTest {

  private WhitelistPersistor whitelistPersistor;
  private File tempFile;
  private final String accountsWhitelist =
      String.format("%s=[%s]", WHITELIST_TYPE.ACCOUNTS.getTomlKey(), "\"account1\",\"account2\"");
  private final String nodesWhitelist =
      String.format("%s=[%s]", WHITELIST_TYPE.NODES.getTomlKey(), "\"node1\",\"node2\"");

  @Before
  public void setUp() throws IOException {
    List<String> lines = Lists.newArrayList(nodesWhitelist, accountsWhitelist);
    tempFile = File.createTempFile("test", "test");
    tempFile.deleteOnExit();
    Files.write(tempFile.toPath(), lines, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    whitelistPersistor = new WhitelistPersistor(tempFile.getAbsolutePath());
  }

  @Test
  public void lineShouldBeRemoved() throws IOException {
    WHITELIST_TYPE keyForRemoval = WHITELIST_TYPE.ACCOUNTS;

    assertThat(countLines()).isEqualTo(2);
    assertThat(hasKey(keyForRemoval)).isTrue();

    whitelistPersistor.removeExistingConfigItem(keyForRemoval);

    assertThat(countLines()).isEqualTo(1);
    assertThat(hasKey(keyForRemoval)).isFalse();
  }

  @Test
  public void lineShouldBeAdded() throws IOException {
    final WHITELIST_TYPE key = WHITELIST_TYPE.NODES;
    final Set<String> updatedWhitelist = Collections.singleton("node5");

    assertThat(countLines()).isEqualTo(2);
    assertThat(hasKey(key)).isTrue();

    whitelistPersistor.removeExistingConfigItem(WHITELIST_TYPE.NODES);

    assertThat(countLines()).isEqualTo(1);
    assertThat(hasKey(key)).isFalse();

    whitelistPersistor.addNewConfigItem(key, updatedWhitelist);

    assertThat(countLines()).isEqualTo(2);
    assertThat(hasKey(key)).isTrue();
  }

  @Test
  public void lineShouldBeReplaced() throws IOException {
    WHITELIST_TYPE key = WHITELIST_TYPE.NODES;
    String newValue = "node5";

    assertThat(countLines()).isEqualTo(2);
    assertThat(hasKeyAndExactLineContent(key, nodesWhitelist)).isTrue();

    whitelistPersistor.updateConfig(key, Collections.singleton(newValue));

    assertThat(countLines()).isEqualTo(2);
    assertThat(hasKeyAndContainsValue(key, newValue)).isTrue();
    assertThat(hasKeyAndExactLineContent(key, nodesWhitelist)).isFalse();
  }

  @Test
  public void outputIsValidTOML() throws IOException {
    WHITELIST_TYPE key = WHITELIST_TYPE.ACCOUNTS;
    List<String> newValue = Lists.newArrayList("account5", "account6", "account4");
    String expectedValue =
        String.format("%s=[%s]", "accounts-whitelist", "\"account5\",\"account6\",\"account4\"");

    whitelistPersistor.updateConfig(key, newValue);

    assertThat(hasKey(key)).isTrue();
    assertThat(hasKeyAndExactLineContent(key, expectedValue)).isTrue();
  }

  @After
  public void tearDown() {
    tempFile.delete();
  }

  private long countLines() throws IOException {
    try (Stream<String> lines = Files.lines(tempFile.toPath())) {
      return lines.count();
    }
  }

  private boolean hasKey(final WHITELIST_TYPE key) throws IOException {
    try (Stream<String> lines = Files.lines(tempFile.toPath())) {
      return lines.anyMatch(s -> s.startsWith(key.getTomlKey()));
    }
  }

  private boolean hasKeyAndContainsValue(final WHITELIST_TYPE key, final String value)
      throws IOException {
    try (Stream<String> lines = Files.lines(tempFile.toPath())) {
      return lines.anyMatch(s -> s.startsWith(key.getTomlKey()) && s.contains(value));
    }
  }

  private boolean hasKeyAndExactLineContent(final WHITELIST_TYPE key, final String exactKeyValue)
      throws IOException {
    try (Stream<String> lines = Files.lines(tempFile.toPath())) {
      return lines.anyMatch(s -> s.startsWith(key.getTomlKey()) && s.equals(exactKeyValue));
    }
  }
}
