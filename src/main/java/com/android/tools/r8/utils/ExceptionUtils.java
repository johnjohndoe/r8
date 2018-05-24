// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class ExceptionUtils {

  public static final int STATUS_ERROR = 1;

  public static void withConsumeResourceHandler(
      Reporter reporter, StringConsumer consumer, String data) {
    withConsumeResourceHandler(reporter, handler -> consumer.accept(data, handler));
  }

  public static void withConsumeResourceHandler(
      Reporter reporter, Consumer<DiagnosticsHandler> consumer) {
    // Unchecked exceptions simply propagate out, aborting the compilation forcefully.
    consumer.accept(reporter);
    // Fail fast for now. We might consider delaying failure since consumer failure does not affect
    // the compilation. We might need to be careful to correctly identify errors so as to exit
    // compilation with an error code.
    reporter.failIfPendingErrors();
  }

  public interface CompileAction {
    void run() throws IOException, CompilationException, CompilationError, ResourceException;
  }

  public static void withD8CompilationHandler(Reporter reporter, CompileAction action)
      throws CompilationFailedException {
    withCompilationHandler(reporter, action, CompilationException::getMessageForD8);
  }

  public static void withR8CompilationHandler(Reporter reporter, CompileAction action)
      throws CompilationFailedException {
    withCompilationHandler(reporter, action, CompilationException::getMessageForR8);
  }

  public static void withCompilationHandler(
      Reporter reporter,
      CompileAction action,
      Function<CompilationException, String> compilerMessage)
      throws CompilationFailedException {
    try {
      try {
        action.run();
      } catch (IOException e) {
        throw reporter.fatalError(new ExceptionDiagnostic(e, extractIOExceptionOrigin(e)));
      } catch (CompilationException e) {
        throw reporter.fatalError(new StringDiagnostic(compilerMessage.apply(e)), e);
      } catch (CompilationError e) {
        throw reporter.fatalError(e);
      } catch (ResourceException e) {
        throw reporter.fatalError(new ExceptionDiagnostic(e, e.getOrigin()));
      }
      reporter.failIfPendingErrors();
    } catch (AbortException e) {
      throw new CompilationFailedException(e);
    }
  }

  public interface MainAction {
    void run() throws CompilationFailedException, IOException;
  }

  public static void withMainProgramHandler(MainAction action) {
    try {
      action.run();
    } catch (CompilationFailedException | AbortException e) {
      // Detail of the errors were already reported
      System.err.println("Compilation failed");
      System.exit(STATUS_ERROR);
    } catch (RuntimeException  | IOException e) {
      System.err.println("Compilation failed with an internal error.");
      Throwable cause = e.getCause() == null ? e : e.getCause();
      cause.printStackTrace();
      System.exit(STATUS_ERROR);
    }
  }

  // We should try to avoid the use of this extraction as it signifies a point where we don't have
  // enough context to associate a specific origin with an IOException. Concretely, we should move
  // towards always catching IOException and rethrowing CompilationError with proper origins.
  public static Origin extractIOExceptionOrigin(IOException e) {
    if (e instanceof FileSystemException) {
      FileSystemException fse = (FileSystemException) e;
      if (fse.getFile() != null && !fse.getFile().isEmpty()) {
        return new PathOrigin(Paths.get(fse.getFile()));
      }
    }
    return Origin.unknown();
  }

}
