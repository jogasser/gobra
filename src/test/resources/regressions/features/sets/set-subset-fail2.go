package pkg

func foo(ghost s set[int], ghost t seq[int]) {
  // fails: `t` is a sequence rather than a set
  //:: ExpectedOutput(type_error)
  ghost u := s subset t
}
