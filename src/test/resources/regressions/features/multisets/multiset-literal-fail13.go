package pkg

func foo() {
  ghost m := mset[seq[int]] { { 1 : 10, 0 : 20 } }
  //:: ExpectedOutput(assert_error:assertion_error)
  assert m == mset[seq[int]] { { 10, 20 } }
}
