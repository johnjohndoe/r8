// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.TestBase.Backend.DEX;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.CfDebugTestConfig;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.debug.DexDebugTestConfig;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class TestCompileResult<
        CR extends TestCompileResult<CR, RR>, RR extends TestRunResult>
    extends TestBaseResult<CR, RR> {

  public final AndroidApp app;
  final List<Path> additionalRunClassPath = new ArrayList<>();

  TestCompileResult(TestState state, AndroidApp app) {
    super(state);
    this.app = app;
  }

  public abstract Backend getBackend();

  protected abstract RR createRunResult(ProcessResult result);

  public RR run(Class<?> mainClass) throws IOException {
    return run(mainClass.getTypeName());
  }

  public RR run(String mainClass) throws IOException {
    switch (getBackend()) {
      case DEX:
        return runArt(additionalRunClassPath, mainClass);
      case CF:
        return runJava(additionalRunClassPath, mainClass);
      default:
        throw new Unreachable();
    }
  }

  public CR addRunClasspath(List<Path> classpath) {
    additionalRunClassPath.addAll(classpath);
    return self();
  }

  public CR writeToZip(Path file) throws IOException {
    app.writeToZip(file, getBackend() == DEX ? OutputMode.DexIndexed : OutputMode.ClassFile);
    return self();
  }

  public CodeInspector inspector() throws IOException, ExecutionException {
    return new CodeInspector(app);
  }

  public CR inspect(Consumer<CodeInspector> consumer) throws IOException, ExecutionException {
    consumer.accept(inspector());
    return self();
  }

  public DebugTestConfig debugConfig() {
    // Rethrow exceptions since debug config is usually used in a delayed wrapper which
    // does not declare exceptions.
    try {
      Path out = state.getNewTempFolder().resolve("out.zip");
      switch (getBackend()) {
        case CF:
          {
            app.writeToZip(out, OutputMode.ClassFile);
            return new CfDebugTestConfig().addPaths(out);
          }
        case DEX:
          {
            app.writeToZip(out, OutputMode.DexIndexed);
            return new DexDebugTestConfig().addPaths(out);
          }
        default:
          throw new Unreachable();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private RR runJava(List<Path> additionalClassPath, String mainClass) throws IOException {
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZip(out, OutputMode.ClassFile);
    List<Path> classPath = ImmutableList.<Path>builder()
        .addAll(additionalClassPath)
        .add(out)
        .build();
    ProcessResult result = ToolHelper.runJava(classPath, mainClass);
    return createRunResult(result);
  }

  private RR runArt(List<Path> additionalClassPath, String mainClass) throws IOException {
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZip(out, OutputMode.DexIndexed);
    List<String> classPath = ImmutableList.<String>builder()
        .addAll(additionalClassPath.stream().map(Path::toString).collect(Collectors.toList()))
        .add(out.toString())
        .build();
    ProcessResult result = ToolHelper.runArtRaw(classPath, mainClass, dummy -> {});
    return createRunResult(result);
  }
}
