library(methods)
library(svglite)
library(ggplot2)
theme_set(theme_classic())
library(dplyr)


#' Create a ggplot plot for a result on a single metadata doc
#'
#' @param x (data.frame) An MDQ results data frame
#'
#' @return (gg) The ggplot2 plot object
#' @export
#'
#' @examples
makeSingleDocPlot <- function(x) {
  results_summarized <- x %>%
    mutate(denom = length(pid)) %>%
    group_by(pid, type, status, level) %>%
    summarize(proportion = length(status) / unique(denom)[1])


  g <- ggplot(results_summarized, aes(level, proportion, fill = status)) +
    geom_bar(stat = "identity") +
    scale_fill_manual(drop = FALSE,
                      values = c("#d9edf7", "#f2dede", "#f5f5f5", "#fcf8e3")) +
    facet_wrap(~ type) +
    labs(x = "", y = "Proportion of All Checks Run") +
    theme(legend.title = element_blank(),
          panel.border = element_rect(colour = "black", fill = NA),
          legend.position = "top")

  g
}



#' Create a stacked bar plot for a result on multiple metadata documents
#'
#' @param x (data.frame) An MDQ results data frame
#'
#' @return (gg) The ggplot2 plot object
#' @export
#'
#' @examples
makeMultiDocPlotBar <- function(x) {
  results_summarized <- x %>%
    mutate(denom = length(pid)) %>%
    group_by(datasource, status, level) %>%
    summarize(proportion = length(status) / unique(denom)[1])

  g <- ggplot(results_summarized, aes(level, proportion, fill = status)) +
    geom_bar(stat = "identity") +
    scale_fill_manual(drop = FALSE,
                      values = c("#d9edf7", "#f2dede", "#f5f5f5", "#fcf8e3"))  +
    labs(x = "", y = "Proportion of All Checks Run") +
    theme(legend.title = element_blank(),
          panel.border = element_rect(colour = "black", fill = NA),
          legend.position = "top")

  # Facet by data source if we have more than one data source
  if ("datasource" %in% names(results_summarized) &&
      (length(unique(results_summarized$datasource)) > 1)) {
    g <- g + facet_wrap(~datasource)
  }

  g
}


#' Create a box plot for a result on multiple metadata documents
#'
#' @param x (data.frame) An MDQ results data frame
#'
#' @return (gg) The ggplot2 plot object
#' @export
#'
#' @examples
makeMultiDocPlotBox <- function(x) {
  # Calculate scores
  results_summarized <- x %>%
    filter(level != "INFO") %>%
    group_by(datasource, pid) %>%
    summarize(score = length(which(status == "SUCCESS")) /
                length(status))

  g <- ggplot(results_summarized, aes(datasource, score)) +
    geom_boxplot() +
    scale_y_continuous(limits = c(0, 1)) +
    labs(x = "", y = "Score") +
    theme(legend.title = element_blank(),
          panel.border = element_rect(colour = "black", fill = NA),
          legend.position = "top")

  g
}


#' Create a plot for a result on multiple metadata documents
#'
#' @param x (data.frame) An MDQ results data frame
#'
#' @return (gg) The ggplot2 plot object
#' @export
#'
#' @examples
makeMultiDocPlot <- function(x, type="box") {
  if (type == "box") {
    g <- makeMultiDocPlotBox(x)
  } else if (type == "bar") {
    g <- makeMultiDocPlotBar(x)
  } else {
    stop(paste0("Type of ", type, " is not supported. Must be either 'box' or 'bar'."))
  }

  g
}


#' Wrapper function that creates result plots for single and multiple document
#' results.
#'
#' Note: The plot is saved as a PNG at `path`.
#'
#' @param x (data.frame) An MDQ results data frame
#' @param path (character) The path to save the plot
#'
#' @return (character) The path where the plot was saved
#' @export
#'
#' @examples
makeMDQPlot <- function(x, path) {
  if (length(unique(x$pid)) == 1) {
    g <- makeSingleDocPlot(x)
  } else {
    g <- makeMultiDocPlot(x, type = "box")
  }

  ggsave(filename = path,
         plot = g,
         width = 4,
         height = 4)

  path
}


# Load + munge data
results = read.csv(inputPath, stringsAsFactors = FALSE)

results$type <- factor(results$type,
                       levels = c("metadata", "data", "congruence"),
                       ordered = TRUE)

results$level <- factor(results$level,
                    levels = c("INFO", "OPTIONAL", "REQUIRED"),
                        ordered = TRUE)

results$status <- factor(results$status,
                         levels = c("SUCCESS", "FAILURE", "SKIP", "ERROR"),
                         ordered = TRUE)


# Make the plot
outputPath <- makeMDQPlot(results, outputPath)

# Pass on result
mdq_result = list(status = "SUCCESS",
                  output = list(list(value = outputPath)))
