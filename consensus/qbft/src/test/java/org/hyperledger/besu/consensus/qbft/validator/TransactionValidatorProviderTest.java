package org.hyperledger.besu.consensus.qbft.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.consensus.common.BftValidatorOverrides;
import org.hyperledger.besu.consensus.common.validator.blockbased.VoteTallyCacheTestBase;
import org.hyperledger.besu.ethereum.core.Address;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import org.junit.Test;

public class TransactionValidatorProviderTest extends VoteTallyCacheTestBase {
  final ValidatorContractController validatorContractController =
      mock(ValidatorContractController.class);

  @Test
  public void validatorFromForkAreReturnedRatherThanPriorBlock() {

    final List<Address> forkedValidators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    final Map<Long, List<Address>> forkingValidatorMap = new HashMap<>();
    forkingValidatorMap.put(3L, forkedValidators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(
            blockChain,
            validatorContractController,
            new BftValidatorOverrides(forkingValidatorMap));

    final Collection<Address> result =
        validatorProvider.getValidatorsAfterBlock(block_2.getHeader());

    assertThat(result).containsExactlyElementsOf(forkedValidators);
  }

  // TODO
  // emptyForkingValidatorMapResultsInValidatorsBeingReadFromPreviousHeader

  // TODO
  // validatorsInForkUsedIfForkDirectlyFollowsEpoch

  @Test
  public void atHeadApiOperatesIdenticallyToUnderlyingApi() {
    final List<Address> forkedValidators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    final Map<Long, List<Address>> forkingValidatorMap = new HashMap<>();
    forkingValidatorMap.put(3L, forkedValidators);

    final TransactionValidatorProvider validatorProvider =
        new TransactionValidatorProvider(
            blockChain,
            validatorContractController,
            new BftValidatorOverrides(forkingValidatorMap));

    final Collection<Address> result = validatorProvider.getValidatorsAtHead();

    assertThat(result).containsExactlyElementsOf(forkedValidators);
  }
}
