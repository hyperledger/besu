/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.fullsync.era1prepipeline;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Era1FileSource implements Iterator<Path> {
  private static final Pattern ERA1_FILE_PATTERN =
      Pattern.compile("(?:mainnet|sepolia)-(?<fileNumber>\\d{5})-[0-9a-fA-f]{8}.era1");
  private static final String ERA1_PATTERN_FILE_NUMBER_GROUP = "fileNumber";

  private final Path era1Path;

  private int currentFileNumber;
  private final Map<Integer, Path> era1DirectoryContentsByFileNumber = new HashMap<>();

  public Era1FileSource(final Path era1Path, final long currentHeadBlockNumber) {
    this.era1Path = era1Path;

    currentFileNumber = (int) (currentHeadBlockNumber / 8192);
  }

  @Override
  public boolean hasNext() {
    if (era1DirectoryContentsByFileNumber.isEmpty()) {
      populateEra1DirectoryContents();
    }

    return era1DirectoryContentsByFileNumber.containsKey(currentFileNumber);
  }

  @Override
  public Path next() {
    if (era1DirectoryContentsByFileNumber.isEmpty()) {
      populateEra1DirectoryContents();
    }
    return era1DirectoryContentsByFileNumber.get(currentFileNumber++);
  }

  private void populateEra1DirectoryContents() {
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(era1Path)) {
      directoryStream.forEach(
          (file) -> {
            Matcher matcher = ERA1_FILE_PATTERN.matcher(file.getFileName().toString());
            if (matcher.matches()) {
              era1DirectoryContentsByFileNumber.put(
                  Integer.parseInt(matcher.group(ERA1_PATTERN_FILE_NUMBER_GROUP)), file);
            }
          });
    } catch (IOException e) {
      throw new RuntimeException("IOException attempting to inspect supplied era1 data path", e);
    }
  }
}
