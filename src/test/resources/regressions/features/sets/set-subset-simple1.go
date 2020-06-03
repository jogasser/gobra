package pkg

func example1(ghost s set[int], ghost t set[int]) {
  ghost u := s subset t
}

func example2(ghost s set[int], ghost t set[int]) (ghost b bool) {
  b = s subset t
}

func example3() {
  assert set[int] { 1, 2 } subset set[int] { 3, 2, 1 }
}

func example4(ghost s set[int]) {
  assert set[int] { } subset s
  assert s subset s
}

func example5(ghost s set[int], ghost t set[int]) {
  assert s subset t && t subset s ==> s == t
  assert s intersection t subset s
  assert s subset s union t
}

func example6(ghost s set[int], ghost t set[int], ghost u set[int]) {
  assert s subset t && t subset u ==> s subset u
  assert s subset t && s subset u ==> s subset (t intersection u)
}

ensures u subset s
func example7(ghost s set[int], ghost t set[int]) (ghost u set[int]) {
  u = s intersection t
}

ghost func example8(s set[int], t set[int]) {
  if (s subset t) { }
}
