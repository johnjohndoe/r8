// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.TestRuntime.NoneRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestParametersBuilder {

  // Static computation of VMs configured as available by the testing invocation.
  private static final List<TestRuntime> availableRuntimes =
      getAvailableRuntimes().collect(Collectors.toList());

  // Predicate describing which test parameters are applicable to the test.
  // Built via the methods found below. Defaults to no applicable parameters, i.e., the emtpy set.
  private Predicate<TestParameters> filter = param -> false;

  private TestParametersBuilder() {}

  public static TestParametersBuilder builder() {
    return new TestParametersBuilder();
  }

  private TestParametersBuilder withFilter(Predicate<TestParameters> predicate) {
    filter = filter.or(predicate);
    return this;
  }

  private TestParametersBuilder withCfRuntimeFilter(Predicate<CfVm> predicate) {
    return withFilter(p -> p.isCfRuntime() && predicate.test(p.getRuntime().asCf().getVm()));
  }

  private TestParametersBuilder withDexRuntimeFilter(Predicate<DexVm.Version> predicate) {
    return withFilter(
        p -> p.isDexRuntime() && predicate.test(p.getRuntime().asDex().getVm().getVersion()));
  }

  public TestParametersBuilder withNoneRuntime() {
    return withFilter(p -> p.getRuntime() == NoneRuntime.getInstance());
  }

  public TestParametersBuilder withAllRuntimes() {
    return withCfRuntimes().withDexRuntimes();
  }

  /** Add specific runtime if available. */
  public TestParametersBuilder withCfRuntime(CfVm runtime) {
    return withCfRuntimeFilter(vm -> vm == runtime);
  }

  /** Add all available CF runtimes. */
  public TestParametersBuilder withCfRuntimes() {
    return withCfRuntimeFilter(vm -> true);
  }

  /** Add all available CF runtimes between {@param startInclusive} and {@param endInclusive}. */
  public TestParametersBuilder withCfRuntimes(CfVm startInclusive, CfVm endInclusive) {
    return withCfRuntimeFilter(
        vm -> startInclusive.lessThanOrEqual(vm) && vm.lessThanOrEqual(endInclusive));
  }

  /** Add all available CF runtimes starting from and including {@param startInclusive}. */
  public TestParametersBuilder withCfRuntimesStartingFromIncluding(CfVm startInclusive) {
    return withCfRuntimeFilter(vm -> startInclusive.lessThanOrEqual(vm));
  }

  /** Add all available CF runtimes starting from and excluding {@param startExcluding}. */
  public TestParametersBuilder withCfRuntimesStartingFromExcluding(CfVm startExcluding) {
    return withCfRuntimeFilter(vm -> startExcluding.lessThan(vm));
  }

  /** Add all available CF runtimes ending at and including {@param endInclusive}. */
  public TestParametersBuilder withCfRuntimesEndingAtIncluding(CfVm endInclusive) {
    return withCfRuntimeFilter(vm -> vm.lessThanOrEqual(endInclusive));
  }

  /** Add all available CF runtimes ending at and excluding {@param endExclusive}. */
  public TestParametersBuilder withCfRuntimesEndingAtExcluding(CfVm endExclusive) {
    return withCfRuntimeFilter(vm -> vm.lessThan(endExclusive));
  }

  /** Add all available DEX runtimes. */
  public TestParametersBuilder withDexRuntimes() {
    return withDexRuntimeFilter(vm -> true);
  }

  /** Add specific runtime if available. */
  public TestParametersBuilder withDexRuntime(DexVm.Version runtime) {
    return withDexRuntimeFilter(vm -> vm == runtime);
  }

  /** Add all available CF runtimes between {@param startInclusive} and {@param endInclusive}. */
  public TestParametersBuilder withDexRuntimes(
      DexVm.Version startInclusive, DexVm.Version endInclusive) {
    return withDexRuntimeFilter(
        vm -> startInclusive.isOlderThanOrEqual(vm) && vm.isOlderThanOrEqual(endInclusive));
  }

  /** Add all available DEX runtimes that support native multidex. */
  public TestParametersBuilder withNativeMultidexDexRuntimes() {
    return withDexRuntimesStartingFromIncluding(DexVm.Version.V5_1_1);
  }

  /** Add all available DEX runtimes starting from and including {@param startInclusive}. */
  public TestParametersBuilder withDexRuntimesStartingFromIncluding(DexVm.Version startInclusive) {
    return withDexRuntimeFilter(vm -> startInclusive.isOlderThanOrEqual(vm));
  }

  /** Add all available DEX runtimes starting from and excluding {@param startExcluding}. */
  public TestParametersBuilder withDexRuntimesStartingFromExcluding(DexVm.Version startExcluding) {
    return withDexRuntimeFilter(
        vm -> vm != startExcluding && startExcluding.isOlderThanOrEqual(vm));
  }

  /** Add all available DEX runtimes ending at and including {@param endInclusive}. */
  public TestParametersBuilder withDexRuntimesEndingAtIncluding(DexVm.Version endInclusive) {
    return withDexRuntimeFilter(vm -> vm.isOlderThanOrEqual(endInclusive));
  }

  /** Add all available DEX runtimes ending at and excluding {@param endExclusive}. */
  public TestParametersBuilder withDexRuntimesEndingAtExcluding(DexVm.Version endExclusive) {
    return withDexRuntimeFilter(vm -> vm != endExclusive && vm.isOlderThanOrEqual(endExclusive));
  }

  /**
   * API level configuration.
   *
   * <p>Currently enabling API level config will by default configure each DEX VM to be configured
   * with two parameters, one running at the highest api-level supported by the VM and one at the
   * lowest supported by the compiler (i.e., B).
   */
  private static final AndroidApiLevel lowestCompilerApiLevel = AndroidApiLevel.B;

  private boolean enableApiLevels = false;

  private Predicate<AndroidApiLevel> apiLevelFilter = param -> false;

  private TestParametersBuilder withApiFilter(Predicate<AndroidApiLevel> filter) {
    enableApiLevels = true;
    apiLevelFilter = apiLevelFilter.or(filter);
    return this;
  }

  public TestParametersBuilder withAllApiLevels() {
    return withApiFilter(api -> true);
  }

  public TestParametersBuilder withApiLevelsStartingAtIncluding(AndroidApiLevel startInclusive) {
    return withApiFilter(api -> startInclusive.getLevel() <= api.getLevel());
  }

  public TestParametersBuilder withApiLevelsStartingAtExcluding(AndroidApiLevel startExclusive) {
    return withApiFilter(api -> startExclusive.getLevel() < api.getLevel());
  }

  public TestParametersBuilder withApiLevelsEndingAtInclusive(AndroidApiLevel endInclusive) {
    return withApiFilter(api -> api.getLevel() <= endInclusive.getLevel());
  }

  public TestParametersBuilder withApiLevelsEndingAtExcluding(AndroidApiLevel endExclusive) {
    return withApiFilter(api -> api.getLevel() < endExclusive.getLevel());
  }

  public TestParametersCollection build() {
    return new TestParametersCollection(
        getAvailableRuntimes()
            .flatMap(this::createParameters)
            .filter(filter)
            .collect(Collectors.toList()));
  }

  public Stream<TestParameters> createParameters(TestRuntime runtime) {
    if (!enableApiLevels || !runtime.isDex()) {
      return Stream.of(new TestParameters(runtime));
    }
    List<AndroidApiLevel> sortedApiLevels =
        Arrays.stream(AndroidApiLevel.values()).filter(apiLevelFilter).collect(Collectors.toList());
    if (sortedApiLevels.isEmpty()) {
      return Stream.of();
    }
    AndroidApiLevel vmLevel = runtime.asDex().getMinApiLevel();
    AndroidApiLevel lowestApplicable = sortedApiLevels.get(sortedApiLevels.size() - 1);
    if (vmLevel.getLevel() < lowestApplicable.getLevel()) {
      return Stream.of();
    }
    if (sortedApiLevels.size() > 1) {
      for (int i = 0; i < sortedApiLevels.size(); i++) {
        AndroidApiLevel highestApplicable = sortedApiLevels.get(i);
        if (highestApplicable.getLevel() <= vmLevel.getLevel()
            && lowestApplicable != highestApplicable) {
          return Stream.of(
              new TestParameters(runtime, lowestApplicable),
              new TestParameters(runtime, highestApplicable));
        }
      }
    }
    return Stream.of(new TestParameters(runtime, lowestApplicable));
  }

  // Public method to check that the CF runtime coincides with the system runtime.
  public static boolean isSystemJdk(CfVm vm) {
    String version = System.getProperty("java.version");
    switch (vm) {
      case JDK8:
        return version.startsWith("1.8.");
      case JDK9:
        return version.startsWith("9.");
      case JDK11:
        return version.startsWith("11.");
    }
    throw new Unreachable();
  }

  private static boolean isSupportedJdk(CfVm vm) {
    return isSystemJdk(vm) || TestRuntime.isCheckedInJDK(vm);
  }

  private static Stream<TestRuntime> getAvailableRuntimes() {
    String runtimesProperty = System.getProperty("runtimes");
    Stream<TestRuntime> runtimes;
    if (runtimesProperty != null) {
      runtimes =
          Arrays.stream(runtimesProperty.split(":"))
              .filter(s -> !s.isEmpty())
              .map(
                  name -> {
                    TestRuntime runtime = TestRuntime.fromName(name);
                    if (runtime != null) {
                      return runtime;
                    }
                    throw new RuntimeException("Unexpected runtime property name: " + name);
                  });
    } else {
      runtimes =
          Stream.concat(
              Stream.of(NoneRuntime.getInstance()),
              Stream.concat(
                  Arrays.stream(TestRuntime.CfVm.values()).map(CfRuntime::new),
                  Arrays.stream(DexVm.Version.values()).map(DexRuntime::new)));
    }
    // TODO(b/127785410) Support multiple VMs at the same time.
    return runtimes.filter(runtime -> !runtime.isCf() || isSupportedJdk(runtime.asCf().getVm()));
  }

  public static List<CfVm> getAvailableCfVms() {
    return getAvailableRuntimes()
        .filter(TestRuntime::isCf)
        .map(runtime -> runtime.asCf().getVm())
        .collect(Collectors.toList());
  }

  public static List<DexVm> getAvailableDexVms() {
    return getAvailableRuntimes()
        .filter(TestRuntime::isDex)
        .map(runtime -> runtime.asDex().getVm())
        .collect(Collectors.toList());
  }
}
