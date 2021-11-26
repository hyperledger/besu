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
package org.hyperledger.besu.ethereum.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.privacy.PrivateStateGenesisAllocator;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateWorldStateReader;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupGenesisProvider;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;

public class PrivacyParameters {

  // Last address that can be generated for a pre-compiled contract
  public static final Integer PRIVACY = Byte.MAX_VALUE - 1;
  public static final Address DEFAULT_PRIVACY = Address.precompiled(PRIVACY);
  public static final Address FLEXIBLE_PRIVACY = Address.precompiled(PRIVACY - 1);

  // Flexible privacy management contracts (injected in private state)
  public static final Address FLEXIBLE_PRIVACY_PROXY = Address.precompiled(PRIVACY - 2);
  public static final Address DEFAULT_FLEXIBLE_PRIVACY_MANAGEMENT =
      Address.precompiled(PRIVACY - 3);

  public static final Address PLUGIN_PRIVACY = Address.precompiled(PRIVACY - 4);

  public static final URI DEFAULT_ENCLAVE_URL = URI.create("http://localhost:8888");
  public static final PrivacyParameters DEFAULT = new PrivacyParameters();

  private boolean enabled;
  private URI enclaveUri;
  private String privacyUserId;
  private File enclavePublicKeyFile;
  private Optional<KeyPair> signingKeyPair = Optional.empty();
  private Enclave enclave;
  private PrivacyStorageProvider privateStorageProvider;
  private WorldStateArchive privateWorldStateArchive;
  private PrivateStateStorage privateStateStorage;
  private boolean multiTenancyEnabled;
  private boolean flexiblePrivacyGroupsEnabled;
  private boolean privacyPluginEnabled;
  private PrivateStateRootResolver privateStateRootResolver;
  private PrivateWorldStateReader privateWorldStateReader;
  private Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters = Optional.empty();
  private PrivacyPluginService privacyPluginService;

  public Address getPrivacyAddress() {
    if (isPrivacyPluginEnabled()) {
      return PLUGIN_PRIVACY;
    } else if (isFlexiblePrivacyGroupsEnabled()) {
      return FLEXIBLE_PRIVACY;
    } else {
      return DEFAULT_PRIVACY;
    }
  }

  public Boolean isEnabled() {
    return enabled;
  }

  private void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public URI getEnclaveUri() {
    return enclaveUri;
  }

  private void setEnclaveUri(final URI enclaveUri) {
    this.enclaveUri = enclaveUri;
  }

  public String getPrivacyUserId() {
    return privacyUserId;
  }

  @VisibleForTesting
  public void setPrivacyUserId(final String privacyUserId) {
    this.privacyUserId = privacyUserId;
  }

  public File getEnclavePublicKeyFile() {
    return enclavePublicKeyFile;
  }

  private void setEnclavePublicKeyFile(final File enclavePublicKeyFile) {
    this.enclavePublicKeyFile = enclavePublicKeyFile;
  }

  public Optional<KeyPair> getSigningKeyPair() {
    return signingKeyPair;
  }

  private void setSigningKeyPair(final KeyPair signingKeyPair) {
    this.signingKeyPair = Optional.ofNullable(signingKeyPair);
  }

  public WorldStateArchive getPrivateWorldStateArchive() {
    return privateWorldStateArchive;
  }

  private void setPrivateWorldStateArchive(final WorldStateArchive privateWorldStateArchive) {
    this.privateWorldStateArchive = privateWorldStateArchive;
  }

  public PrivacyStorageProvider getPrivateStorageProvider() {
    return privateStorageProvider;
  }

  private void setPrivateStorageProvider(final PrivacyStorageProvider privateStorageProvider) {
    this.privateStorageProvider = privateStorageProvider;
  }

  public PrivateStateStorage getPrivateStateStorage() {
    return privateStateStorage;
  }

  private void setPrivateStateStorage(final PrivateStateStorage privateStateStorage) {
    this.privateStateStorage = privateStateStorage;
  }

  public Enclave getEnclave() {
    return enclave;
  }

  private void setEnclave(final Enclave enclave) {
    this.enclave = enclave;
  }

  private void setMultiTenancyEnabled(final boolean multiTenancyEnabled) {
    this.multiTenancyEnabled = multiTenancyEnabled;
  }

  public boolean isMultiTenancyEnabled() {
    return multiTenancyEnabled;
  }

