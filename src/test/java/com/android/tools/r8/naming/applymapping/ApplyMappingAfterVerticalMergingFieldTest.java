// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ApplyMappingAfterVerticalMergingFieldTest extends TestBase {

  // Base class will be vertical class merged into subclass
  public static class LibraryBase {

    public boolean foo = System.nanoTime() > 0;
  }

  // Subclass targeted via vertical class merging. The main method ensures a reference to foo.
  public static class LibrarySubclass extends LibraryBase {

    public static void main(String[] args) {
      System.out.println(new LibrarySubclass().foo);
    }
  }

  // Program class that uses LibrarySubclass but the library does not explicitly keep foo and
  // should thus fail at runtime.
  public static class ProgramClass extends LibrarySubclass {

    public static void main(String[] args) {
      System.out.println(new ProgramClass().foo);
    }
  }

  // Test runner code follows.

  private static final Class<?>[] LIBRARY_CLASSES = {
    NeverMerge.class, LibraryBase.class, LibrarySubclass.class
  };

  private static final Class<?>[] PROGRAM_CLASSES = {
      ProgramClass.class
  };

  private Backend backend;

  @Parameterized.Parameters(name = "{0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public ApplyMappingAfterVerticalMergingFieldTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void runOnJvm() throws Throwable {
    Assume.assumeTrue(backend == Backend.CF);
    testForJvm()
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(ProgramClass.class)
        .assertSuccessWithOutput(StringUtils.lines("true"));
  }

  @Test
  public void b121042934() throws Exception {
    R8TestCompileResult libraryResult = testForR8(backend)
        .enableInliningAnnotations()
        .addProgramClasses(LIBRARY_CLASSES)
        .addKeepMainRule(LibrarySubclass.class)
        .addKeepClassAndDefaultConstructor(LibrarySubclass.class)
        .compile();

    CodeInspector inspector = libraryResult.inspector();
    assertThat(inspector.clazz(LibraryBase.class), not(isPresent()));
    assertThat(inspector.clazz(LibrarySubclass.class), isPresent());

    Path libraryOut = temp.newFolder().toPath().resolve("out.jar");
    libraryResult.writeToZip(libraryOut);
    testForR8(backend)
        .noTreeShaking()
        .noMinification()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(libraryResult.getProguardMap())
        .addLibraryClasses(LIBRARY_CLASSES)
        .compile()
        .addRunClasspath(Collections.singletonList(libraryOut))
        .run(ProgramClass.class)
        .assertFailureWithErrorThatMatches(containsString("NoSuchFieldError"));
  }
}
