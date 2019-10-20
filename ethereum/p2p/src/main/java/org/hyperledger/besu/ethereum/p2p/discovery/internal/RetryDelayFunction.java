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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import java.util.function.UnaryOperator;

/**
 * A function to calculate the next retry delay based on the previous one. In the future, this could
 * be enhanced to take in the number of retries so far, so the function can take a decision to abort
 * if too many retries have been attempted.
 */
interface RetryDelayFunction extends UnaryOperator<Long> {

  static RetryDelayFunction linear(final double multiplier, final long min, final long max) {
    return (prev) -> Math.min(Math.max((long) Math.ceil(prev * multiplier), min), max);
  }
}
