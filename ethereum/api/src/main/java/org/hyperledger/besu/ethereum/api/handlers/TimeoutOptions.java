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
package org.hyperledger.besu.ethereum.api.handlers;

import java.time.Duration;

public class TimeoutOptions {
  private static final long DEFAULT_TIMEOUT = Duration.ofDays(1).toMillis();
  private static final int DEFAULT_ERROR_CODE = 504;
  private final long timeout;

  private final int errorCode;

  public TimeoutOptions(final long timeout, final int errorCode) {
    this.timeout = timeout;
    this.errorCode = errorCode;
  }

  public static TimeoutOptions defaultOptions() {
    return new TimeoutOptions(DEFAULT_TIMEOUT, DEFAULT_ERROR_CODE);
  }

  public long getTimeout() {
    return timeout;
  }

  public int getErrorCode() {
    return errorCode;
  }
}
