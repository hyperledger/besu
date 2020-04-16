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

import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateWorldStateReader;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;

public class PrivacyParameters {

  public static final URI DEFAULT_ENCLAVE_URL = URI.create("http://localhost:8888");
  public static final PrivacyParameters DEFAULT = new PrivacyParameters();

  private Integer privacyAddress = Address.PRIVACY;
  private boolean enabled;
  private URI enclaveUri;
  private String enclavePublicKey;
  private File enclavePublicKeyFile;
  private Optional<SECP256K1.KeyPair> signingKeyPair = Optional.empty();
  private Enclave enclave;
  private PrivacyStorageProvider privateStorageProvider;
  private WorldStateArchive privateWorldStateArchive;
  private PrivateStateStorage privateStateStorage;
  private boolean multiTenancyEnabled;
  private boolean onchainPrivacyGroupsEnabled;
  private PrivateStateRootResolver privateStateRootResolver;
  private PrivateWorldStateReader privateWorldStateReader;

  public Integer getPrivacyAddress() {
    return privacyAddress;
  }

  private void setPrivacyAddress(final Integer privacyAddress) {
    this.privacyAddress = privacyAddress;
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

  public String getEnclavePublicKey() {
    return enclavePublicKey;
  }

  @VisibleForTesting
  public void setEnclavePublicKey(final String enclavePublicKey) {
    this.enclavePublicKey = enclavePublicKey;
  }

  public File getEnclavePublicKeyFile() {
    return enclavePublicKeyFile;
  }

  private void setEnclavePublicKeyFile(final File enclavePublicKeyFile) {
    this.enclavePublicKeyFile = enclavePublicKeyFile;
  }

  public Optional<SECP256K1.KeyPair> getSigningKeyPair() {
    return signingKeyPair;
  }

  private void setSigningKeyPair(final SECP256K1.KeyPair signingKeyPair) {
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

  private void setOnchainPrivacyGroupsEnabled(final boolean onchainPrivacyGroupsEnabled) {
    this.onchainPrivacyGroupsEnabled = onchainPrivacyGroupsEnabled;
  }

  public boolean isOnchainPrivacyGroupsEnabled() {
    return onchainPrivacyGroupsEnabled;
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

  @Override
  public String toString() {
    return "PrivacyParameters{"
        + "enabled="
        + enabled
        + ", multiTenancyEnabled = "
        + multiTenancyEnabled
        + ", onchainPrivacyGroupsEnabled = "
        + onchainPrivacyGroupsEnabled
        + ", enclaveUri='"
        + enclaveUri
        + '\''
        + '}';
  }

  public static class Builder {

    private boolean enabled;
    private URI enclaveUrl;
    private Integer privacyAddress = Address.PRIVACY;
    private File enclavePublicKeyFile;
    private String enclavePublicKey;
    private Path privateKeyPath;
    private PrivacyStorageProvider storageProvider;
    private EnclaveFactory enclaveFactory;
    private boolean multiTenancyEnabled;
    private Path privacyKeyStoreFile;
    private Path privacyKeyStorePasswordFile;
    private Path privacyTlsKnownEnclaveFile;
    private boolean onchainPrivacyGroupsEnabled;

    public Builder setPrivacyAddress(final Integer privacyAddress) {
      this.privacyAddress = privacyAddress;
      return this;
    }

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

    public Builder setOnchainPrivacyGroupsEnabled(final boolean onchainPrivacyGroupsEnabled) {
      this.onchainPrivacyGroupsEnabled = onchainPrivacyGroupsEnabled;
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
            new WorldStateArchive(privateWorldStateStorage, privatePreimageStorage);

        final PrivateStateStorage privateStateStorage = storageProvider.createPrivateStateStorage();
        final PrivateStateRootResolver privateStateRootResolver =
            new PrivateStateRootResolver(privateStateStorage);

        config.setPrivateStateRootResolver(privateStateRootResolver);
        config.setPrivateWorldStateReader(
            new PrivateWorldStateReader(
                privateStateRootResolver, privateWorldStateArchive, privateStateStorage));

        config.setPrivateWorldStateArchive(privateWorldStateArchive);
        config.setEnclavePublicKey(enclavePublicKey);
        config.setEnclavePublicKeyFile(enclavePublicKeyFile);
        config.setPrivateStorageProvider(storageProvider);
        config.setPrivateStateStorage(privateStateStorage);
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

        if (privateKeyPath != null) {
          config.setSigningKeyPair(KeyPairUtil.load(privateKeyPath.toFile()));
        }
      }
      config.setEnabled(enabled);
      config.setEnclaveUri(enclaveUrl);
      config.setPrivacyAddress(privacyAddress);
      config.setMultiTenancyEnabled(multiTenancyEnabled);
      config.setOnchainPrivacyGroupsEnabled(onchainPrivacyGroupsEnabled);
      return config;
    }

    public Builder setEnclavePublicKeyUsingFile(final File publicKeyFile) throws IOException {
      this.enclavePublicKeyFile = publicKeyFile;
      this.enclavePublicKey = Files.asCharSource(publicKeyFile, UTF_8).read();
      validatePublicKey(publicKeyFile);
      return this;
    }

    private void validatePublicKey(final File publicKeyFile) {
      if (publicKeyFile.length() != 44) {
        throw new IllegalArgumentException(
            "Contents of enclave public key file needs to be 44 characters long to decode to a valid 32 byte public key.");
      }
      // throws exception if invalid base 64
      Base64.getDecoder().decode(this.enclavePublicKey);
    }
  }
}
