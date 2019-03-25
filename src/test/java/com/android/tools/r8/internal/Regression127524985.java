// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regression127524985 extends TestBase {

  private static final String MAIN = "com.android.tools.r8.internal.Regression127524985$Main";

  private static final Path JAR =
      Paths.get("third_party/internal/issue-127524985/issue-127524985.jar");

  private static final String EXPECTED = StringUtils.lines("true");

  @Parameters(name = "{0}")
  public static Backend[] data() {
    return Backend.values();
  }

  private final Backend backend;

  public Regression127524985(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Throwable {
    if (backend == Backend.CF) {
      testForJvm()
          .addClasspath(JAR)
          .run(MAIN)
          .assertSuccessWithOutput(EXPECTED);
    }
    (backend == Backend.DEX
            ? testForD8()
            : testForR8(backend)
                .debug()
                .noTreeShaking()
                .noMinification()
                .addKeepAllAttributes()
                .addKeepRules("-dontwarn *"))
        .addProgramFiles(JAR)
        .compile()
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }
}
