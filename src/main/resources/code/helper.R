#' Check for the presence of an element. Assumes a single element.
#'
#' Note: Modifies global environment!
#'
#' @param x (any) Object to check.
#'
#' @return
#' @export
#'
#' @examples
check_presence <- function(x) {
  name <- paste(substitute(x), collapse = "")
  cat(paste0("Check presence of ", name, "\n"))
    if (!is(x, "list")) {
    status <- "FAILURE"
    message <- paste0("Object '", name, "' was not a list, which is the expected type.")
  } else if (length(x) == 0) {
    status <- "FAILURE"
    message <- paste0("Object '", name, "' was of length zero when it was expected to be of length one.")
  } else if (length(x) > 1) {
    status <- "FAILURE"
    message <- paste0("Object '", name, "' was of length ", length(x), " when it was expected to be of length one.")
  } else {
    status <- "SUCCESS"
    message <- paste0("Object '", name, "' was present and was of length one.")
  }

  if (any(grepl("mdq_result", ls(envir = .GlobalEnv)))) {
    local_result <- get("mdq_result", envir = .GlobalEnv)

    # Toggle status to FAILURE if its currently SUCCESS and status == "FAILURE"
    if ("status" %in% names(mdq_result) &&
        local_result[["status"]] != "FAILURE" &&
        status == "FAILURE") {
      cat("Converting status to FAILURE\n")
      local_result[["status"]] <- "FAILURE"
      cat(paste0("status is now ", local_result[["status"]], "\n"))
    }

    # Append output to any existing outputs
    if ("output" %in% names(local_result)) {
      cat("Appending an output\n")
      local_result[["output"]][[length(local_result[["output"]]) + 1]] <- list(value = message)
    } else {
      cat("Replacing output\n")
      local_result[["output"]] <- list(list(value = message))
    }
  } else {
    local_result <<- list(status = status,
                          output = list(list(value = message)))
  }

  assign("mdq_result", local_result, envir = .GlobalEnv)
}
