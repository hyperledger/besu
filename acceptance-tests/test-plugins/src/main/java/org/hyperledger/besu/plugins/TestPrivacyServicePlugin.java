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
package org.hyperledger.besu.plugins;

import static org.hyperledger.besu.ethereum.privacy.PrivateTransaction.readFrom;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.data.PrivateTransaction;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.PrivacyService;
import org.hyperledger.besu.plugin.services.privacy.PrivacyPayloadEncryptionProvider;

import java.util.Optional;

import com.google.auto.service.AutoService;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine.Option;

@AutoService(BesuPlugin.class)
public class TestPrivacyServicePlugin implements BesuPlugin {
  private Options options;
  private PrivacyService service;

  @Override
  public void register(final BesuContext context) {
    options = new TestPrivacyServicePlugin.Options();
    context.getService(PicoCLIOptions.class).get().addPicoCLIOptions("privacy-service", options);
    service = context.getService(PrivacyService.class).get();
  }

  @Override
  public void start() {
    if (options.enabled) {
      service.setUnrestrictedPayloadEncryptionProvider(
          new PrivacyPayloadEncryptionProvider() {
            @Override
            public Bytes encryptMarkerPayload(
                final PrivateTransaction privateTransaction, final String privacyUserId) {
              return Bytes.fromHexString(options.prefix).and(privateTransaction.getPayload());
            }

            @Override
            public Optional<PrivateTransaction> decryptMarkerPayload(
                final long blockNumber, final Bytes payload) {
              if (payload.toHexString().startsWith(options.prefix)) {
                final BytesValueRLPInput bytesValueRLPInput =
                    new BytesValueRLPInput(payload, false);
                return Optional.of(readFrom(bytesValueRLPInput));
              }

              return Optional.empty();
            }
          });
    }
  }

  @Override
  public void stop() {}

  public static class Options {
    @Option(names = "--plugin-privacy-service-encryption-enabled")
    boolean enabled = false;

    @Option(names = "--plugin-privacy-service-encryption-prefix")
    String prefix;
  }
}
