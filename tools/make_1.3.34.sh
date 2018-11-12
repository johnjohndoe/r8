#!/bin/bash

set -x

CHERRY_BRANCH=cherry
git new-branch --upstream origin/d8-1.3 $CHERRY_BRANCH

# CHERRY-PICK NEW TEST BUILDING ABSTRACTIONS TO 1.3

# Add new test building abstractions.
git cherry-pick 21f10431b8815f31d271319f28793ea510510d65
git status --short | awk '{if ($1=="DU") print $2}' | xargs git rm
git -c core.editor=/bin/true cherry-pick --continue
git cl upload -f -m"Add new test building abstractions."

# Add methods for writing TestRunResult parts to a PrintStream
git cherry-pick 541807a717d15ac9932ca006c05a84d42b1cc38e
git cl upload -f -m"Add methods for writing TestRunResult parts to a PrintStream"

# src/test/java/com/android/tools/r8/TestRunResult.java from Optimize instance-of instructions
git show 491b4243d4328293cef7348a53ac47d881046c59 -- src/test/java/com/android/tools/r8/TestRunResult.java | git apply -
git commit -a -m"src/test/java/com/android/tools/r8/TestRunResult.java from Optimize instance-of instructions"
git cl upload -f -m"src/test/java/com/android/tools/r8/TestRunResult.java from Optimize instance-of instructions"

# Add options consumer support to test builders.
git cherry-pick 874273e65b473026aaa9f70d31d8995cb8d6c2d8
git cl upload -m"Add options consumer support to test builders."

# src/test/java/com/android/tools/r8/TestBuilder.java from Static, horizontal class merging
git show ccc9d7f1d51f4453812ac555b72175ff50032da2 -- src/test/java/com/android/tools/r8/TestBuilder.java | git apply -
git commit -a -m"src/test/java/com/android/tools/r8/TestBuilder.java from Static, horizontal class merging"
git cl upload -f -m"src/test/java/com/android/tools/r8/TestBuilder.java from Static, horizontal class merging"

# Compiler specific compilation result
git cherry-pick 232c84de4c2aa51638394812ec5212300d7514fb
git cl upload -m"Compiler specific compilation result"

# Convenience utilities to use test builders in debug tests.
git cherry-pick 5a68d7da0598c760eb3dcf521953acf458743903
git status --short | awk '{if ($1=="DU") print $2}' | xargs git rm
git -c core.editor=/bin/true cherry-pick --continue
git cl upload -f -m"Convenience utilities to use test builders in debug tests."

# src/test/java/com/android/tools/r8/TestCompileResult.java from Make StringLengthTest use InstructionSubject.
git show ed715f7b3b8f76ca6afb0c3cde7f1146577760e7 -- src/test/java/com/android/tools/r8/TestCompileResult.java | git apply -
git commit -a -m"src/test/java/com/android/tools/r8/TestCompileResult.java from Make StringLengthTest use InstructionSubject."
git cl upload -f -m"src/test/java/com/android/tools/r8/TestCompileResult.java from Make StringLengthTest use InstructionSubject."

# src/test/java/com/android/tools/r8/TestCompileResult.java and src/test/java/com/android/tools/r8/R8TestCompileResult.java from Add initial support for removing unused arguments
git show a2bd26f9cb19b2c9d127e62aa99a275069c4eb60 -- src/test/java/com/android/tools/r8/TestCompileResult.java src/test/java/com/android/tools/r8/R8TestCompileResult.java | git apply -
git commit -a -m"src/test/java/com/android/tools/r8/TestCompileResult.java and src/test/java/com/android/tools/r8/R8TestCompileResult.java from Add initial support for removing unused arguments"
git cl upload -f -m"src/test/java/com/android/tools/r8/TestCompileResult.java and src/test/java/com/android/tools/r8/R8TestCompileResult.java from Add initial support for removing unused arguments"

# Make TestRunResult.inspector() use the mapping file from the compilation
git cherry-pick 3a1a7807a3715d4598c3843cabe520c90abde710
git status --short | awk '{if ($1=="DU") print $2}' | xargs git rm
git -c core.editor=/bin/true cherry-pick --continue
git cl upload -f -m"Make TestRunResult.inspector() use the mapping file from the compilation"

