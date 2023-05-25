package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.plugin.data.Withdrawal;

public class WithdrawalAdapter {

  Withdrawal withdrawal;

  public WithdrawalAdapter(final Withdrawal withdrawal) {
    this.withdrawal = withdrawal;
  }

  public Long getIndex() {
    return withdrawal.getIndex().toLong();
  }

  public Long getValidator() {
    return withdrawal.getValidatorIndex().toLong();
  }

  public Address getAddress() {
    return withdrawal.getAddress();
  }

  public Long getAmount() {
    return withdrawal.getAmount().getAsBigInteger().longValue();
  }
}
