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
package org.hyperledger.besu.config.experimental;

import java.util.Optional;

/** For now there is a static config that is driven by a command line option. */
public class MergeOptions {
  private static Optional<Boolean> mergeEnabled = Optional.empty();

  public static void setMergeEnabled(final boolean bool) {
    if (!mergeEnabled.isPresent()) {
      mergeEnabled = Optional.of(bool);
    } else if (mergeEnabled.get() != bool) {
      throw new RuntimeException("Refusing to re-configure already configured merge feature");
    }
  }

  public static boolean isMergeEnabled() {
    return mergeEnabled.orElse(false);
  }
}
