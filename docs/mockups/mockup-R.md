# R API Mock Up

Goal: Write a recommendation suite using native R code.
Implementation: An R package that facilities writing a recommendation
(set of checks)

## View: A Recommendation (written in R)

```{R}
# filename: lter-test-suite.R

suite("lter-test-suite")

check_that("the title has five tokens", {
  requires("metadata") # Specifically says what it requires so we only load
                       # what's needed for this check
  description("Titles should have exactly five 'tokens', which essentially
               just means words.")

  #' Two objects are exposed inside this expression:
  #'
  #'  - metadata
  #'  - data
  #'
  #  `metadata` has slots which correspond to a controlled
  #  set of 'concepts' (title, originator, etc) that are
  #  implemented independent of the metadata dialect. For
  #  dialects that do not support a concept, the slot value
  #  will be NA.

  library(stringr) # The check can load a package!
  tokens <- str_split(metadata@title, " ")

  expect_true(length(tokens) == 5)
})

check_that("the data in the first column is normally distributed", {
  requires("data")
  description("The first column should have data for which we lack sufficient
               evidence that the data were not drawn from a normal distribution.
               Specifically, this runs the Shaprio-Wilk test for normality
               against the values present in the first column of the dataset
               and checks whether the p-value of the test is greater than or
               equal to an alpha of 0.05.")

  # Note that, in this test, the data are available inside this expression by
  # the name 'data' which is a data.frame. Tests can use standard data.frame
  # syntax to reference the rows, columns, and individual values.
  test <- shapiro.test(data[,1])

  expect_true(test$p.value >= 0.05)
})

```

## View: Run a Recommendation against metadata and/or data

```{R}
run_recommendation("lter-test-suite", metadata = "../path/to/metadata.xml")

# => .S
# => One check skipped:
# => Check: the data in the first column is normally distributed
# => Reason: Check requires data and data were not explicitly passed in
# =>
# => [1] "1/2" # <-- The score


run_recommendation("lter-test-suite",
                   metadata = "../path/to/metadata.xml",
                   data = "../path/to/data.csv")
# => ..
# => All checks passed.
# =>
# => [1] "2/2" # <-- The score
```
