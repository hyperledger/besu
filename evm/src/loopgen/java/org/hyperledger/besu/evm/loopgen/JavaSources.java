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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

/**
 * EVM.java and the operation classes as javac reads them: their syntax trees, what each name in
 * them refers to, and their source text. The generator copies source text, and decides what to
 * change in it from what javac resolved each name to, never from how the name is spelled.
 */
final class JavaSources implements AutoCloseable {

  private static final String OPERATIONS_DIRECTORY = "org/hyperledger/besu/evm/v2/operation";

  /** The package of the operations, whose static methods a copy may copy in turn. */
  static final String OPERATIONS_PACKAGE = "org.hyperledger.besu.evm.v2.operation";

  /** EVM's package, whose classes EVM names without an import. */
  private static final String EVM_PACKAGE = "org.hyperledger.besu.evm";

  private final StandardJavaFileManager fileManager;
  private final Trees trees;
  private final CompilationUnitTree evmUnit;
  private final ClassTree evmTree;
  private final TypeElement evmClass;
  private final String evmText;

  private JavaSources(
      final StandardJavaFileManager fileManager,
      final Trees trees,
      final CompilationUnitTree evmUnit)
      throws IOException {
    this.fileManager = fileManager;
    this.trees = trees;
    this.evmUnit = evmUnit;
    this.evmTree =
        evmUnit.getTypeDecls().stream()
            .filter(ClassTree.class::isInstance)
            .map(ClassTree.class::cast)
            .findFirst()
            .orElseThrow();
    this.evmClass = (TypeElement) trees.getElement(TreePath.getPath(evmUnit, evmTree));
    this.evmText = evmUnit.getSourceFile().getCharContent(true).toString();
  }

