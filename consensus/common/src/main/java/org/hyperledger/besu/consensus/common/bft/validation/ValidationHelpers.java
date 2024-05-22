/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.consensus.common.bft.validation;

import org.hyperledger.besu.consensus.common.bft.payload.Authored;
import org.hyperledger.besu.consensus.common.bft.payload.Payload;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;

import java.util.Collection;

/** The type Validation helpers. */
public class ValidationHelpers {
  /** Default constructor. */
  private ValidationHelpers() {}

  /**
   * Has duplicate authors.
   *
   * @param <T> the type parameter
   * @param msgs the msgs
   * @return the boolean
   */
  public static <T extends Payload> boolean hasDuplicateAuthors(
      final Collection<SignedData<T>> msgs) {
    final long distinctAuthorCount = msgs.stream().map(Authored::getAuthor).distinct().count();
    return distinctAuthorCount != msgs.size();
  }

  /**
   * Has sufficient entries.
   *
   * @param <T> the type parameter
   * @param msgs the msgs
   * @param requiredMsgCount the required msg count
   * @return the boolean
   */
  public static <T extends Payload> boolean hasSufficientEntries(
      final Collection<SignedData<T>> msgs, final long requiredMsgCount) {
    return msgs.size() >= requiredMsgCount;
  }
}
