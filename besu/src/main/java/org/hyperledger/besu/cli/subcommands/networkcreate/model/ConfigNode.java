package org.hyperledger.besu.cli.subcommands.networkcreate.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

interface ConfigNode {
  void setParent(final ConfigNode parent);

  @JsonIgnore
  ConfigNode getParent();
}
