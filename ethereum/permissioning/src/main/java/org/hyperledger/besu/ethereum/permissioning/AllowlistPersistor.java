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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import org.apache.tuweni.toml.Toml;
import org.apache.tuweni.toml.TomlParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllowlistPersistor {
  private static final Logger LOG = LoggerFactory.getLogger(AllowlistPersistor.class);

  private final File configurationFile;

  public enum ALLOWLIST_TYPE {
    ACCOUNTS("accounts-allowlist"),
    NODES("nodes-allowlist");

    private final String tomlKey;

    ALLOWLIST_TYPE(final String tomlKey) {
      this.tomlKey = tomlKey;
    }

    public String getTomlKey() {
      return tomlKey;
    }
  }

  public AllowlistPersistor(final String configurationFile) {
    this.configurationFile = new File(configurationFile);
  }

  public static boolean verifyConfigFileMatchesState(
      final ALLOWLIST_TYPE allowlistType,
      final Collection<String> checkLists,
      final Path configurationFilePath)
      throws IOException, AllowlistFileSyncException {

    final Map<ALLOWLIST_TYPE, Collection<String>> configItems =
        existingConfigItems(configurationFilePath);
    final Collection<String> existingValues =
        configItems.get(allowlistType) != null
            ? configItems.get(allowlistType)
            : Collections.emptyList();

    if (!existingValues.containsAll(checkLists)) {
      LOG.atDebug()
          .setMessage("\n LISTS DO NOT MATCH configFile::")
          .addArgument(existingValues)
          .addArgument(configurationFilePath)
          .log();
      LOG.atDebug().setMessage("\nLISTS DO NOT MATCH in-memory ::").addArgument(checkLists).log();
      throw new AllowlistFileSyncException();
    }
    return true;
  }

  public boolean verifyConfigFileMatchesState(
      final ALLOWLIST_TYPE allowlistType, final Collection<String> checkLists)
      throws IOException, AllowlistFileSyncException {
    return verifyConfigFileMatchesState(allowlistType, checkLists, configurationFile.toPath());
  }

  public synchronized void updateConfig(
      final ALLOWLIST_TYPE allowlistType, final Collection<String> updatedAllowlistValues)
      throws IOException {
    removeExistingConfigItem(allowlistType);
    addNewConfigItem(allowlistType, updatedAllowlistValues);
  }

  private static Map<ALLOWLIST_TYPE, Collection<String>> existingConfigItems(
      final Path configurationFilePath) throws IOException {
    TomlParseResult parsedToml = Toml.parse(configurationFilePath);

    return Arrays.stream(ALLOWLIST_TYPE.values())
        .filter(k -> parsedToml.contains(k.getTomlKey()))
        .map(
            allowlist_type ->
                new AbstractMap.SimpleImmutableEntry<>(
                    allowlist_type, parsedToml.getArrayOrEmpty(allowlist_type.getTomlKey())))
        .collect(
            Collectors.toMap(
                o -> o.getKey(),
                o ->
                    o.getValue().toList().parallelStream()
                        .map(Object::toString)
                        .collect(Collectors.toList())));
  }

  @VisibleForTesting
  void removeExistingConfigItem(final ALLOWLIST_TYPE allowlistType) throws IOException {
    List<String> otherConfigItems =
        existingConfigItems(configurationFile.toPath()).entrySet().parallelStream()
            .filter(listType -> !listType.getKey().equals(allowlistType))
            .map(keyVal -> valueListToTomlArray(keyVal.getKey(), keyVal.getValue()))
            .collect(Collectors.toList());

    Files.write(
        configurationFile.toPath(),
        otherConfigItems,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  @VisibleForTesting
  public static void addNewConfigItem(
      final ALLOWLIST_TYPE allowlistType,
      final Collection<String> allowlistValues,
      final Path configFilePath)
      throws IOException {
    String newConfigItem = valueListToTomlArray(allowlistType, allowlistValues);

    Files.write(
        configFilePath,
        newConfigItem.getBytes(Charsets.UTF_8),
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND);
  }

  @VisibleForTesting
  void addNewConfigItem(
      final ALLOWLIST_TYPE allowlistType, final Collection<String> allowlistValues)
      throws IOException {
    addNewConfigItem(allowlistType, allowlistValues, configurationFile.toPath());
  }

  private static String valueListToTomlArray(
      final ALLOWLIST_TYPE allowlistType, final Collection<String> allowlistValues) {
    return String.format(
        "%s=[%s]",
        allowlistType.getTomlKey(),
        allowlistValues.parallelStream()
            .map(uri -> String.format("\"%s\"", uri))
            .collect(Collectors.joining(",")));
  }
}
