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
package org.hyperledger.besu.cli.logging;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;

/** Custom Log4J Configuration Factory for Besu */
public class BesuLoggingConfigurationFactory extends ConfigurationFactory {
  /** Default constructor. */
  public BesuLoggingConfigurationFactory() {}

  @Override
  protected String[] getSupportedTypes() {
    return new String[] {".xml", "*"};
  }

  @Override
  public Configuration getConfiguration(
      final LoggerContext loggerContext, final ConfigurationSource source) {
    return new XmlExtensionConfiguration(loggerContext, source);
  }
}