  private void setFlexiblePrivacyGroupsEnabled(final boolean flexiblePrivacyGroupsEnabled) {
    this.flexiblePrivacyGroupsEnabled = flexiblePrivacyGroupsEnabled;
  }

  public boolean isFlexiblePrivacyGroupsEnabled() {
    return flexiblePrivacyGroupsEnabled;
  }

  private void setPrivacyPluginEnabled(final boolean privacyPluginEnabled) {
    this.privacyPluginEnabled = privacyPluginEnabled;
  }

  public boolean isPrivacyPluginEnabled() {
    return privacyPluginEnabled;
  }

  public PrivateStateRootResolver getPrivateStateRootResolver() {
    return privateStateRootResolver;
  }

  private void setPrivateStateRootResolver(
      final PrivateStateRootResolver privateStateRootResolver) {
    this.privateStateRootResolver = privateStateRootResolver;
  }

  public PrivateWorldStateReader getPrivateWorldStateReader() {
    return privateWorldStateReader;
  }

  private void setPrivateWorldStateReader(final PrivateWorldStateReader privateWorldStateReader) {
    this.privateWorldStateReader = privateWorldStateReader;
  }

  public Optional<GoQuorumPrivacyParameters> getGoQuorumPrivacyParameters() {
    return goQuorumPrivacyParameters;
  }

  private void setGoQuorumPrivacyParameters(
      final Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters) {
    this.goQuorumPrivacyParameters =
        goQuorumPrivacyParameters != null ? goQuorumPrivacyParameters : Optional.empty();
  }

  private void setPrivacyService(final PrivacyPluginService privacyPluginService) {
    this.privacyPluginService = privacyPluginService;
  }

  public PrivacyPluginService getPrivacyService() {
    return privacyPluginService;
  }

  public PrivateStateGenesisAllocator getPrivateStateGenesisAllocator() {
    // Note: the order of plugin registration may cause issues here.
    // This is why it's instantiated on get. It's needed in the privacy pre-compile constructors
    // but privacy parameters is built before the plugin has had a chance to register a provider
    // and have cli options instantiated
    return new PrivateStateGenesisAllocator(
        flexiblePrivacyGroupsEnabled, createPrivateGenesisProvider());
  }

  private PrivacyGroupGenesisProvider createPrivateGenesisProvider() {
    if (privacyPluginService != null
        && privacyPluginService.getPrivacyGroupGenesisProvider() != null) {
      return privacyPluginService.getPrivacyGroupGenesisProvider();
    } else {
      return (privacyGroupId, blockNumber) -> Collections::emptyList;
    }
  }

  @Override
  public String toString() {
    return "PrivacyParameters{"
        + "enabled="
        + enabled
        + ", multiTenancyEnabled = "
        + multiTenancyEnabled
        + ", privacyPluginEnabled = "
        + privacyPluginEnabled
        + ", flexiblePrivacyGroupsEnabled = "
        + flexiblePrivacyGroupsEnabled
        + ", enclaveUri='"
        + enclaveUri
        + ", privatePayloadEncryptionService='"
        + privacyPluginService.getClass().getSimpleName()
        + '\''
        + '}';
  }

  public static class Builder {

    private boolean enabled;
    private URI enclaveUrl;
    private File enclavePublicKeyFile;
    private String privacyUserId;
    private Path privateKeyPath;
    private PrivacyStorageProvider storageProvider;
    private EnclaveFactory enclaveFactory;
    private boolean multiTenancyEnabled;
    private Path privacyKeyStoreFile;
    private Path privacyKeyStorePasswordFile;
    private Path privacyTlsKnownEnclaveFile;
    private boolean flexiblePrivacyGroupsEnabled;
    private boolean privacyPluginEnabled;
    private Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters;
    private PrivacyPluginService privacyPluginService;

    public Builder setEnclaveUrl(final URI enclaveUrl) {
      this.enclaveUrl = enclaveUrl;
      return this;
    }

