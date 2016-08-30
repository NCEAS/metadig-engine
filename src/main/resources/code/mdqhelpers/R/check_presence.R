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
  if (!is(x, "list")) stop(paste0("The passed in object '", name, "' is not a list and it must be."))

 if (length(x) == 0) {
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

    if (!is(local_result, "list")) stop("Name 'mdq_result' copied from global environment was not a list and must be.")

    # Toggle status to FAILURE if its currently SUCCESS and status == "FAILURE"
    if ("status" %in% names(mdq_result) &&
        local_result[["status"]] != "FAILURE" &&
        status == "FAILURE") {
      local_result[["status"]] <- "FAILURE"
    }

    # Append output to any existing outputs
    if ("output" %in% names(local_result)) {
      local_result[["output"]][[length(local_result[["output"]]) + 1]] <- list(value = message)
    } else {
      local_result[["output"]] <- list(list(value = message))
    }
  } else {
    local_result <<- list(status = status,
                          output = list(list(value = message)))
  }

  assign("mdq_result", local_result, envir = .GlobalEnv)
}
