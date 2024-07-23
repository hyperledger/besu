/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli.config;

import org.hyperledger.besu.cli.util.ProfileFinder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfilesCompletionCandidatesTest {
  @TempDir private static Path tempProfilesDir;
  private static String originalProfilesDirProperty;

  @BeforeAll
  public static void copyExternalProfiles() throws IOException {
    for (String internalProfileName : InternalProfileName.getInternalProfileNames()) {
      final Path profilePath = tempProfilesDir.resolve(internalProfileName + "_external.toml");

      String profileConfigFile =
          InternalProfileName.valueOfIgnoreCase(internalProfileName).get().getConfigFile();
      try (InputStream resourceUrl =
          ProfileFinder.class.getClassLoader().getResourceAsStream(profileConfigFile)) {
        if (resourceUrl != null) {
          Files.copy(resourceUrl, profilePath);
        }
      }
    }
  }

  @BeforeAll
  public static void setupSystemProperty() {
    originalProfilesDirProperty = System.getProperty("besu.profiles.dir");
    // sets the system property for the test
    System.setProperty("besu.profiles.dir", tempProfilesDir.toString());
  }

  @Test
  void profileCompletionCandidates_shouldIncludeInternalAndExternalProfiles() {
    Iterator<String> candidates = new ProfilesCompletionCandidates().iterator();
    // convert Iterator to List
    List<String> candidatesList = new ArrayList<>();
    candidates.forEachRemaining(candidatesList::add);

    Assertions.assertThat(candidatesList).containsExactlyInAnyOrderElementsOf(allProfileNames());
  }

  static Set<String> allProfileNames() {
    final Set<String> profileNames = new TreeSet<>(InternalProfileName.getInternalProfileNames());
    final Set<String> externalProfileNames =
        InternalProfileName.getInternalProfileNames().stream()
            .map(name -> name + "_external")
            .collect(Collectors.toSet());
    profileNames.addAll(externalProfileNames);
    return profileNames;
  }

  @AfterAll
  public static void clearSystemProperty() {
    if (originalProfilesDirProperty != null) {
      System.setProperty("besu.profiles.dir", originalProfilesDirProperty);
    } else {
      System.clearProperty("besu.profiles.dir");
    }
  }
}
