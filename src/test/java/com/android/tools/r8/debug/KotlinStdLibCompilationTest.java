// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinStdLibCompilationTest extends TestBase {

  private static final Path KOTLIN_STDLIB =
      Paths.get(
          ToolHelper.THIRD_PARTY_DIR,
          "kotlin-compiler-1.3.41",
          "kotlinc",
          "lib",
          "kotlin-stdlib.jar");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection setup() {
    return TestParametersBuilder.builder().withAllRuntimes().withAllApiLevels().build();
  }

  public KotlinStdLibCompilationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws CompilationFailedException {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramFiles(KOTLIN_STDLIB)
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }

  @Test
  public void testR8() throws CompilationFailedException {
    testForR8(parameters.getBackend())
        .addProgramFiles(KOTLIN_STDLIB)
        .noMinification()
        .noTreeShaking()
        .addKeepAllAttributes()
        .setMode(CompilationMode.DEBUG)
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }
}