# Add testing with Proguard as compiler
git cherry-pick e708d2f93e71748e1a3220cfd7e8c4f5d7b72895
git cl upload -f -m"Add testing with Proguard as compiler"

# Check existence of mapping file from creating Proguard test result
git cherry-pick 6e7960d123ea9812360d39dea4da858c98320826
git cl upload -f -m"Check existence of mapping file from creating Proguard test result"

# Add test support for R8 compat mode
git cherry-pick 8c42c923bbf5931d5120741647ab67abd09b0717
git cl upload -f -m"Add test support for R8 compat mode"

# Add TestBase.testForDX()
git cherry-pick d21f384d602db3d345a6d41ed0fec465bd9b70b6
git cl upload -f -m"Add TestBase.testForDX()"

# src/test/java/com/android/tools/r8/utils/codeinspector/ClassSubject.java from Optimize main method of synthesized lambda classes
git show 5d18c46a7a281c5c51420c4793680148f7651046 -- src/test/java/com/android/tools/r8/utils/codeinspector/ClassSubject.java | git apply -
git commit -a -m"src/test/java/com/android/tools/r8/utils/codeinspector/ClassSubject.java from Optimize main method of synthesized lambda classes"
git cl upload -m"src/test/java/com/android/tools/r8/utils/codeinspector/ClassSubject.java from Optimize main method of synthesized lambda classes"

# src/test/java/com/android/tools/r8/utils/codeinspector/AbsentClassSubject.java src/test/java/com/android/tools/r8/utils/codeinspector/ClassSubject.java src/test/java/com/android/tools/r8/utils/codeinspector/FoundClassSubject.java from Lookup single invoke target in uninstantiated type opt.
git show 755356c916a67f314534de2dc649af8fc704e1b6 -- src/test/java/com/android/tools/r8/utils/codeinspector/AbsentClassSubject.java src/test/java/com/android/tools/r8/utils/codeinspector/ClassSubject.java src/test/java/com/android/tools/r8/utils/codeinspector/FoundClassSubject.java | git apply -
git commit -a -m"src/test/java/com/android/tools/r8/utils/codeinspector/AbsentClassSubject.java src/test/java/com/android/tools/r8/utils/codeinspector/ClassSubject.java src/test/java/com/android/tools/r8/utils/codeinspector/FoundClassSubject.java from Lookup single invoke target in uninstantiated type opt."
git cl upload -m"src/test/java/com/android/tools/r8/utils/codeinspector/AbsentClassSubject.java src/test/java/com/android/tools/r8/utils/codeinspector/ClassSubject.java src/test/java/com/android/tools/r8/utils/codeinspector/FoundClassSubject.java from Lookup single invoke target in uninstantiated type opt."

# src/test/java/com/android/tools/r8/TestRunResult.java and src/test/java/com/android/tools/r8/TestShrinkerBuilder.java from Verify correctness of types
git show 98a41c7e4794a6913cd81029b6d812d9e16f6c42 -- src/test/java/com/android/tools/r8/TestRunResult.java src/test/java/com/android/tools/r8/TestShrinkerBuilder.java | git apply -
git commit -a -m"src/test/java/com/android/tools/r8/TestRunResult.java and src/test/java/com/android/tools/r8/TestShrinkerBuilder.java from Verify correctness of types"
git cl upload -m"src/test/java/com/android/tools/r8/TestRunResult.java and src/test/java/com/android/tools/r8/TestShrinkerBuilder.java from Verify correctness of types"

# Add @NeverMerge annotation for testing
git cherry-pick 06c856975e893ec8bc0406ef97e0f9949a503a7d
git status --short | awk '{if ($1=="DU") print $2}' | xargs git rm
git -c core.editor=/bin/true cherry-pick --continue
git cl upload -m"Add @NeverMerge annotation for testing"

# AND NOW FOR THE REAL STUFF
# Use same matching semantics for fields and methods rules
git cherry-pick 76ece4f0350d689b0b1a4ada01fd21871c8539e0
git cl upload -m"Use same matching semantics for fields and methods rules"

# Fix broken tests
git cherry-pick 586f3500d7ca8b57d8b6e0d31e10a0c242606b84
git cl upload -m"Fix broken tests"

# Change the semantics for keeping methods
git cherry-pick de3dab0050043c50c5c6879fc8517fda071c31ce
git cl upload -m"Change the semantics for keeping methods"
