package org.hyperledger.besu.consensus.qbft.voting;

import org.hyperledger.besu.consensus.common.ValidatorProvider;
import org.hyperledger.besu.consensus.common.voting.ValidatorVote;
import org.hyperledger.besu.consensus.common.voting.VoteType;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TransactionValidatorProvider implements ValidatorProvider {

  private final Blockchain blockchain;
  private final ValidatorSmartContractController validatorSmartContractController;
  // TODO cache results
  // TODO use fork map to override

  public TransactionValidatorProvider(
      final Blockchain blockchain,
      final ValidatorSmartContractController validatorSmartContractController,
      final Map<Long, List<Address>> bftValidatorForkMap) {
    this.blockchain = blockchain;
    this.validatorSmartContractController = validatorSmartContractController;
  }

  @Override
  public Collection<Address> getValidatorsAtHead() {
    return validatorSmartContractController.getValidators(blockchain.getChainHeadHeader());
  }

  @Override
  public Collection<Address> getValidatorsAfterBlock(final BlockHeader header) {
    return validatorSmartContractController.getValidators(header);
  }

  @Override
  public Optional<ValidatorVote> getVoteAfterBlock(
      final BlockHeader header, final Address localAddress) {
    return Optional.empty();
  }

  @Override
  public void auth(final Address address) {}

  @Override
  public void drop(final Address address) {}

  @Override
  public void discard(final Address address) {}

  @Override
  public Map<Address, VoteType> getProposals() {
    return Collections.emptyMap();
  }
}
