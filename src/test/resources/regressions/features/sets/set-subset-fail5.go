package pkg

func foo(ghost s set[int], ghost t set[int]) {
  //:: ExpectedOutput(assert_error:assertion_error)
  assert set[int] { 1, 2, 3 } subset set[int] { 1, 3, 4 }
}
