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

package org.hyperledger.besu.nat.core;

import org.hyperledger.besu.nat.NatMethod;

import java.util.Objects;

public class AutoDetectionResult {

  private final NatMethod natMethod;
  private final boolean isDetectedNatMethod;

  public AutoDetectionResult(final NatMethod natMethod, final boolean isDetectedNatMethod) {
    this.natMethod = natMethod;
    this.isDetectedNatMethod = isDetectedNatMethod;
  }

  public NatMethod getNatMethod() {
    return natMethod;
  }

  public boolean isDetectedNatMethod() {
    return isDetectedNatMethod;
  }

  @Override
  public int hashCode() {
    return Objects.hash(natMethod, isDetectedNatMethod);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AutoDetectionResult that = (AutoDetectionResult) o;
    return Objects.equals(natMethod, that.natMethod)
        && isDetectedNatMethod == that.isDetectedNatMethod;
  }
}
