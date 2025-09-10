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
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Era1FileSource implements Iterator<URI> {
  private static final Pattern ERA1_FILE_PATTERN =
      Pattern.compile("(?:mainnet|sepolia)-(?<fileNumber>\\d{5})-[0-9a-fA-F]{8}.era1");
  private static final String ERA1_PATTERN_FILE_NUMBER_GROUP = "fileNumber";
  private static final int ERA1_BLOCK_COUNT = 8192;

  private final URI era1PathUri;

  private int currentFileNumber;
  private final Map<Integer, URI> era1DirectoryContentsByFileNumber = new HashMap<>();

  public Era1FileSource(final URI era1PathUri, final long currentHeadBlockNumber) {
    this.era1PathUri = era1PathUri;
    currentFileNumber = (int) (currentHeadBlockNumber / ERA1_BLOCK_COUNT);
  }

  @Override
  public boolean hasNext() {
    if (era1DirectoryContentsByFileNumber.isEmpty()) {
      populateEra1DirectoryContents();
    }

    return era1DirectoryContentsByFileNumber.containsKey(currentFileNumber);
  }

  @Override
  public URI next() {
    if (era1DirectoryContentsByFileNumber.isEmpty()) {
      populateEra1DirectoryContents();
    }
    return era1DirectoryContentsByFileNumber.get(currentFileNumber++);
  }

  private void populateEra1DirectoryContents() {

    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Path.of(era1PathUri))) {
      directoryStream.forEach(
          (file) -> {
            Matcher matcher = ERA1_FILE_PATTERN.matcher(file.getFileName().toString());
            if (matcher.matches()) {
              era1DirectoryContentsByFileNumber.put(
                  Integer.parseInt(matcher.group(ERA1_PATTERN_FILE_NUMBER_GROUP)), file.toUri());
            }
          });
    } catch (IOException e) {
      throw new RuntimeException("IOException attempting to inspect supplied era1 data path", e);
    }
  }
}
