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
package org.hyperledger.besu.ethereum.linea;

import static java.util.Objects.isNull;

import java.util.OptionalInt;

public class LineaParameters {
  public static final LineaParameters DEFAULT = new LineaParameters(null, null);

  private final OptionalInt transactionCalldataMaxSize;
  private final OptionalInt blockCalldataMaxSize;

  public LineaParameters(
      final Integer transactionCalldataMaxSize, final Integer blockCalldataMaxSize) {
    this.transactionCalldataMaxSize =
        isNull(transactionCalldataMaxSize)
            ? OptionalInt.empty()
            : OptionalInt.of(transactionCalldataMaxSize);
    this.blockCalldataMaxSize =
        isNull(blockCalldataMaxSize) ? OptionalInt.empty() : OptionalInt.of(blockCalldataMaxSize);
  }

  public OptionalInt maybeTransactionCalldataMaxSize() {
    return transactionCalldataMaxSize;
  }

  public OptionalInt maybeBlockCalldataMaxSize() {
    return blockCalldataMaxSize;
  }
}
