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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Era1HttpFileSource implements Iterator<URI> {
  private static final Pattern ERA1_LINK_PATTERN =
      Pattern.compile(
          "<a href=\"(?<fileName>(?:mainnet|sepolia)-(?<fileNumber>\\d{5})-[0-9a-fA-F]{8}.era1)\">");
  private static final String ERA1_PATTERN_FILE_NAME_GROUP = "fileName";
  private static final String ERA1_PATTERN_FILE_NUMBER_GROUP = "fileNumber";
  private static final int ERA1_BLOCK_COUNT = 8192;

  private final URI era1Uri;

  private int currentFileNumber;
  private final Map<Integer, URI> era1DirectoryContentsByFileNumber = new HashMap<>();

  public Era1HttpFileSource(final URI era1Uri, final long currentHeadBlockNumber) {
    this.era1Uri = era1Uri;
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
    HttpRequest getRequest = HttpRequest.newBuilder(era1Uri).GET().build();
    HttpResponse<String> getResponse;
    try (HttpClient httpClient = HttpClient.newHttpClient()) {
      getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    Matcher matcher = ERA1_LINK_PATTERN.matcher(getResponse.body());
    while (matcher.find()) {
      String fileName = matcher.group(ERA1_PATTERN_FILE_NAME_GROUP);
      int fileNumber = Integer.parseInt(matcher.group(ERA1_PATTERN_FILE_NUMBER_GROUP));

      era1DirectoryContentsByFileNumber.put(fileNumber, era1Uri.resolve(fileName));
    }
  }
}
