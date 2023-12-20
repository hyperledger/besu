/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.metrics;

import java.util.function.DoubleSupplier;

public class ReplaceableDoubleSupplier implements DoubleSupplier {
  private DoubleSupplier currentSupplier;

  public ReplaceableDoubleSupplier(final DoubleSupplier currentSupplier) {
    this.currentSupplier = currentSupplier;
  }

  @Override
  public double getAsDouble() {
    return currentSupplier.getAsDouble();
  }

  public ReplaceableDoubleSupplier replaceDoubleSupplier(final DoubleSupplier newSupplier) {
    currentSupplier = newSupplier;
    return this;
  }
}
