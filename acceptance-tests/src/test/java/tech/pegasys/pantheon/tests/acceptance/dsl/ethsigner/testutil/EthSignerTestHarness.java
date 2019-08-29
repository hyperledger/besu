/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner.testutil;

import java.net.URI;
import java.util.Properties;

public class EthSignerTestHarness {
  private final EthSignerConfig config;
  private final Properties portsProperties;

  public EthSignerTestHarness(final EthSignerConfig config, final Properties properties) {
    this.config = config;
    this.portsProperties = properties;
  }

  public URI getHttpListeningUrl() {
    return URI.create(
        "http://"
            + config.getHttpListenHost().getHostAddress()
            + ":"
            + portsProperties.getProperty("http-jsonrpc"));
  }
}
