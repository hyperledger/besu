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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.warnLambda;

import java.util.function.Supplier;

import org.apache.logging.log4j.Level;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jLambdaHelperTest {
  private static final Logger LOG = LoggerFactory.getLogger(Slf4jLambdaHelperTest.class);

  @Test
  public void smokeDebugLambda() {
    Log4j2ConfiguratorUtil.setLevel(LOG.getName(), Level.WARN);
    debugLambda(
        LOG,
        "blah",
        (Supplier<String>)
            () -> {
              throw new RuntimeException("should not evaluate");
            });
    Log4j2ConfiguratorUtil.setLevelDebug(LOG.getName());
    debugLambda(LOG, "blah {}", () -> "stuff");
    debugLambda(LOG, "blah {} {}", () -> "stuff", () -> "stuff2");
  }

  @Test
  public void smokeTraceLambda() {
    traceLambda(
        LOG,
        "blah",
        (Supplier<String>)
            () -> {
              throw new RuntimeException("should not evaluate");
            });
    Log4j2ConfiguratorUtil.setLevel(LOG.getName(), Level.TRACE);
    traceLambda(LOG, "blah {}", () -> "stuff");
    traceLambda(LOG, "blah {} {}", () -> "stuff", () -> "stuff2");
  }

  @Test
  public void smokeWarnLambda() {
    Log4j2ConfiguratorUtil.setLevel(LOG.getName(), Level.OFF);
    traceLambda(
        LOG,
        "blah",
        (Supplier<String>)
            () -> {
              throw new RuntimeException("should not evaluate");
            });
    Log4j2ConfiguratorUtil.setLevel(LOG.getName(), Level.WARN);
    warnLambda(LOG, "blah {}", () -> "stuff");
    warnLambda(LOG, "blah {} {}", () -> "stuff", () -> "stuff2");
  }
}
