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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

/**
 * Copies the code of the most frequent EVM v2 operations into the untraced loop's switch in
 * EVM.java, so that C2 compiles them into the loop without calls and with the loop's state in
 * registers. The operations' code is not changed: the generator finds, in {@code
 * EVM.executeOperationV2}, what each hot opcode runs, copies that method's body, and the bodies of
 * the operations' methods it returns, and makes only these substitutions:
 *
 * <ul>
 *   <li>The frame's stack array, stack top, program counter, gas and code become the loop's locals
 *       {@code s}, {@code sp}, {@code pc}, {@code gas} and {@code codeObject}, and setting the
 *       stack top or the program counter assigns the local.
 *   <li>Until a case has set it, the stack top it reads is {@code spBase} plus a constant of the
 *       case's own, and the program counter {@code pcBase} plus that constant, where the loop takes
 *       {@code spBase} and {@code pcBase} from a table. Each case's indices are then expressions of
 *       their own: shared by several cases, C2 would compute them all before the dispatch, for
 *       every operation.
 *   <li>Returning a result the operation's class declares as a constant success charges its gas and
 *       moves the program counter in the loop. Returning anything else hands the state to the frame
 *       and lets the loop complete the operation as the general path does, which also covers every
 *       halt.
 *   <li>A helper the copied code calls that EVM cannot see is copied into EVM as it is.
 * </ul>
 *
 * Anything else in the copied code, such as a loop, a try, a throw, or any other use of the frame,
 * is rejected, as is code that would no longer mean the same in EVM. {@code ./gradlew
 * :evm:generateEvmV2Loop} writes the copies, and every build checks that EVM.java holds them.
 */
public final class EvmV2LoopGenerator {

  static final String TABLE_BEGIN = "// BEGIN GENERATED case table";
  static final String TABLE_END = "// END GENERATED case table";
  static final String CASES_BEGIN = "// BEGIN GENERATED cases";
  static final String CASES_END = "// END GENERATED cases";

  /**
   * The opcodes whose code the loop runs inline, one case per case of executeOperationV2, in the
   * order of their frequency on mainnet. Each name is the case's constant in EVM.java. JUMP and
   * JUMPI stay on the general path: their check of the destination can analyse the code, a call C2
   * keeps in the loop, and around it the loop's state could not stay in registers.
   */
  private static final Map<String, Integer> HOT = new LinkedHashMap<>();

  static {
    HOT.put("PUSH1", 0x60);
    HOT.put("PUSH2_TO_8", 0x61);
    HOT.put("DUP", 0x80);
    HOT.put("SWAP", 0x90);
    HOT.put("JUMPDEST", 0x5b);
    HOT.put("POP", 0x50);
    HOT.put("ADD", 0x01);
    HOT.put("ISZERO", 0x15);
    HOT.put("AND", 0x16);
    HOT.put("SUB", 0x03);
    HOT.put("LT", 0x10);
    HOT.put("SHL", 0x1b);
    HOT.put("GT", 0x11);
    HOT.put("PUSH0", 0x5f);
    HOT.put("SHR", 0x1c);
    HOT.put("OR", 0x17);
    HOT.put("NOT", 0x19);
    HOT.put("SLT", 0x12);
  }

  /**
   * How far apart the cases' constants are, more than any stack or code offset an operation adds.
   */
  static final int SPACING = 64;

  /** The loop's locals, which copied code must not declare. */
  private static final Set<String> LOOP_NAMES =
      Set.of(
          "frame",
          "code",
          "codeObject",
          "s",
          "sp",
          "pc",
          "gas",
          "opcode",
          "entry",
          "bias",
          "spBase",
          "pcBase",
          "cost",
          "step",
          "result",
          "maxItems",
          "constantinople",
          "shanghai");

  /** EVM's fields the general path's cases read, as the loop's locals that hold them. */
  private static final Map<String, String> EVM_FIELDS =
      Map.of("enableConstantinople", "constantinople", "enableShanghai", "shanghai");

  private static final String SYNC =
      "frame.setTopV2(sp); frame.setPC(pc); frame.setGasRemaining(gas);";

  private static final String OPERATIONS_PACKAGE = "org.hyperledger.besu.evm.v2.operation";

  private final Trees trees;
  private final SourcePositions positions;
  private final String evmSource;
  private final TypeElement evmClass;

  /** Helpers copied into EVM, by their name there. */
  private final Map<String, String> helpers = new LinkedHashMap<>();

  private final Map<ExecutableElement, String> helperNames = new HashMap<>();

  /** Result constants copied into EVM, by their name there. */
  private final Map<String, String> constants = new LinkedHashMap<>();

  private EvmV2LoopGenerator(
      final Trees trees, final String evmSource, final TypeElement evmClass) {
    this.trees = trees;
    this.positions = trees.getSourcePositions();
    this.evmSource = evmSource;
    this.evmClass = evmClass;
  }