    public Builder setEnabled(final boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder setStorageProvider(final PrivacyStorageProvider privateStorageProvider) {
      this.storageProvider = privateStorageProvider;
      return this;
    }

    public Builder setPrivateKeyPath(final Path privateKeyPath) {
      this.privateKeyPath = privateKeyPath;
      return this;
    }

    public Builder setEnclaveFactory(final EnclaveFactory enclaveFactory) {
      this.enclaveFactory = enclaveFactory;
      return this;
    }

    public Builder setMultiTenancyEnabled(final boolean multiTenancyEnabled) {
      this.multiTenancyEnabled = multiTenancyEnabled;
      return this;
    }

    public Builder setPrivacyKeyStoreFile(final Path privacyKeyStoreFile) {
      this.privacyKeyStoreFile = privacyKeyStoreFile;
      return this;
    }

    public Builder setPrivacyKeyStorePasswordFile(final Path privacyKeyStorePasswordFile) {
      this.privacyKeyStorePasswordFile = privacyKeyStorePasswordFile;
      return this;
    }

    public Builder setPrivacyTlsKnownEnclaveFile(final Path privacyTlsKnownEnclaveFile) {
      this.privacyTlsKnownEnclaveFile = privacyTlsKnownEnclaveFile;
      return this;
    }

    public Builder setFlexiblePrivacyGroupsEnabled(final boolean flexiblePrivacyGroupsEnabled) {
      this.flexiblePrivacyGroupsEnabled = flexiblePrivacyGroupsEnabled;
      return this;
    }

    public Builder setPrivacyPluginEnabled(final boolean privacyPluginEnabled) {
      this.privacyPluginEnabled = privacyPluginEnabled;
      return this;
    }

    public Builder setGoQuorumPrivacyParameters(
        final Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters) {
      this.goQuorumPrivacyParameters = goQuorumPrivacyParameters;
      return this;
    }

    public Builder setPrivacyUserIdUsingFile(final File publicKeyFile) throws IOException {
      this.enclavePublicKeyFile = publicKeyFile;
      this.privacyUserId = Files.asCharSource(publicKeyFile, UTF_8).read();
      validatePublicKey(publicKeyFile);
      return this;
    }

    public Builder setPrivacyService(final PrivacyPluginService privacyPluginService) {
      this.privacyPluginService = privacyPluginService;
      return this;
    }

    public PrivacyParameters build() {
      final PrivacyParameters config = new PrivacyParameters();
      if (enabled) {
        final WorldStateStorage privateWorldStateStorage =
            storageProvider.createWorldStateStorage();
        final WorldStatePreimageStorage privatePreimageStorage =
            storageProvider.createWorldStatePreimageStorage();
        final WorldStateArchive privateWorldStateArchive =
            new DefaultWorldStateArchive(privateWorldStateStorage, privatePreimageStorage);

        final PrivateStateStorage privateStateStorage = storageProvider.createPrivateStateStorage();
        final PrivateStateRootResolver privateStateRootResolver =
            new PrivateStateRootResolver(privateStateStorage);

        config.setPrivateStateRootResolver(privateStateRootResolver);
        config.setPrivateWorldStateReader(
            new PrivateWorldStateReader(
                privateStateRootResolver, privateWorldStateArchive, privateStateStorage));

        config.setPrivateWorldStateArchive(privateWorldStateArchive);

        config.setPrivacyService(privacyPluginService);

        config.setPrivateStorageProvider(storageProvider);
        config.setPrivateStateStorage(privateStateStorage);

        config.setPrivacyUserId(privacyUserId);
        config.setEnclavePublicKeyFile(enclavePublicKeyFile);
        // pass TLS options to enclave factory if they are set
        if (privacyKeyStoreFile != null) {
          config.setEnclave(
              enclaveFactory.createVertxEnclave(
                  enclaveUrl,
                  privacyKeyStoreFile,
                  privacyKeyStorePasswordFile,
                  privacyTlsKnownEnclaveFile));
        } else {
          config.setEnclave(enclaveFactory.createVertxEnclave(enclaveUrl));
        }
        config.setEnclaveUri(enclaveUrl);

        if (privateKeyPath != null) {
          config.setSigningKeyPair(KeyPairUtil.load(privateKeyPath.toFile()));
        }
      }
      config.setEnabled(enabled);
      config.setMultiTenancyEnabled(multiTenancyEnabled);
      config.setFlexiblePrivacyGroupsEnabled(flexiblePrivacyGroupsEnabled);
      config.setPrivacyPluginEnabled(privacyPluginEnabled);
      config.setGoQuorumPrivacyParameters(goQuorumPrivacyParameters);
      return config;
    }

    private void validatePublicKey(final File publicKeyFile) {
      if (publicKeyFile.length() != 44) {
        throw new IllegalArgumentException(
            "Contents of enclave public key file needs to be 44 characters long to decode to a valid 32 byte public key.");
      }
      // throws exception if invalid base 64
      Base64.getDecoder().decode(this.privacyUserId);
    }
  }
}
