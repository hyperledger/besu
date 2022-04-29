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
import java.util.concurrent.TimeUnit;

public class TimeoutOptions {

  public static final int DEFAULT_ERROR_CODE = 504;

  private static final long DEFAULT_TIMEOUT_SECONDS = Duration.ofMinutes(5).toSeconds();
  private final long timeoutSec;

  private final int errorCode;

  public TimeoutOptions(final long timeoutSec, final int errorCode) {
    this.timeoutSec = timeoutSec;
    this.errorCode = errorCode;
  }

  public TimeoutOptions(final long timeoutSec) {
    this(timeoutSec, DEFAULT_ERROR_CODE);
  }

  public static TimeoutOptions defaultOptions() {
    return new TimeoutOptions(DEFAULT_TIMEOUT_SECONDS, DEFAULT_ERROR_CODE);
  }

  public long getTimeoutSeconds() {
    return timeoutSec;
  }

  public long getTimeoutMillis() {
    return TimeUnit.SECONDS.toMillis(timeoutSec);
  }

  public int getErrorCode() {
    return errorCode;
  }
}
