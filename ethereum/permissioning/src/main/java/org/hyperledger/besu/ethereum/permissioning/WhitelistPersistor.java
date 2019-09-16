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
import net.consensys.cava.toml.Toml;
import net.consensys.cava.toml.TomlParseResult;

public class WhitelistPersistor {

  private File configurationFile;

  public enum WHITELIST_TYPE {
    ACCOUNTS("accounts-whitelist"),
    NODES("nodes-whitelist");

    private String tomlKey;

    WHITELIST_TYPE(final String tomlKey) {
      this.tomlKey = tomlKey;
    }

    public String getTomlKey() {
      return tomlKey;
    }
  }

  public WhitelistPersistor(final String configurationFile) {
    this.configurationFile = new File(configurationFile);
  }

  public static boolean verifyConfigFileMatchesState(
      final WHITELIST_TYPE whitelistType,
      final Collection<String> checkLists,
      final Path configurationFilePath)
      throws IOException, WhitelistFileSyncException {

    final Map<WHITELIST_TYPE, Collection<String>> configItems =
        existingConfigItems(configurationFilePath);
    final Collection<String> existingValues =
        configItems.get(whitelistType) != null
            ? configItems.get(whitelistType)
            : Collections.emptyList();

    boolean listsMatch = existingValues.containsAll(checkLists);
    if (!listsMatch) {
      throw new WhitelistFileSyncException();
    }
    return listsMatch;
  }

  public boolean verifyConfigFileMatchesState(
      final WHITELIST_TYPE whitelistType, final Collection<String> checkLists)
      throws IOException, WhitelistFileSyncException {
    return verifyConfigFileMatchesState(whitelistType, checkLists, configurationFile.toPath());
  }

  public synchronized void updateConfig(
      final WHITELIST_TYPE whitelistType, final Collection<String> updatedWhitelistValues)
      throws IOException {
    removeExistingConfigItem(whitelistType);
    addNewConfigItem(whitelistType, updatedWhitelistValues);
  }

  private static Map<WHITELIST_TYPE, Collection<String>> existingConfigItems(
      final Path configurationFilePath) throws IOException {
    TomlParseResult parsedToml = Toml.parse(configurationFilePath);

    return Arrays.stream(WHITELIST_TYPE.values())
        .filter(k -> parsedToml.contains(k.getTomlKey()))
        .map(
            whitelist_type ->
                new AbstractMap.SimpleImmutableEntry<>(
                    whitelist_type, parsedToml.getArrayOrEmpty(whitelist_type.getTomlKey())))
        .collect(
            Collectors.toMap(
                o -> o.getKey(),
                o ->
                    o.getValue()
                        .toList()
                        .parallelStream()
                        .map(Object::toString)
                        .collect(Collectors.toList())));
  }

  @VisibleForTesting
  void removeExistingConfigItem(final WHITELIST_TYPE whitelistType) throws IOException {
    List<String> otherConfigItems =
        existingConfigItems(configurationFile.toPath())
            .entrySet()
            .parallelStream()
            .filter(listType -> !listType.getKey().equals(whitelistType))
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
      final WHITELIST_TYPE whitelistType,
      final Collection<String> whitelistValues,
      final Path configFilePath)
      throws IOException {
    String newConfigItem = valueListToTomlArray(whitelistType, whitelistValues);

    Files.write(
        configFilePath,
        newConfigItem.getBytes(Charsets.UTF_8),
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND);
  }

  @VisibleForTesting
  void addNewConfigItem(
      final WHITELIST_TYPE whitelistType, final Collection<String> whitelistValues)
      throws IOException {
    addNewConfigItem(whitelistType, whitelistValues, configurationFile.toPath());
  }

  private static String valueListToTomlArray(
      final WHITELIST_TYPE whitelistType, final Collection<String> whitelistValues) {
    return String.format(
        "%s=[%s]",
        whitelistType.getTomlKey(),
        whitelistValues
            .parallelStream()
            .map(uri -> String.format("\"%s\"", uri))
            .collect(Collectors.joining(",")));
  }
}