  /**
   * Runs the generator.
   *
   * @param args write or check, the main source directory, the compile class path and, for check, a
   *     marker file
   * @throws IOException if a file cannot be read or written
   */
  public static void main(final String[] args) throws IOException {
    if (args.length < 3 || !(args[0].equals("write") || args[0].equals("check"))) {
      System.err.println(
          "usage: EvmV2LoopGenerator write|check <src/main/java> <class path> [<marker file>]");
      System.exit(2);
    }
    final Path sources = Path.of(args[1]);
    final Path evmFile = sources.resolve("org/hyperledger/besu/evm/EVM.java");
    final String current = Files.readString(evmFile, UTF_8);
    final String generated;
    try {
      generated = generate(sources, args[2], current);
    } catch (final GeneratorException e) {
      System.err.println("The EVM v2 loop cannot copy the operations' code: " + e.getMessage());
      System.exit(1);
      return;
    }
    if (args[0].equals("write")) {
      Files.writeString(evmFile, generated, UTF_8);
      System.out.println("Wrote " + HOT.size() + " cases into " + evmFile);
    } else {
      final String expected = withoutWhitespace(generated);
      final String actual = withoutWhitespace(current);
      if (!expected.equals(actual)) {
        int at = 0;
        while (at < Math.min(expected.length(), actual.length())
            && expected.charAt(at) == actual.charAt(at)) {
          at++;
        }
        System.err.println(
            evmFile
                + " does not hold the copies of the operations' code. Run ./gradlew"
                + " :evm:generateEvmV2Loop after changing an operation the untraced loop copies."
                + " First difference, without whitespace: expected "
                + expected.substring(at, Math.min(expected.length(), at + 80))
                + " but found "
                + actual.substring(at, Math.min(actual.length(), at + 80)));
        System.exit(1);
      }
      if (args.length > 3) {
        final Path marker = Path.of(args[3]);
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "checked " + HOT.size() + " cases\n", UTF_8);
      }
    }
  }

  private static String withoutWhitespace(final String text) {
    final StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (!Character.isWhitespace(c)) {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static String generate(final Path sources, final String classPath, final String current)
      throws IOException {
    final List<Path> files = new ArrayList<>();
    files.add(sources.resolve("org/hyperledger/besu/evm/EVM.java"));
    try (Stream<Path> operations =
        Files.list(sources.resolve("org/hyperledger/besu/evm/v2/operation"))) {
      operations.filter(f -> f.toString().endsWith(".java")).sorted().forEach(files::add);
    }
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8)) {
      final JavacTask task =
          (JavacTask)
              compiler.getTask(
                  null,
                  fileManager,
                  diagnostics,
                  List.of(
                      "-proc:none",
                      "-implicit:none",
                      "-sourcepath",
                      sources.toString(),
                      "-classpath",
                      classPath),
                  null,
                  fileManager.getJavaFileObjectsFromPaths(files));
      final Iterable<? extends CompilationUnitTree> units = task.parse();
      task.analyze();
      final List<String> errors = new ArrayList<>();
      for (final Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
        // EVM.java may not compile before its generated regions are written; javac still
        // attributes the general path the copies come from
        if (d.getKind() == Diagnostic.Kind.ERROR
            && (d.getSource() == null || !d.getSource().getName().endsWith("EVM.java"))) {
          errors.add(d.toString());
        }
      }
      if (!errors.isEmpty()) {
        throw new GeneratorException("the sources do not compile:\n" + String.join("\n", errors));
      }
      final Trees trees = Trees.instance(task);
      CompilationUnitTree evmUnit = null;
      for (final CompilationUnitTree unit : units) {
        if (unit.getSourceFile().getName().endsWith("EVM.java")) {
          evmUnit = unit;
        }
      }
      if (evmUnit == null) {
        throw new GeneratorException("EVM.java was not parsed");
      }
      final ClassTree evmTree =
          evmUnit.getTypeDecls().stream()
              .filter(ClassTree.class::isInstance)
              .map(ClassTree.class::cast)
              .findFirst()
              .orElseThrow();
      final TypeElement evmClass =
          (TypeElement) trees.getElement(TreePath.getPath(evmUnit, evmTree));
      final EvmV2LoopGenerator generator =
          new EvmV2LoopGenerator(
              trees, evmUnit.getSourceFile().getCharContent(true).toString(), evmClass);
      return generator.generate(evmUnit, evmTree, current);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // The cases

  private String generate(
      final CompilationUnitTree evmUnit, final ClassTree evmTree, final String current) {
    final Map<Integer, TreePath> caseByOpcode = generalPathCases(evmUnit, evmTree);
    final StringBuilder cases =
        new StringBuilder(CASES_BEGIN)
            .append(": copies of the operations' code;\n")
            .append("// edit those, then run ./gradlew :evm:generateEvmV2Loop\n");
    final StringBuilder table = new StringBuilder();
    int index = 1;
    for (final Map.Entry<String, Integer> hot : HOT.entrySet()) {
      final TreePath casePath = caseByOpcode.get(hot.getValue());
      if (casePath == null) {
        throw new GeneratorException(
            "executeOperationV2 has no case for opcode 0x" + Integer.toHexString(hot.getValue()));
      }
      final CaseTree caseTree = (CaseTree) casePath.getLeaf();
      final String name = "COPIED_" + hot.getKey();
      table.append("private static final int ").append(name).append(" = ").append(index);
      table.append(";\n");
      final Copy copy = new Copy(index * SPACING);
      final Scope scope = new Scope(null);
      final TreePath body = new TreePath(casePath, caseTree.getBody());
      final String code = unblocked(copy.returning(body, scope, true));
      final String from = describe(caseTree.getBody());
      cases.append("// BEGIN copied from ").append(from).append("\n");
      cases.append("case ").append(name).append(" -> {\n").append(code).append("\n}\n");
      cases.append("// END copied from ").append(from).append("\n");
      index++;
    }
    cases.append(CASES_END);

    final StringBuilder header = new StringBuilder(TABLE_BEGIN);
    header.append(
        " for the copies of the operations' code in the untraced loop\n"
            + "// below; do not edit, run ./gradlew :evm:generateEvmV2Loop\n");
    header.append(table);
    header.append(
        "\n// Per opcode: its case in the low byte, and above it the distance of the case's\n"
            + "// constant from the stack top and pc that its copied code reads\n");
    header.append("private static final int[] DISPATCH = new int[256];\n\nstatic {\n");
    for (final Map.Entry<String, Integer> hot : HOT.entrySet()) {
      final List<Integer> opcodes = labels((CaseTree) caseByOpcode.get(hot.getValue()).getLeaf());
      for (final int[] run : runs(opcodes)) {
        header
            .append("dispatch(COPIED_")
            .append(hot.getKey())
            .append(", ")
            .append(hex(run[0]))
            .append(", ")
            .append(hex(run[1]))
            .append(");\n");
      }
    }
    header.append("}\n\n");
    header.append(
        "private static void dispatch(final int copiedCase, final int first, final int last) {\n"
            + "for (int op = first; op <= last; op++) {\n"
            + "DISPATCH[op] = ((copiedCase * "
            + SPACING
            + ") << 8) | copiedCase;\n}\n}\n");
    for (final Map.Entry<String, String> constant : constants.entrySet()) {
      header.append('\n').append(constant.getValue());
    }
    for (final Map.Entry<String, String> helper : helpers.entrySet()) {
      header.append('\n').append(helper.getValue());
    }
    header.append(TABLE_END);
    return replaceRegion(
        replaceRegion(current, TABLE_BEGIN, TABLE_END, header.toString(), 2),
        CASES_BEGIN,
        CASES_END,
        cases.toString(),
        8);
  }

  /** A copy without the braces of a single block around it, which the case's own braces replace. */
  private static String unblocked(final String code) {
    if (!code.startsWith("{") || !code.endsWith("}")) {
      return code;
    }
    int depth = 0;
    for (int i = 0; i < code.length(); i++) {
      depth += code.charAt(i) == '{' ? 1 : code.charAt(i) == '}' ? -1 : 0;
      if (depth == 0 && i < code.length() - 1) {
        return code;
      }
    }
    return code.substring(1, code.length() - 1).strip();
  }

  /** The cases of executeOperationV2's switch, by each opcode they list. */
  private Map<Integer, TreePath> generalPathCases(
      final CompilationUnitTree evmUnit, final ClassTree evmTree) {
    final Map<Integer, TreePath> cases = new HashMap<>();
    for (final Tree member : evmTree.getMembers()) {
      if (!(member instanceof MethodTree method)
          || !method.getName().contentEquals("executeOperationV2")) {
        continue;
      }
      new TreePathScanner<Void, Void>() {
        @Override
        public Void visitSwitchExpression(final SwitchExpressionTree node, final Void unused) {
          for (final CaseTree c : node.getCases()) {
            final TreePath path = new TreePath(getCurrentPath(), c);
            for (final int opcode : labels(c)) {
              cases.put(opcode, path);
            }
          }
          return null;
        }
      }.scan(new TreePath(TreePath.getPath(evmUnit, evmTree), method), null);
    }
    if (cases.isEmpty()) {
      throw new GeneratorException("EVM has no executeOperationV2 with a switch");
    }
    return cases;
  }

  private static List<Integer> labels(final CaseTree caseTree) {
    final List<Integer> opcodes = new ArrayList<>();
    for (final ExpressionTree label : caseTree.getExpressions()) {
      if (label instanceof LiteralTree literal && literal.getValue() instanceof Integer value) {
        opcodes.add(value);
      }
    }
    return opcodes;
  }

  private static List<int[]> runs(final List<Integer> opcodes) {
    final List<int[]> runs = new ArrayList<>();
    for (final int opcode : new TreeSet<>(opcodes)) {
      if (!runs.isEmpty() && runs.getLast()[1] == opcode - 1) {
        runs.getLast()[1] = opcode;
      } else {
        runs.add(new int[] {opcode, opcode});
      }
    }
    return runs;
  }

  private static String hex(final int value) {
    return String.format("0x%02x", value);
  }

  private String describe(final Tree tree) {
    final List<String> names = new ArrayList<>();
    new TreeScanner<Void, Void>() {
      @Override
      public Void visitMethodInvocation(final MethodInvocationTree node, final Void unused) {
        final ExpressionTree select = node.getMethodSelect();
        names.add(
            select instanceof MemberSelectTree member
                ? member.getExpression() + "." + member.getIdentifier()
                : select.toString());
        return super.visitMethodInvocation(node, unused);
      }

      @Override
      public Void visitMemberSelect(final MemberSelectTree node, final Void unused) {
        if (names.isEmpty()) {
          names.add(node.toString());
        }
        return null;
      }
    }.scan(tree, null);
    return names.isEmpty() ? tree.toString() : names.getFirst();
  }

  // ---------------------------------------------------------------------------------------------
  // Copying

  /** What a name of the copied code stands for in the copy. */
  private static final class Scope {
    final Scope parent;
    final Map<Element, String> names = new HashMap<>();
    final Map<Element, Binding> bindings = new HashMap<>();
    final Set<Element> frames = new HashSet<>();

    Scope(final Scope parent) {
      this.parent = parent;
    }
  }

  /** A parameter bound to the argument the caller passed, which is copied in the caller's scope. */
  private record Binding(TreePath argument, Scope scope) {}

  /** The copy of one case. */
  private final class Copy {
    private final int offset;
    private final Set<String> declared = new HashSet<>(LOOP_NAMES);
    private boolean spSet;
    private boolean pcSet;

    Copy(final int offset) {
      this.offset = offset;
    }

    private String sp() {
      return spSet ? "sp" : "(spBase + " + offset + ")";
    }

    private String pc() {
      return pcSet ? "pc" : "(pcBase + " + offset + ")";
    }

    /** Copies {@code return expression;}, as the case's last statement when tail. */
    String returning(final TreePath expression, final Scope scope, final boolean tail) {
      final Tree leaf = expression.getLeaf();
      final String exit = tail ? "" : "\nbreak;";
      if (leaf instanceof ParenthesizedTree parens) {
        return returning(new TreePath(expression, parens.getExpression()), scope, tail);
      }
      if (leaf instanceof ConditionalExpressionTree conditional) {
        final String condition =
            expression(new TreePath(expression, conditional.getCondition()), scope);
        final boolean spBefore = spSet;
        final boolean pcBefore = pcSet;
        final String whenTrue =
            returning(new TreePath(expression, conditional.getTrueExpression()), scope, tail);
        spSet = spBefore;
        pcSet = pcBefore;
        final String whenFalse =
            returning(new TreePath(expression, conditional.getFalseExpression()), scope, tail);
        spSet = spBefore;
        pcSet = pcBefore;
        return "if (" + condition + ") {\n" + whenTrue + "\n} else {\n" + whenFalse + "\n}";
      }
      final Element element = trees.getElement(expression);
      if (leaf instanceof IdentifierTree && element != null && bound(scope, element) != null) {
        final Binding binding = bound(scope, element);
        return returning(binding.argument(), binding.scope(), tail);
      }
      if ((leaf instanceof IdentifierTree || leaf instanceof MemberSelectTree)
          && element instanceof VariableElement field
          && field.getKind() == ElementKind.FIELD
          && isResult(field)) {
        final long[] success = successOf(field);
        if (success != null) {
          return "cost = " + success[0] + "L;\nstep = " + success[1] + ";" + exit;
        }
        return SYNC + "\nresult = " + resultConstant(field) + ";" + exit;
      }
      if (leaf instanceof MethodInvocationTree invocation
          && element instanceof ExecutableElement method
          && method.getModifiers().contains(Modifier.STATIC)
          && isOperationsCode(method)) {
        return inlined(expression, invocation, method, scope, tail);
      }
      return SYNC + "\nresult = " + frameExpression(expression, scope) + ";" + exit;
    }

    /** Copies the body of a method whose result is returned, with its parameters bound. */
    private String inlined(
        final TreePath call,
        final MethodInvocationTree invocation,
        final ExecutableElement method,
        final Scope scope,
        final boolean tail) {
      final MethodTree tree = trees.getTree(method);
      final TreePath path = trees.getPath(method);
      if (tree == null || path == null || tree.getBody() == null) {
        throw new GeneratorException("no source for " + method);
      }
      final Scope inner = new Scope(scope);
      final StringBuilder out = new StringBuilder("{\n");
      for (int i = 0; i < tree.getParameters().size(); i++) {
        final VariableTree parameter = tree.getParameters().get(i);
        final Element element = trees.getElement(new TreePath(path, parameter));
        final TreePath argument = new TreePath(call, invocation.getArguments().get(i));
        if (isFrame(argument, scope)) {
          inner.frames.add(element);
        } else if (isPlain(argument.getLeaf())) {
          inner.bindings.put(element, new Binding(argument, scope));
        } else {
          final String name = declare(parameter.getName().toString());
          inner.names.put(element, name);
          // not final for a literal, so that javac and Error Prone do not fold the copied
          // conditions on it into constants; C2 folds them
          out.append(argument.getLeaf() instanceof LiteralTree ? "" : "final ")
              .append(parameter.getType())
              .append(' ')
              .append(name)
              .append(" = ")
              .append(expression(argument, scope))
              .append(";\n");
        }
      }
      out.append(
          statements(
              new TreePath(path, tree.getBody()), tree.getBody().getStatements(), inner, tail));
      return out.append("\n}").toString();
    }

    private String statements(
        final TreePath block,
        final List<? extends StatementTree> statements,
        final Scope scope,
        final boolean tail) {
      final StringBuilder out = new StringBuilder();
      for (int i = 0; i < statements.size(); i++) {
        final boolean last = i == statements.size() - 1;
        out.append(statement(new TreePath(block, statements.get(i)), scope, tail && last));
        out.append('\n');
      }
      return out.toString();
    }

    private String statement(final TreePath path, final Scope scope, final boolean tail) {
      final Tree leaf = path.getLeaf();
      if (leaf instanceof ReturnTree ret) {
        return returning(new TreePath(path, ret.getExpression()), scope, tail);
      }
      if (leaf instanceof BlockTree block) {
        return "{\n" + statements(path, block.getStatements(), scope, tail) + "}";
      }
      if (leaf instanceof IfTree ifTree) {
        final StringBuilder out = new StringBuilder("if ");
        out.append(expression(new TreePath(path, ifTree.getCondition()), scope)).append(' ');
        final boolean spBefore = spSet;
        final boolean pcBefore = pcSet;
        out.append(braced(new TreePath(path, ifTree.getThenStatement()), scope, tail));
        final boolean thenExits = exits(ifTree.getThenStatement());
        final boolean spThen = thenExits ? spBefore : spSet;
        final boolean pcThen = thenExits ? pcBefore : pcSet;
        spSet = spBefore;
        pcSet = pcBefore;
        boolean spElse = spBefore;
        boolean pcElse = pcBefore;
        if (ifTree.getElseStatement() != null) {
          out.append(" else ")
              .append(braced(new TreePath(path, ifTree.getElseStatement()), scope, tail));
          if (!exits(ifTree.getElseStatement())) {
            spElse = spSet;
            pcElse = pcSet;
          }
        }
        spSet = spThen || spElse;
        pcSet = pcThen || pcElse;
        return out.toString();
      }
      if (leaf instanceof ExpressionStatementTree statement) {
        return expression(new TreePath(path, statement.getExpression()), scope) + ";";
      }
      if (leaf instanceof VariableTree variable) {
        final Element element = trees.getElement(path);
        final String name = declare(variable.getName().toString());
        scope.names.put(element, name);
        final boolean isFinal = variable.getModifiers().getFlags().contains(Modifier.FINAL);
        return (isFinal ? "final " : "")
            + variable.getType()
            + " "
            + name
            + (variable.getInitializer() == null
                ? ""
                : " = " + expression(new TreePath(path, variable.getInitializer()), scope))
            + ";";
      }
      throw reject(path, "a " + leaf.getKind() + " statement");
    }

    private boolean exits(final StatementTree statement) {
      if (statement instanceof ReturnTree) {
        return true;
      }
      if (statement instanceof BlockTree block) {
        return !block.getStatements().isEmpty() && exits(block.getStatements().getLast());
      }
      if (statement instanceof IfTree ifTree) {
        return ifTree.getElseStatement() != null
            && exits(ifTree.getThenStatement())
            && exits(ifTree.getElseStatement());
      }
      return false;
    }

    private String braced(final TreePath path, final Scope scope, final boolean tail) {
      final String copied = statement(path, scope, tail);
      return path.getLeaf() instanceof BlockTree ? copied : "{\n" + copied + "\n}";
    }

    private String declare(final String name) {
      String candidate = name;
      for (int i = 2; declared.contains(candidate); i++) {
        candidate = name + i;
      }
      declared.add(candidate);
      return candidate;
    }

    /** Copies an expression the copy runs with the loop's state in its locals. */
    String expression(final TreePath path, final Scope scope) {
      return rewrite(path, scope, false);
    }

    /** Copies an expression the copy runs once it has handed its state to the frame. */
    String frameExpression(final TreePath path, final Scope scope) {
      return rewrite(path, scope, true);
    }

    private String rewrite(final TreePath path, final Scope scope, final boolean withFrame) {
      final String replacedWhole = replacement(path, scope, withFrame);
      if (replacedWhole != null) {
        return replacedWhole;
      }
      final String source = sourceOf(path);
      final long start = positions.getStartPosition(path.getCompilationUnit(), path.getLeaf());
      final List<long[]> spans = new ArrayList<>();
      final List<String> texts = new ArrayList<>();
      new TreePathScanner<Void, Void>() {
        @Override
        public Void scan(final Tree tree, final Void unused) {
          if (tree == null) {
            return null;
          }
          final TreePath here = new TreePath(getCurrentPath(), tree);
          final String replaced = replacement(here, scope, withFrame);
          if (replaced != null) {
            final CompilationUnitTree unit = here.getCompilationUnit();
            spans.add(
                new long[] {
                  positions.getStartPosition(unit, tree) - start,
                  positions.getEndPosition(unit, tree) - start
                });
            texts.add(replaced);
            return null;
          }
          return super.scan(tree, unused);
        }
      }.scan(path, null);
      final StringBuilder out = new StringBuilder(source);
      for (int i = spans.size() - 1; i >= 0; i--) {
        out.replace((int) spans.get(i)[0], (int) spans.get(i)[1], texts.get(i));
      }
      return out.toString();
    }

    /** The copy of a subtree that does not mean the same in the loop, or null to copy it as is. */
    private String replacement(final TreePath path, final Scope scope, final boolean withFrame) {
      final Tree leaf = path.getLeaf();
      if (leaf instanceof MethodInvocationTree invocation) {
        return invocation(path, invocation, scope, withFrame);
      }
      if (leaf instanceof IdentifierTree identifier) {
        return identifier(path, identifier, scope, withFrame);
      }
      if (leaf instanceof MemberSelectTree select) {
        final Element element = trees.getElement(path);
        if (element instanceof VariableElement field
            && field.getKind() == ElementKind.FIELD
            && field.getModifiers().contains(Modifier.STATIC)
            && !isEvmCode(path)) {
          return visibleFromEvm(field)
              ? qualified((TypeElement) field.getEnclosingElement()) + "." + field.getSimpleName()
              : constantOf(field, path);
        }
        return null;
      }
      switch (leaf.getKind()) {
        case LAMBDA_EXPRESSION, MEMBER_REFERENCE, SWITCH_EXPRESSION ->
            throw reject(path, "a " + leaf.getKind());
        default -> {
          return null;
        }
      }
    }

    private String invocation(
        final TreePath path,
        final MethodInvocationTree invocation,
        final Scope scope,
        final boolean withFrame) {
      final Element element = trees.getElement(path);
      if (!(element instanceof ExecutableElement method)) {
        throw reject(path, "a call C2 cannot resolve");
      }
      final ExpressionTree select = invocation.getMethodSelect();
      final List<String> arguments = new ArrayList<>();
      for (final ExpressionTree argument : invocation.getArguments()) {
        final TreePath argumentPath = new TreePath(path, argument);
        if (isFrame(argumentPath, scope) && !withFrame) {
          throw reject(path, "the frame passed to " + method.getSimpleName());
        }
        arguments.add(rewrite(argumentPath, scope, withFrame));
      }
      if (select instanceof MemberSelectTree member
          && isFrame(new TreePath(path, member.getExpression()), scope)) {
        return withFrame
            ? "frame." + method.getSimpleName() + "(" + String.join(", ", arguments) + ")"
            : frameAccess(path, method.getSimpleName().toString(), arguments);
      }
      if (method.getModifiers().contains(Modifier.STATIC)) {
        final String callee =
            visibleFromEvm(method)
                ? qualified((TypeElement) method.getEnclosingElement())
                    + "."
                    + method.getSimpleName()
                : helper(method, path);
        return callee + "(" + String.join(", ", arguments) + ")";
      }
      if (select instanceof MemberSelectTree member) {
        return rewrite(new TreePath(path, member.getExpression()), scope, withFrame)
            + "."
            + member.getIdentifier()
            + "("
            + String.join(", ", arguments)
            + ")";
      }
      throw reject(path, "a call of an instance method of the operation");
    }

    private String frameAccess(
        final TreePath path, final String name, final List<String> arguments) {
      switch (name) {
        case "stackDataV2" -> {
          return "s";
        }
        case "stackTopV2" -> {
          return sp();
        }
        case "stackHasItemsV2" -> {
          return "(" + sp() + " >= " + arguments.getFirst() + ")";
        }
        case "stackHasSpaceV2" -> {
          return "(" + sp() + " + " + arguments.getFirst() + " <= maxItems)";
        }
        case "setTopV2" -> {
          spSet = true;
          return "sp = " + arguments.getFirst();
        }
        case "getPC" -> {
          return pc();
        }
        case "setPC" -> {
          pcSet = true;
          return "pc = " + arguments.getFirst();
        }
        case "getRemainingGas" -> {
          return "gas";
        }
        case "getCode" -> {
          return "codeObject";
        }
        default -> throw reject(path, "the frame's " + name + "()");
      }
    }

    private String identifier(
        final TreePath path,
        final IdentifierTree identifier,
        final Scope scope,
        final boolean withFrame) {
      if (path.getParentPath().getLeaf() instanceof MethodInvocationTree call
          && call.getMethodSelect() == identifier) {
        return null;
      }
      final Element element = trees.getElement(path);
      if (element == null) {
        return null;
      }
      if (isFrameElement(element, scope)) {
        if (withFrame) {
          return "frame";
        }
        throw reject(path, "the frame used other than through its stack, pc and gas");
      }
      for (Scope s = scope; s != null; s = s.parent) {
        if (s.names.containsKey(element)) {
          return s.names.get(element);
        }
        if (s.bindings.containsKey(element)) {
          final Binding binding = s.bindings.get(element);
          return term(rewrite(binding.argument(), binding.scope(), withFrame));
        }
      }
      if (element.getKind() == ElementKind.PARAMETER
          && element.getEnclosingElement().getEnclosingElement().equals(evmClass)) {
        // the general path's own parameters are the loop's locals
        return switch (element.getSimpleName().toString()) {
          case "pc" -> pc();
          case "opcode" -> "opcode";
          case "code" -> "code";
          default -> throw reject(path, "executeOperationV2's parameter " + element);
        };
      }
      if (element.getKind() == ElementKind.FIELD) {
        final VariableElement field = (VariableElement) element;
        if (field.getEnclosingElement().equals(evmClass)
            && !field.getModifiers().contains(Modifier.STATIC)) {
          final String local = EVM_FIELDS.get(field.getSimpleName().toString());
          if (local == null) {
            throw reject(path, "EVM's field " + field.getSimpleName());
          }
          return local;
        }
        if (!field.getModifiers().contains(Modifier.STATIC)) {
          throw reject(path, "an instance field");
        }
        if (isEvmCode(path)) {
          return null;
        }
        return visibleFromEvm(field)
            ? qualified((TypeElement) field.getEnclosingElement()) + "." + field.getSimpleName()
            : constantOf(field, path);
      }
      if (element.getKind() == ElementKind.LOCAL_VARIABLE
          || element.getKind() == ElementKind.PARAMETER) {
        throw reject(path, "a variable of the operation the copy did not declare: " + element);
      }
      return null;
    }

    private static String term(final String text) {
      return text.matches("[\\w.$]+|\\(.*\\)") && balanced(text) ? text : "(" + text + ")";
    }

    private static boolean balanced(final String text) {
      if (!text.startsWith("(")) {
        return true;
      }
      int depth = 0;
      for (int i = 0; i < text.length(); i++) {
        depth += text.charAt(i) == '(' ? 1 : text.charAt(i) == ')' ? -1 : 0;
        if (depth == 0 && i < text.length() - 1) {
          return false;
        }
      }
      return true;
    }

    private Binding bound(final Scope scope, final Element element) {
      for (Scope s = scope; s != null; s = s.parent) {
        if (s.bindings.containsKey(element)) {
          return s.bindings.get(element);
        }
      }
      return null;
    }

    private boolean isFrame(final TreePath path, final Scope scope) {
      final Element element = trees.getElement(path);
      return path.getLeaf() instanceof IdentifierTree
          && element != null
          && isFrameElement(element, scope);
    }

    private boolean isFrameElement(final Element element, final Scope scope) {
      if (element.getKind() == ElementKind.PARAMETER
          && element.getSimpleName().contentEquals("frame")
          && element.getEnclosingElement().getEnclosingElement().equals(evmClass)) {
        return true;
      }
      for (Scope s = scope; s != null; s = s.parent) {
        if (s.frames.contains(element)) {
          return true;
        }
      }
      return false;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // What copied code refers to

  private boolean isEvmCode(final TreePath path) {
    return path.getCompilationUnit().getSourceFile().getName().endsWith("EVM.java");
  }

  private static boolean isOperationsCode(final ExecutableElement method) {
    final Element owner = method.getEnclosingElement();
    return owner instanceof TypeElement type
        && type.getQualifiedName().toString().startsWith(OPERATIONS_PACKAGE + ".");
  }

  /** A side-effect-free argument, which a copy may name where the parameter appears. */
  private static boolean isPlain(final Tree argument) {
    return argument instanceof IdentifierTree
        || (argument instanceof MemberSelectTree member
            && member.getExpression() instanceof IdentifierTree)
        || (argument instanceof MethodInvocationTree call
            && call.getArguments().isEmpty()
            && call.getMethodSelect() instanceof MemberSelectTree member
            && member.getIdentifier().contentEquals("stackDataV2"));
  }

  private boolean isResult(final VariableElement field) {
    return field.asType().toString().endsWith("OperationResult")
        && field.getModifiers().contains(Modifier.STATIC)
        && field.getModifiers().contains(Modifier.FINAL);
  }

  /** The gas cost and pc step of a result constant that succeeds, or null for a halt. */
  private long[] successOf(final VariableElement field) {
    final NewClassTree created = initializer(field);
    final List<? extends ExpressionTree> arguments = created.getArguments();
    if (arguments.size() < 2
        || arguments.size() > 3
        || arguments.get(1).getKind() != Tree.Kind.NULL_LITERAL) {
      return null;
    }
    final long cost = literal(arguments.get(0), field);
    final long step = arguments.size() == 3 ? literal(arguments.get(2), field) : 1;
    return new long[] {cost, step};
  }

  private NewClassTree initializer(final VariableElement field) {
    final Tree tree = trees.getTree(field);
    if (tree instanceof VariableTree variable
        && variable.getInitializer() instanceof NewClassTree created
        && created.getIdentifier().toString().endsWith("OperationResult")) {
      return created;
    }
    throw new GeneratorException(
        field.getEnclosingElement() + "." + field + " is not a new OperationResult(...)");
  }

  private static long literal(final ExpressionTree expression, final VariableElement field) {
    if (expression instanceof LiteralTree literal && literal.getValue() instanceof Number number) {
      return number.longValue();
    }
    throw new GeneratorException(
        field.getEnclosingElement() + "." + field + " has a cost or step that is not a literal");
  }

  /** The name in EVM of a result constant EVM cannot see, copied once. */
  private String resultConstant(final VariableElement field) {
    if (visibleFromEvm(field)) {
      return qualified((TypeElement) field.getEnclosingElement()) + "." + field.getSimpleName();
    }
    final TypeElement owner = (TypeElement) field.getEnclosingElement();
    final String name =
        upperSnake(owner.getSimpleName().toString()) + "_" + field.getSimpleName().toString();
    if (!constants.containsKey(name)) {
      final NewClassTree created = initializer(field);
      final List<String> arguments = new ArrayList<>();
      for (final ExpressionTree argument : created.getArguments()) {
        arguments.add(argument.toString());
      }
      constants.put(
          name,
          "// copied from "
              + owner.getSimpleName()
              + "."
              + field.getSimpleName()
              + "\nprivate static final OperationResult "
              + name
              + " =\n new OperationResult("
              + String.join(", ", arguments)
              + ");\n");
    }
    return name;
  }

  /** A constant EVM cannot see, as its value. */
  private String constantOf(final VariableElement field, final TreePath path) {
    final Object value = field.getConstantValue();
    if (value instanceof Integer || value instanceof Long) {
      return value + (value instanceof Long ? "L" : "");
    }
    throw new GeneratorException(
        where(path) + ": " + field + " is neither visible from EVM nor a number constant");
  }

  /** The name in EVM of a static helper EVM cannot see, copied into EVM once, as it is. */
  private String helper(final ExecutableElement method, final TreePath call) {
    final String known = helperNames.get(method);
    if (known != null) {
      return known;
    }
    final TypeElement owner = (TypeElement) method.getEnclosingElement();
    final String simple = owner.getSimpleName().toString();
    final String name =
        Character.toLowerCase(simple.charAt(0))
            + simple.substring(1)
            + Character.toUpperCase(method.getSimpleName().charAt(0))
            + method.getSimpleName().toString().substring(1);
    helperNames.put(method, name);
    final MethodTree tree = trees.getTree(method);
    final TreePath path = trees.getPath(method);
    if (tree == null || path == null || tree.getBody() == null) {
      throw new GeneratorException(where(call) + ": no source for " + method);
    }
    for (final VariableTree parameter : tree.getParameters()) {
      if (parameter.getType().toString().endsWith("MessageFrame")) {
        throw new GeneratorException(where(call) + ": the helper " + method + " takes the frame");
      }
    }
    final Copy copy = new Copy(0);
    final Scope scope = new Scope(null);
    final List<String> parameters = new ArrayList<>();
    for (final VariableTree parameter : tree.getParameters()) {
      final Element element = trees.getElement(new TreePath(path, parameter));
      scope.names.put(element, parameter.getName().toString());
      copy.declared.remove(parameter.getName().toString());
      parameters.add("final " + parameter.getType() + " " + parameter.getName());
    }
    final String body =
        new HelperCopy(copy, scope).body(new TreePath(path, tree.getBody()), tree.getBody());
    helpers.put(
        name,
        "// copied from "
            + simple
            + "."
            + method.getSimpleName()
            + "\nprivate static "
            + tree.getReturnType()
            + " "
            + name
            + "("
            + String.join(", ", parameters)
            + ") "
            + body
            + "\n");
    return name;
  }

  /** A helper's body, copied with only its references to what EVM cannot see changed. */
  private final class HelperCopy {
    private final Copy copy;
    private final Scope scope;

    HelperCopy(final Copy copy, final Scope scope) {
      this.copy = copy;
      this.scope = scope;
    }

    String body(final TreePath path, final BlockTree block) {
      for (final StatementTree statement : block.getStatements()) {
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitVariable(final VariableTree node, final Void unused) {
            scope.names.put(trees.getElement(getCurrentPath()), node.getName().toString());
            return super.visitVariable(node, unused);
          }

          @Override
          public Void scan(final Tree tree, final Void unused) {
            if (tree != null) {
              switch (tree.getKind()) {
                case FOR_LOOP,
                    ENHANCED_FOR_LOOP,
                    WHILE_LOOP,
                    DO_WHILE_LOOP,
                    TRY,
                    THROW,
                    SYNCHRONIZED,
                    LAMBDA_EXPRESSION ->
                    throw reject(new TreePath(getCurrentPath(), tree), "a " + tree.getKind());
                default -> {}
              }
            }
            return super.scan(tree, unused);
          }
        }.scan(new TreePath(path, statement), null);
      }
      return copy.frameExpression(path, scope);
    }
  }

  private boolean visibleFromEvm(final Element element) {
    if (!element.getModifiers().contains(Modifier.PUBLIC)) {
      return false;
    }
    for (Element e = element.getEnclosingElement();
        e instanceof TypeElement type;
        e = e.getEnclosingElement()) {
      if (!type.getModifiers().contains(Modifier.PUBLIC)) {
        return false;
      }
    }
    return true;
  }

  /** A class's name as EVM writes it: simple when EVM imports it, else qualified. */
  private String qualified(final TypeElement type) {
    final String name = type.getQualifiedName().toString();
    final String pkg = name.substring(0, name.lastIndexOf('.'));
    if (pkg.equals("java.lang")
        || pkg.equals("org.hyperledger.besu.evm")
        || evmSource.contains("import " + name + ";")) {
      return type.getSimpleName().toString();
    }
    final Element outer = type.getEnclosingElement();
    if (outer instanceof TypeElement outerType
        && evmSource.contains("import " + outerType.getQualifiedName() + ";")) {
      return outerType.getSimpleName() + "." + type.getSimpleName();
    }
    return name;
  }

  private String sourceOf(final TreePath path) {
    final CompilationUnitTree unit = path.getCompilationUnit();
    try {
      final String source = unit.getSourceFile().getCharContent(true).toString();
      return source.substring(
          (int) positions.getStartPosition(unit, path.getLeaf()),
          (int) positions.getEndPosition(unit, path.getLeaf()));
    } catch (final IOException e) {
      throw new GeneratorException("cannot read " + unit.getSourceFile().getName());
    }
  }

  private String where(final TreePath path) {
    final CompilationUnitTree unit = path.getCompilationUnit();
    final long line =
        unit.getLineMap().getLineNumber(positions.getStartPosition(unit, path.getLeaf()));
    return Path.of(unit.getSourceFile().getName()).getFileName() + ":" + line;
  }

  private GeneratorException reject(final TreePath path, final String what) {
    return new GeneratorException(where(path) + ": the loop cannot copy " + what);
  }

  static String upperSnake(final String camel) {
    return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(java.util.Locale.ROOT);
  }

  private static String replaceRegion(
      final String text,
      final String begin,
      final String end,
      final String replacement,
      final int indent) {
    final int from = text.indexOf(begin);
    final int to = text.indexOf(end);
    if (from < 0 || to < from) {
      throw new GeneratorException("EVM.java lacks the markers " + begin + " ... " + end);
    }
    final int lineStart = text.lastIndexOf('\n', from) + 1;
    final String pad = " ".repeat(indent);
    final StringBuilder indented = new StringBuilder();
    for (final String line : replacement.split("\n", -1)) {
      indented.append(line.isBlank() ? "" : pad + line.strip()).append('\n');
    }
    final int lineEnd = text.indexOf('\n', to);
    return text.substring(0, lineStart) + indented + text.substring(lineEnd + 1);
  }

  /** What stops the generator, with where in which source. */
  static final class GeneratorException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    GeneratorException(final String message) {
      super(message);
    }
  }
}
