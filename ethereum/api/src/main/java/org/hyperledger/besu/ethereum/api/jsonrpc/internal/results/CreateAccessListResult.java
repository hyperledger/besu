package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.AccessListEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.google.common.collect.Multimap;
import org.apache.tuweni.bytes.Bytes32;

public class CreateAccessListResult {
  List<AccessListEntry> list = new ArrayList<>();
  String gasUsed;

  public CreateAccessListResult(final Multimap<Address, Bytes32> map, final long gasUsed) {
    map.asMap().forEach((k, v) -> list.add(new AccessListEntry(k, new ArrayList<>(v))));
    this.gasUsed = Quantity.create(gasUsed);
  }

  @JsonGetter(value = "accessList")
  public Collection<AccessListEntry> getList() {
    return list;
  }

  @JsonGetter(value = "gasUsed")
  public String getGasUsed() {
    return gasUsed;
  }
}
