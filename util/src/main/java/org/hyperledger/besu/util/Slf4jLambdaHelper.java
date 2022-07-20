/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.util;

import java.util.Arrays;
import java.util.function.Supplier;

import org.slf4j.Logger;

/**
 * Static helper class to shim SLF4J with lambda parameter suppliers until the final release of
 * SLF4J 2.0.
 */
public class Slf4jLambdaHelper {

  private Slf4jLambdaHelper() {}

  public static void warnLambda(
      final Logger log, final String message, final Supplier<?>... params) {
    if (log.isWarnEnabled()) {
      log.warn(message, Arrays.stream(params).map(Supplier::get).toArray());
    }
  }

  public static void infoLambda(
      final Logger log, final String message, final Supplier<?>... params) {
    if (log.isInfoEnabled()) {
      log.info(message, Arrays.stream(params).map(Supplier::get).toArray());
    }
  }

  public static void debugLambda(
      final Logger log, final String message, final Supplier<?>... params) {
    if (log.isDebugEnabled()) {
      log.debug(message, Arrays.stream(params).map(Supplier::get).toArray());
    }
  }

  public static void traceLambda(
      final Logger log, final String message, final Supplier<?>... params) {
    if (log.isTraceEnabled()) {
      log.trace(message, Arrays.stream(params).map(Supplier::get).toArray());
    }
  }
}
