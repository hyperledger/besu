package tech.pegasys.pantheon.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.util.bytes.Bytes32;

import org.junit.Test;

public class PreAllocatedOperandStackTest {

  @Test
  public void construction() {
    final OperandStack stack = new PreAllocatedOperandStack(1);
    assertThat(stack.size()).isEqualTo(0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void construction_NegativeMaximumSize() {
    new PreAllocatedOperandStack(-1);
  }

  @Test(expected = IllegalStateException.class)
  public void push_StackOverflow() {
    final OperandStack stack = new PreAllocatedOperandStack(1);
    stack.push(Bytes32.fromHexString("0x01"));
    stack.push(Bytes32.fromHexString("0x02"));
  }

  @Test(expected = IllegalStateException.class)
  public void pop_StackUnderflow() {
    final OperandStack stack = new PreAllocatedOperandStack(1);
    stack.pop();
  }

  @Test
  public void pushPop() {
    final OperandStack stack = new PreAllocatedOperandStack(1);
    stack.push(Bytes32.fromHexString("0x01"));
    assertThat(stack.size()).isEqualTo(1);
    assertThat(stack.pop()).isEqualTo(Bytes32.fromHexString("0x01"));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void get_NegativeOffset() {
    final OperandStack stack = new PreAllocatedOperandStack(1);
    stack.get(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void get_IndexGreaterThanSize() {
    final OperandStack stack = new PreAllocatedOperandStack(1);
    stack.push(Bytes32.fromHexString("0x01"));
    stack.get(2);
  }

  @Test
  public void get() {
    final OperandStack stack = new PreAllocatedOperandStack(3);
    stack.push(Bytes32.fromHexString("0x01"));
    stack.push(Bytes32.fromHexString("0x02"));
    stack.push(Bytes32.fromHexString("0x03"));
    assertThat(stack.size()).isEqualTo(3);
    assertThat(stack.get(0)).isEqualTo(Bytes32.fromHexString("0x03"));
    assertThat(stack.get(1)).isEqualTo(Bytes32.fromHexString("0x02"));
    assertThat(stack.get(2)).isEqualTo(Bytes32.fromHexString("0x01"));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void set_NegativeOffset() {
    final OperandStack stack = new PreAllocatedOperandStack(1);
    stack.get(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void set_IndexGreaterThanSize() {
    final OperandStack stack = new PreAllocatedOperandStack(1);
    stack.push(Bytes32.fromHexString("0x01"));
    stack.get(2);
  }

  @Test
  public void set() {
    final OperandStack stack = new PreAllocatedOperandStack(3);
    stack.push(Bytes32.fromHexString("0x01"));
    stack.push(Bytes32.fromHexString("0x02"));
    stack.push(Bytes32.fromHexString("0x03"));
    stack.set(2, Bytes32.fromHexString("0x04"));
    assertThat(stack.size()).isEqualTo(3);
    assertThat(stack.get(0)).isEqualTo(Bytes32.fromHexString("0x03"));
    assertThat(stack.get(1)).isEqualTo(Bytes32.fromHexString("0x02"));
    assertThat(stack.get(2)).isEqualTo(Bytes32.fromHexString("0x04"));
  }
}
