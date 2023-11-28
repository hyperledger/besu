/*
 * Copyright PublicMint.
 * https://www.gnu.org/licenses/gpl-3.0.html
 * SPDX-License-Identifier: GNU GPLv3
 */
package org.hyperledger.besu.ethereum.eth.authtxservice;

import java.util.ArrayList;
import java.util.List;

public class AuthTxServiceConfiguration {
  // Runner configuration
  public boolean isEnabled;

  // connection configuration
  private String amqpEndpoint = "";

  // publish transaction created
  public static final String exchange = "uledger.events";
  public static final String publishTransactionCreatedRoutingKey = "transaction.created";

  // subscribe to authorized
  public static String subscribeTransactionAuthorizedQueue = "uledger:transaction:authorized";
  public static String subscribeTransactionAuthorizedRoutingKey = "transaction.authorized";

  // subscribe to rejected
  public static String subscribeTransactionRejectedQueue = "uledger:transaction:rejected";
  public static String subscribeTransactionRejectedRoutingKey = "transaction.rejected";
  private List<String> genesisAddresses = new ArrayList<>();

  // node configuration
  private String advertisedHost = "";

  public AuthTxServiceConfiguration() {
    this.isEnabled = false;
  }

  public static AuthTxServiceConfiguration createDefault() {
    AuthTxServiceConfiguration config = new AuthTxServiceConfiguration();
    return config;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public void setEnabled() {
    this.isEnabled = true;
  }

  public AuthTxServiceConfiguration setEnabled(final boolean enabled) {
    isEnabled = enabled;
    return this;
  }

  public String getAmqpEndpoint() {
    return amqpEndpoint;
  }

  public AuthTxServiceConfiguration setEndpoint(final String amqpEndpoint) {
    this.amqpEndpoint = amqpEndpoint;
    return this;
  }

  public AuthTxServiceConfiguration setAuthorizedAddresses(final List<String> genesisAddresses) {
    this.genesisAddresses = genesisAddresses;
    return this;
  }

  public List<String> getGenesisAddresses() {
    return genesisAddresses;
  }

  /**
   * Get if client will act as passive mode when authentication service is enabled and endpoint is
   * empty this means that client is just a relayer of transactions.
   */
  public boolean isEnabledAsPassiveMode() {
    return this.isEnabled && this.amqpEndpoint.isEmpty();
  }

  public void setEnode(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
  }

  public String getEnode() {
    return this.advertisedHost;
  }
}