  /**
   * Has javac read EVM.java and the operation classes, and resolve every name in them.
   *
   * @param sourceRoot the main source directory
   * @param evmFile EVM.java's path under it
   * @param classPath the class path the main sources compile against
   * @return the sources
   * @throws IOException if a source cannot be read
   */
  static JavaSources read(final Path sourceRoot, final String evmFile, final String classPath)
      throws IOException {
    final List<Path> files = new ArrayList<>();
    files.add(sourceRoot.resolve(evmFile));
    try (Stream<Path> operations = Files.list(sourceRoot.resolve(OPERATIONS_DIRECTORY))) {
      operations.filter(f -> f.toString().endsWith(".java")).sorted().forEach(files::add);
    }
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, UTF_8);
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
                    sourceRoot.toString(),
                    "-classpath",
                    classPath),
                null,
                fileManager.getJavaFileObjectsFromPaths(files));
    CompilationUnitTree evmUnit = null;
    for (final CompilationUnitTree unit : task.parse()) {
      if (unit.getSourceFile().getName().endsWith(evmFile)) {
        evmUnit = unit;
      }
    }
    task.analyze();

    final List<String> errors = new ArrayList<>();
    for (final Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
      // EVM.java may not compile until its generated regions are written, and javac resolves
      // executeOperationV2, which the copies come from, all the same
      final boolean inEvm =
          diagnostic.getSource() != null && diagnostic.getSource().getName().endsWith(evmFile);
      if (diagnostic.getKind() == Diagnostic.Kind.ERROR && !inEvm) {
        errors.add(diagnostic.toString());
      }
    }
    if (!errors.isEmpty()) {
      throw new GeneratorException("the sources do not compile:\n" + String.join("\n", errors));
    }
    if (evmUnit == null) {
      throw new GeneratorException(evmFile + " was not read");
    }
    return new JavaSources(fileManager, Trees.instance(task), evmUnit);
  }

  @Override
  public void close() throws IOException {
    fileManager.close();
  }

  // ---------------------------------------------------------------------------------------------
  // EVM.executeOperationV2, the general path

  /**
   * The cases of {@code executeOperationV2}'s switch, which say what each opcode runs, by every
   * opcode they list.
   */
  Map<Integer, TreePath> executeOperationV2Cases() {
    final Map<Integer, TreePath> cases = new HashMap<>();
    for (final Tree member : evmTree.getMembers()) {
      if (member instanceof MethodTree method
          && method.getName().contentEquals("executeOperationV2")) {
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitSwitchExpression(final SwitchExpressionTree node, final Void unused) {
            for (final CaseTree c : node.getCases()) {
              for (final int opcode : opcodesOf(c)) {
                cases.put(opcode, new TreePath(getCurrentPath(), c));
              }
            }
            return null;
          }
        }.scan(new TreePath(TreePath.getPath(evmUnit, evmTree), method), null);
      }
    }
    if (cases.isEmpty()) {
      throw new GeneratorException("EVM has no executeOperationV2 with a switch");
    }
    return cases;
  }

  /** The opcodes a case of {@code executeOperationV2}'s switch lists. */
  static List<Integer> opcodesOf(final CaseTree generalPathCase) {
    final List<Integer> opcodes = new ArrayList<>();
    for (final ExpressionTree label : generalPathCase.getExpressions()) {
      if (label instanceof LiteralTree literal && literal.getValue() instanceof Integer opcode) {
        opcodes.add(opcode);
      }
    }
    return opcodes;
  }

  /** Whether the element is the {@code executeOperationV2} parameter of that name. */
  boolean isExecuteOperationV2Parameter(final Element element, final String name) {
    return element.getKind() == ElementKind.PARAMETER
        && element.getSimpleName().contentEquals(name)
        && element.getEnclosingElement().getSimpleName().contentEquals("executeOperationV2")
        && element.getEnclosingElement().getEnclosingElement().equals(evmClass);
  }

  boolean isExecuteOperationV2Parameter(final Element element) {
    return element.getKind() == ElementKind.PARAMETER
        && element.getEnclosingElement().getEnclosingElement().equals(evmClass);
  }

  /** Whether the field is an instance field of EVM. */
  boolean isEvmInstanceField(final Element field) {
    return field.getEnclosingElement().equals(evmClass)
        && !field.getModifiers().contains(Modifier.STATIC);
  }

  /** Whether the code is EVM's own, whose names already mean in the copy what they meant. */
  boolean isInEvm(final TreePath code) {
    return code.getCompilationUnit() == evmUnit;
  }

  // ---------------------------------------------------------------------------------------------
  // Names

  /** What javac resolved the name, call or declaration at the path to, or null. */
  Element element(final TreePath path) {
    return trees.getElement(path);
  }

  /** The declaration of an element declared in these sources, or null. */
  Tree declaration(final Element element) {
    return trees.getTree(element);
  }

  /** The path to the declaration of an element declared in these sources, or null. */
  TreePath pathTo(final Element element) {
    return trees.getPath(element);
  }

  /** Whether EVM can name the element: it and every class around it are public. */
  static boolean visibleFromEvm(final Element element) {
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

  /** A class's name as EVM.java writes it: simple when EVM can, else qualified. */
  String nameInEvm(final TypeElement type) {
    final String name = type.getQualifiedName().toString();
    final String pkg = name.substring(0, name.lastIndexOf('.'));
    if (pkg.equals("java.lang") || pkg.equals(EVM_PACKAGE) || evmImports(name)) {
      return type.getSimpleName().toString();
    }
    if (type.getEnclosingElement() instanceof TypeElement outer
        && evmImports(outer.getQualifiedName().toString())) {
      return outer.getSimpleName() + "." + type.getSimpleName();
    }
    return name;
  }

  private boolean evmImports(final String qualifiedName) {
    return evmText.contains("import " + qualifiedName + ";");
  }

  // ---------------------------------------------------------------------------------------------
  // Source text

  /** The source text of the code at the path. */
  String text(final TreePath code) {
    return text(code.getCompilationUnit()).substring(start(code), end(code));
  }

  /** Where the code at the path starts in its source file. */
  int start(final TreePath code) {
    return (int)
        trees.getSourcePositions().getStartPosition(code.getCompilationUnit(), code.getLeaf());
  }

  /** Where the code at the path ends in its source file. */
  int end(final TreePath code) {
    return (int)
        trees.getSourcePositions().getEndPosition(code.getCompilationUnit(), code.getLeaf());
  }

  /** The file and line of the code at the path, for an error message. */
  String location(final TreePath code) {
    final CompilationUnitTree unit = code.getCompilationUnit();
    final long line = unit.getLineMap().getLineNumber(start(code));
    return Path.of(unit.getSourceFile().getName()).getFileName() + ":" + line;
  }

  private static String text(final CompilationUnitTree unit) {
    try {
      return unit.getSourceFile().getCharContent(true).toString();
    } catch (final IOException e) {
      throw new GeneratorException("cannot read " + unit.getSourceFile().getName());
    }
  }
}
