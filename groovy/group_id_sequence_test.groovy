// Tests for the pure-groovy GroupIdSequence in groups.groovy -- the file-backed, per-experiment
// group-id counter that keeps group ids unique across reloads and instances. The harness binds an
// in-memory variant (an ExperimentContext with a null dataDir), so these construct file-backed
// sequences directly to cover the persistence the normal DSL flow doesn't exercise.
//
// Run via: sbt -java-home "$JAVA8_HOME" "testOnly GroovyScriptTests"
//
// Groovy 1.8.9 target: no closure->functional-interface coercion, no lambdas / `::` refs.

import java.io.File

// A throwaway temp file path for one sequence (its parent dir is created by GroupIdSequence).
def tempSeqFile = {
  def dir = File.createTempFile("gidseq", "")
  dir.delete()
  dir.mkdirs()
  return new File(dir, "group-id.seq")
}

test("an in-memory sequence counts from 1 and does not persist") {
  def seq = new GroupIdSequence(null)
  assert seq.current() == 0
  assert seq.next() == 1
  assert seq.next() == 2
  assert seq.current() == 2
}

test("a file-backed sequence persists each id to its file") {
  def f = tempSeqFile()
  def seq = new GroupIdSequence(f)
  assert seq.next() == 1
  assert f.getText("UTF-8").trim() == "1"
  assert seq.next() == 2
  assert f.getText("UTF-8").trim() == "2"
}

test("a fresh sequence on an existing file resumes past the last id (reload / new instance)") {
  def f = tempSeqFile()
  def first = new GroupIdSequence(f)
  first.next()
  first.next()                          // file now holds "2"
  def second = new GroupIdSequence(f)   // a different engine/instance pointing at the same file
  assert second.current() == 2
  assert second.next() == 3
}

test("a missing sequence file starts at zero") {
  def f = tempSeqFile()
  assert !f.exists()
  def seq = new GroupIdSequence(f)
  assert seq.current() == 0
  assert seq.next() == 1
}
