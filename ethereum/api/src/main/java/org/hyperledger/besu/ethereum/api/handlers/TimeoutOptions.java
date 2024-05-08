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

/** The type Timeout options. */
public class TimeoutOptions {

  /** The constant DEFAULT_ERROR_CODE. */
  public static final int DEFAULT_ERROR_CODE = 504;

  private static final long DEFAULT_TIMEOUT_SECONDS = Duration.ofMinutes(5).toSeconds();
  private final long timeoutSec;

  private final int errorCode;

  /**
   * Instantiates a new Timeout options.
   *
   * @param timeoutSec the timeout sec
   * @param errorCode the error code
   */
  public TimeoutOptions(final long timeoutSec, final int errorCode) {
    this.timeoutSec = timeoutSec;
    this.errorCode = errorCode;
  }

  /**
   * Instantiates a new Timeout options.
   *
   * @param timeoutSec the timeout sec
   */
  public TimeoutOptions(final long timeoutSec) {
    this(timeoutSec, DEFAULT_ERROR_CODE);
  }

  /**
   * Default options timeout options.
   *
   * @return the timeout options
   */
  public static TimeoutOptions defaultOptions() {
    return new TimeoutOptions(DEFAULT_TIMEOUT_SECONDS, DEFAULT_ERROR_CODE);
  }

  /**
   * Gets timeout seconds.
   *
   * @return the timeout seconds
   */
  public long getTimeoutSeconds() {
    return timeoutSec;
  }

  /**
   * Gets timeout millis.
   *
   * @return the timeout millis
   */
  public long getTimeoutMillis() {
    return TimeUnit.SECONDS.toMillis(timeoutSec);
  }

  /**
   * Gets error code.
   *
   * @return the error code
   */
  public int getErrorCode() {
    return errorCode;
  }
}
