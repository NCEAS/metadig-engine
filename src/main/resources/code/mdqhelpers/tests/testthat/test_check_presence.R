context("check_presence")

test_that("throws an error if checking a non-list", {
  expect_error(check_presence(""))
})

test_that("status is set to FAILURE if any expectation is FAILURE", {
  # F
  check_presence(list())
  expect_true(mdq_result[["status"]] == "FAILURE")

  # FS
  rm(list = ls(all=TRUE))
  check_presence(list())
  check_presence(list(""))
  expect_true(mdq_result[["status"]] == "FAILURE")

  # SF
  rm(list = ls(all=TRUE))
  check_presence(list(""))
  check_presence(list())
  expect_true(mdq_result[["status"]] == "FAILURE")

  # FSF
  rm(list = ls(all=TRUE))
  check_presence(list())
  check_presence(list(""))
  check_presence(list())
  expect_true(mdq_result[["status"]] == "FAILURE")

  # SFS
  check_presence(list(""))
  check_presence(list())
  check_presence(list(""))
  expect_true(mdq_result[["status"]] == "FAILURE")
})