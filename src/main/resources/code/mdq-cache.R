#' mdq-cache.R
#'
#' Helper function for the MDQEngine that gives Checks a way to cache objects
#' in a well-defined way so that other Checks in the Suite don't re-acquire
#' them


library(digest)
library(httr)


#' Get the file path for an object specified by its URL. If the content at the
#' given URL has not been cached previously, the bytes are retrieved and cached.
#'
#' @param url (character) Any URL
#'
#' @return (character) The path to the file
#' @export
#'
#' @examples
get <- function(url) {
    temp_dir = Sys.getenv("MDQE_CACHE_DIR", "~/tmp")
    if (temp_dir == "") stop("MDQE_CACHE_DIR was not set.")
    if (!file.exists(temp_dir))
      stop(paste0("MDQE_CACHE_DIR was set to a path that does not exist: ",
                  temp_dir))

    cache_dir = file.path(temp_dir, "mdq-cache")
    if (!file.exists(cache_dir)) dir.create(cache_dir)
    stopifnot(file.exists(cache_dir))

    key = digest(url, algo = "md5", ascii = TRUE, serialize = FALSE)
    file_path = file.path(cache_dir, key)

    if (!file.exists(file_path)) {
      request <- GET(url)
      writeLines(content(request, as = "text"), con = file_path)
    }
    stopifnot(file.exists(file_path))

    file_path
}
