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
package org.hyperledger.besu.ethereum.permissioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.permissioning.AllowlistPersistor.verifyConfigFileMatchesState;

import org.hyperledger.besu.ethereum.permissioning.AllowlistPersistor.ALLOWLIST_TYPE;

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

public class AllowlistPersistorTest {

  private AllowlistPersistor allowlistPersistor;
  private File tempFile;
  private final String accountsAllowlist =
      String.format("%s=[%s]", ALLOWLIST_TYPE.ACCOUNTS.getTomlKey(), "\"account1\",\"account2\"");
  private final String nodesAllowlist =
      String.format("%s=[%s]", ALLOWLIST_TYPE.NODES.getTomlKey(), "\"node1\",\"node2\"");

  @Before
  public void setUp() throws IOException {
    List<String> lines = Lists.newArrayList(nodesAllowlist, accountsAllowlist);
    tempFile = File.createTempFile("test", "test");
    tempFile.deleteOnExit();
    Files.write(tempFile.toPath(), lines, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    allowlistPersistor = new AllowlistPersistor(tempFile.getAbsolutePath());
  }

  @Test
  public void lineShouldBeRemoved() throws IOException {
    ALLOWLIST_TYPE keyForRemoval = ALLOWLIST_TYPE.ACCOUNTS;

    assertThat(countLines()).isEqualTo(2);
    assertThat(hasKey(keyForRemoval)).isTrue();

    allowlistPersistor.removeExistingConfigItem(keyForRemoval);

    assertThat(countLines()).isEqualTo(1);
    assertThat(hasKey(keyForRemoval)).isFalse();
  }

  @Test
  public void lineShouldBeAdded() throws IOException {
    final ALLOWLIST_TYPE key = ALLOWLIST_TYPE.NODES;
    final Set<String> updatedAllowlist = Collections.singleton("node5");

    assertThat(countLines()).isEqualTo(2);
    assertThat(hasKey(key)).isTrue();

    allowlistPersistor.removeExistingConfigItem(ALLOWLIST_TYPE.NODES);

    assertThat(countLines()).isEqualTo(1);
    assertThat(hasKey(key)).isFalse();

    allowlistPersistor.addNewConfigItem(key, updatedAllowlist);

    assertThat(countLines()).isEqualTo(2);
    assertThat(hasKey(key)).isTrue();
  }

  @Test
  public void lineShouldBeReplaced() throws IOException {
    ALLOWLIST_TYPE key = ALLOWLIST_TYPE.NODES;
    String newValue = "node5";

    assertThat(countLines()).isEqualTo(2);
    assertThat(hasKeyAndExactLineContent(key, nodesAllowlist)).isTrue();

    allowlistPersistor.updateConfig(key, Collections.singleton(newValue));

    assertThat(countLines()).isEqualTo(2);
    assertThat(hasKeyAndContainsValue(key, newValue)).isTrue();
    assertThat(hasKeyAndExactLineContent(key, nodesAllowlist)).isFalse();
  }

  @Test
  public void outputIsValidTOML() throws IOException {
    ALLOWLIST_TYPE key = ALLOWLIST_TYPE.ACCOUNTS;
    List<String> newValue = Lists.newArrayList("account5", "account6", "account4");
    String expectedValue =
        String.format("%s=[%s]", "accounts-allowlist", "\"account5\",\"account6\",\"account4\"");

    allowlistPersistor.updateConfig(key, newValue);

    assertThat(hasKey(key)).isTrue();
    assertThat(hasKeyAndExactLineContent(key, expectedValue)).isTrue();
  }

  @Test
  public void compareContentsWhenEqual_shouldMatch() throws Exception {
    final ALLOWLIST_TYPE key = ALLOWLIST_TYPE.ACCOUNTS;
    final List<String> newValue = Lists.newArrayList("account5", "account6", "account4");

    allowlistPersistor.updateConfig(key, newValue);

    assertThat(verifyConfigFileMatchesState(key, newValue, tempFile.toPath())).isTrue();
  }

  @Test
  public void compareContentsWhenDifferent_shouldThrow() throws Exception {
    final ALLOWLIST_TYPE key = ALLOWLIST_TYPE.ACCOUNTS;
    final List<String> newValue = Lists.newArrayList("account5", "account6", "account4");

    allowlistPersistor.updateConfig(key, newValue);

    assertThatThrownBy(
            () ->
                verifyConfigFileMatchesState(
                    key, Lists.newArrayList("not-the-same"), tempFile.toPath()))
        .isInstanceOf(AllowlistFileSyncException.class);
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

  private boolean hasKey(final ALLOWLIST_TYPE key) throws IOException {
    try (Stream<String> lines = Files.lines(tempFile.toPath())) {
      return lines.anyMatch(s -> s.startsWith(key.getTomlKey()));
    }
  }

  private boolean hasKeyAndContainsValue(final ALLOWLIST_TYPE key, final String value)
      throws IOException {
    try (Stream<String> lines = Files.lines(tempFile.toPath())) {
      return lines.anyMatch(s -> s.startsWith(key.getTomlKey()) && s.contains(value));
    }
  }

  private boolean hasKeyAndExactLineContent(final ALLOWLIST_TYPE key, final String exactKeyValue)
      throws IOException {
    try (Stream<String> lines = Files.lines(tempFile.toPath())) {
      return lines.anyMatch(s -> s.startsWith(key.getTomlKey()) && s.equals(exactKeyValue));
    }
  }
}
