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
package org.hyperledger.besu.tests.acceptance.plugins;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;
import org.hyperledger.besu.tests.acceptance.plugins.privacy.TestPrivacyGroupGenesisProvider;
import org.hyperledger.besu.tests.acceptance.plugins.privacy.TestPrivacyPluginPayloadProvider;
import org.hyperledger.besu.tests.acceptance.plugins.privacy.TestSigningPrivateMarkerTransactionFactory;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;

@AutoService(BesuPlugin.class)
public class TestPrivacyServicePlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(TestPrivacyServicePlugin.class);

  PrivacyPluginService pluginService;
  BesuContext context;

  TestPrivacyGroupGenesisProvider privacyGroupGenesisProvider =
      new TestPrivacyGroupGenesisProvider();
  TestSigningPrivateMarkerTransactionFactory privateMarkerTransactionFactory =
      new TestSigningPrivateMarkerTransactionFactory();

  @Override
  public void register(final BesuContext context) {
    this.context = context;

    context
        .getService(PicoCLIOptions.class)
        .orElseThrow()
        .addPicoCLIOptions("privacy-service", this);
    pluginService = context.getService(PrivacyPluginService.class).orElseThrow();
    pluginService.setPrivacyGroupGenesisProvider(privacyGroupGenesisProvider);

    LOG.info("Registering Plugins with options " + this);
  }

  @Override
  public void beforeExternalServices() {
    LOG.info("Start Plugins with options {}", this);

    TestPrivacyPluginPayloadProvider payloadProvider = new TestPrivacyPluginPayloadProvider();

    pluginService.setPayloadProvider(payloadProvider);
    payloadProvider.setPluginPayloadPrefix(prefix);

    if (genesisEnabled) {
      privacyGroupGenesisProvider.setGenesisEnabled();
    }

    if (signingEnabled) {
      pluginService.setPrivateMarkerTransactionFactory(privateMarkerTransactionFactory);
      privateMarkerTransactionFactory.setSigningKeyEnbaled(signingKey);
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Option(names = "--plugin-privacy-service-encryption-prefix")
  String prefix;

  @Option(names = "--plugin-privacy-service-genesis-enabled")
  boolean genesisEnabled = false;

  @Option(names = "--plugin-privacy-service-signing-enabled")
  boolean signingEnabled = false;

  @Option(names = "--plugin-privacy-service-signing-key")
  String signingKey;

  @Override
  public String toString() {
    return "TestPrivacyServicePlugin{"
        + "prefix='"
        + prefix
        + '\''
        + ", signingEnabled="
        + signingEnabled
        + ", genesisEnabled="
        + genesisEnabled
        + ", signingKey='"
        + signingKey
        + '\''
        + '}';
  }
}
