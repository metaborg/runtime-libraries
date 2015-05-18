# Analysis library

## Use (hackish)

Link the following (CWD is project root):

 * syntax/libanalysis/
 * trans/libanalysis/
 * include/LibAnalysis-parenthesize.str -> lib/include/.
 * src-gen/ds-signatures -> .

## Issues

 * We can add include directories for the Ant build, but not for
   analysis. If we could set/append include path for analysis (and
   transformations), this would be slightly cleaner already.

 * If we can make DynSem look in lib as well, put signatures in lib/lala
 * SDF3 imports are really on module names, not file names, so fuck.

 * Build cannot find Analysis.sdf in subdirectory of syntax.
 * Do dependencies from the project POM get included or not? Maybe use
   eather instead of matching artifacts with the POM model ourself?

 * Imports/include folders
   - SDF, Stratego : not analysed, parameter to Ant build
   - dynsem : custom file based import, use language-path folder
   - NaBl-based : assumes all relevant files are analysed, so
     language-path is an auxFileSet (or should we even generate for
     that?)
   - analysing everything and exposing the path are not exclusive, so
     we can always do both. Just need to handle the cases for
     SDF/Stratego specially and we should be good.

 * Languages create source folders for other languages
   - SDF3 create src-gen for Stratego and DS
   - It would probably be better if this is controlled from the build,
     instead of hard-coded. Then the build would specify the target
     folders (and maybe defaults when none are specified).
 * So, a transformation (e.g. compile, or a custom one) has a source
   and auxSource files
 * Artifacts add things to language paths
   - libraries add syntax, stratego, dynsem
