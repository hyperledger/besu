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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public class BlockHeaderValidatorTest {

  @SuppressWarnings("unchecked")
  private final ProtocolContext protocolContext = mock(ProtocolContext.class);

  private final MutableBlockchain blockchain = mock(MutableBlockchain.class);
  private final BlockDataGenerator generator = new BlockDataGenerator();

  @Before
  public void setUp() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
  }

  @SuppressWarnings("unchecked")
  private AttachedBlockHeaderValidationRule createFailingAttachedRule() {
    final AttachedBlockHeaderValidationRule rule = mock(AttachedBlockHeaderValidationRule.class);
    when(rule.validate(notNull(), notNull(), eq(protocolContext))).thenReturn(false);
    return rule;
  }

  @SuppressWarnings("unchecked")
  private AttachedBlockHeaderValidationRule createPassingAttachedRule() {
    final AttachedBlockHeaderValidationRule rule = mock(AttachedBlockHeaderValidationRule.class);
    when(rule.validate(notNull(), notNull(), eq(protocolContext))).thenReturn(true);
    return rule;
  }

  @Test
  public void validateHeader() {
    final AttachedBlockHeaderValidationRule passing1 = createPassingAttachedRule();
    final AttachedBlockHeaderValidationRule passing2 = createPassingAttachedRule();

    final BlockHeader blockHeader = generator.header();

    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder().addRule(passing1).addRule(passing2).build();

    assertThat(
            validator.validateHeader(
                blockHeader, blockHeader, protocolContext, HeaderValidationMode.FULL))
        .isTrue();
    verify(passing1).validate(blockHeader, blockHeader, protocolContext);
    verify(passing2).validate(blockHeader, blockHeader, protocolContext);
  }

  @Test
  public void validateHeaderFailingAttachedRule() {
    final AttachedBlockHeaderValidationRule passing1 = createPassingAttachedRule();
    final AttachedBlockHeaderValidationRule failing1 = createFailingAttachedRule();
    final AttachedBlockHeaderValidationRule passing2 = createPassingAttachedRule();

    final BlockHeader blockHeader = generator.header();

    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder()
            .addRule(passing1)
            .addRule(failing1)
            .addRule(passing2)
            .build();

    assertThat(
            validator.validateHeader(
                blockHeader, blockHeader, protocolContext, HeaderValidationMode.FULL))
        .isFalse();
    verify(passing1).validate(blockHeader, blockHeader, protocolContext);
    verify(failing1).validate(blockHeader, blockHeader, protocolContext);
    verify(passing2, never()).validate(blockHeader, blockHeader, protocolContext);
  }

  @Test
  public void validateHeaderFailingDettachedRule() {
    final DetachedBlockHeaderValidationRule passing1 = createPassingDetachedRule(true);
    final DetachedBlockHeaderValidationRule failing1 = createFailingDetachedRule(true);
    final AttachedBlockHeaderValidationRule passing2 = createPassingAttachedRule();

    final BlockHeader blockHeader = generator.header();

    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder()
            .addRule(passing1)
            .addRule(failing1)
            .addRule(passing2)
            .build();

    assertThat(
            validator.validateHeader(
                blockHeader, blockHeader, protocolContext, HeaderValidationMode.FULL))
        .isFalse();
    verify(passing1).validate(blockHeader, blockHeader);
    verify(failing1).validate(blockHeader, blockHeader);
    verify(passing2, never()).validate(blockHeader, blockHeader, protocolContext);
  }

  @Test
  public void validateHeaderChain() {
    final BlockHeader blockHeader = generator.header();

    when(blockchain.getBlockHeader(blockHeader.getParentHash()))
        .thenReturn(Optional.of(blockHeader));

    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder().addRule(createPassingAttachedRule()).build();

    assertThat(validator.validateHeader(blockHeader, protocolContext, HeaderValidationMode.FULL))
        .isTrue();
  }

  @Test
  public void validateHeaderChainFailsWhenParentNotAvailable() {
    final BlockHeader blockHeader = generator.header();

    when(blockchain.getBlockHeader(blockHeader.getNumber() - 1)).thenReturn(Optional.empty());

    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder().addRule(createPassingAttachedRule()).build();

    assertThat(validator.validateHeader(blockHeader, protocolContext, HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void validateHeaderLightChainFailsWhenParentNotAvailable() {
    final BlockHeader blockHeader = generator.header();

    when(blockchain.getBlockHeader(blockHeader.getParentHash())).thenReturn(Optional.empty());

    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder().addRule(createPassingAttachedRule()).build();

    assertThat(validator.validateHeader(blockHeader, protocolContext, HeaderValidationMode.LIGHT))
        .isFalse();
  }

  @Test
  public void shouldSkipAdditionalValidationRulesWhenDoingLightValidation() {
    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder()
            .addRule(createPassingDetachedRule(true))
            .addRule(createFailingDetachedRule(false))
            .build();

    final BlockHeader header = generator.header();
    final BlockHeader parentHeader = generator.header();
    when(blockchain.getBlockHeader(header.getParentHash())).thenReturn(Optional.of(parentHeader));

    assertThat(validator.validateHeader(header, protocolContext, HeaderValidationMode.LIGHT))
        .isTrue();
  }

  @Test
  public void shouldPerformAdditionalValidationRulesWhenDoingFullValidation() {
    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder()
            .addRule(createPassingDetachedRule(true))
            .addRule(createFailingDetachedRule(false))
            .build();

    assertThat(
            validator.validateHeader(
                generator.header(), generator.header(), protocolContext, HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void shouldStillPerformLightValidationRulesWhenDoingFullValidation() {
    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder()
            .addRule(createPassingDetachedRule(true))
            .addRule(createFailingDetachedRule(false))
            .build();

    assertThat(
            validator.validateHeader(
                generator.header(), generator.header(), protocolContext, HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void shouldPerformAttachedValidationRulesWhenDoingLightValidation() {
    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder()
            .addRule(createFailingAttachedRule())
            .addRule(createPassingDetachedRule(true))
            .build();

    assertThat(
            validator.validateHeader(
                generator.header(), generator.header(), protocolContext, HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void shouldRunRulesInOrderOfAdditionDuringFullValidation() {
    final AttachedBlockHeaderValidationRule rule1 = createPassingAttachedRule();
    final DetachedBlockHeaderValidationRule rule2 = createPassingDetachedRule(true);
    final DetachedBlockHeaderValidationRule rule3 = createPassingDetachedRule(false);
    final AttachedBlockHeaderValidationRule rule4 = createPassingAttachedRule();

    final BlockHeaderValidator validator =
        new BlockHeaderValidator.Builder()
            .addRule(rule1)
            .addRule(rule2)
            .addRule(rule3)
            .addRule(rule4)
            .build();

    final BlockHeader header = generator.header();
    final BlockHeader parent = generator.header();
    assertThat(validator.validateHeader(header, parent, protocolContext, HeaderValidationMode.FULL))
        .isTrue();

    final InOrder inOrder = inOrder(rule1, rule2, rule3, rule4);
    inOrder.verify(rule1).validate(header, parent, protocolContext);
    inOrder.verify(rule2).validate(header, parent);
    inOrder.verify(rule3).validate(header, parent);
    inOrder.verify(rule4).validate(header, parent, protocolContext);
  }

  private DetachedBlockHeaderValidationRule createPassingDetachedRule(
      final boolean includeInLightValidation) {
    return createDetachedRule(true, includeInLightValidation);
  }

  private DetachedBlockHeaderValidationRule createFailingDetachedRule(
      final boolean includeInLightValidation) {
    return createDetachedRule(false, includeInLightValidation);
  }

  private DetachedBlockHeaderValidationRule createDetachedRule(
      final boolean passing, final boolean includeInLightValidation) {
    final DetachedBlockHeaderValidationRule rule = mock(DetachedBlockHeaderValidationRule.class);
    when(rule.validate(any(), any())).thenReturn(passing);
    when(rule.includeInLightValidation()).thenReturn(includeInLightValidation);
    return rule;
  }
}
