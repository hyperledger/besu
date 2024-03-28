/*
 * Copyright Hyperledger Besu contributors.
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

package org.hyperledger.besu;

import dagger.Module;
import dagger.Provides;
import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;

import java.net.URI;

@Module
public class PrivacyReorgParametersModule {

    //TODO: copypasta, get this from the enclave factory
    private static final Bytes ENCLAVE_PUBLIC_KEY =
            Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

    @Provides
    PrivacyParameters providePrivacyReorgParameters(
            final PrivacyStorageProvider storageProvider, final EnclaveFactory enclaveFactory) {

        PrivacyParameters retval = new PrivacyParameters.Builder()
                .setEnabled(true)
                .setStorageProvider(storageProvider)

                .setEnclaveUrl(URI.create("http//1.1.1.1:1234"))
                .setEnclaveFactory(enclaveFactory)
                .build();
        retval.setPrivacyUserId(ENCLAVE_PUBLIC_KEY.toBase64String());
        return retval;
    }
}
