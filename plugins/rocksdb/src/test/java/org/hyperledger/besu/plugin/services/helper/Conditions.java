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
package org.hyperledger.besu.plugin.services.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.assertj.core.api.Condition;

public class Conditions {
  public static final Condition<Path> FILE_EXISTS =
      new Condition<>(path -> path != null && path.toFile().exists(), "File must exist.");
  public static final Condition<Path> FILE_DOES_NOT_EXIST =
      new Condition<>(path -> path == null || !path.toFile().exists(), "File must not exist.");

  public static Condition<Path> shouldContain(final String content) {
    return new Condition<>(
        path -> {
          try {
            return content.equals(Files.readString(path));
          } catch (IOException e) {
            return false;
          }
        },
        "File should contain specified content.");
  }
}
