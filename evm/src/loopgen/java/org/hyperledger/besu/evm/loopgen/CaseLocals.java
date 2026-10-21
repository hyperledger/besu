/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.loopgen;

import java.util.HashSet;
import java.util.Set;

/**
 * The loop's locals as one case's copy sees them, while it is being copied.
 *
 * <p>Until the case has set the stack top or the pc, it reads them as {@code spBase} or {@code
 * pcBase} plus a constant of its own, which the loop's dispatch table cancels. Every case then
 * computes its stack and code indices from an expression only it has. Were they computed from
 * {@code sp} itself, several cases would share them, and C2 would compute all of them before the
 * dispatch, for every operation, in the registers the loop's state needs. Once the case has set
 * {@code sp} or {@code pc}, it reads the local, which then holds the value it set.
 */
final class CaseLocals {

  /** Which of the stack top and pc a case has set at some point of its code. */
  record Assigned(boolean stackTop, boolean pc) {

    /** What has been set after code that took one of two branches. */
    Assigned either(final Assigned other) {
      return new Assigned(stackTop || other.stackTop, pc || other.pc);
    }
  }

  private final int constant;
  private final Set<String> names = new HashSet<>(UntracedLoop.LOCALS);
  private Assigned assigned = new Assigned(false, false);

  /**
   * The locals of the case with the given index in the loop's switch.
   *
   * @param caseIndex the index, from 1
   */
  CaseLocals(final int caseIndex) {
    this.constant = caseIndex * UntracedLoop.CASE_SPACING;
  }

  String stackTop() {
    return assigned.stackTop() ? "sp" : "(spBase + " + constant + ")";
  }

  String pc() {
    return assigned.pc() ? "pc" : "(pcBase + " + constant + ")";
  }

  /** The assignment of a value, already copied, to the stack top. */
  String assignStackTop(final String value) {
    assigned = new Assigned(true, assigned.pc());
    return "sp = " + value;
  }

  /** The assignment of a value, already copied, to the pc. */
  String assignPc(final String value) {
    assigned = new Assigned(assigned.stackTop(), true);
    return "pc = " + value;
  }

  Assigned assigned() {
    return assigned;
  }

  void restore(final Assigned assigned) {
    this.assigned = assigned;
  }

  /**
   * The name to give a variable the copied code declares: its own, unless the loop or an earlier
   * declaration of the case already uses it.
   */
  String declare(final String name) {
    String candidate = name;
    for (int i = 2; names.contains(candidate); i++) {
      candidate = name + i;
    }
    names.add(candidate);
    return candidate;
  }
}
