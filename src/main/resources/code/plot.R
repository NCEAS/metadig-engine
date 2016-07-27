library(methods)
library(svglite)
library(ggplot2)
theme_set(theme_classic())
library(dplyr)

# Load + munge
results = read.csv(inputPath)
results$status <- factor(results$status,
                         levels = c("SUCCESS", "FAILURE", "SKIP", "ERROR"),
                         ordered = TRUE)
results_summarized <- results %>%
  mutate(denom = length(pid)) %>%
  group_by(datasource, status, level) %>%
  summarize(proportion = length(status) / unique(denom)[1])

# Create the plot
g <- ggplot(results_summarized, aes(level, proportion, fill = status)) +
  geom_bar(stat = "identity") +
  scale_fill_manual(drop = FALSE,
                    values = c("#d9edf7", "#f2dede", "#f5f5f5", "#fcf8e3"))  +
  labs(x = "", y = "Proportion of All Checks Run") +
  theme(legend.title = element_blank(),
        panel.border = element_rect(colour = "black", fill = NA))

if ("datasource" %in% names(results_summarized) && (length(unique(results_summarized$datasource)) > 1)) {
  g <- g + facet_wrap(~datasource)
}

# Save to disk
png(outputPath, width=400, height=400)
g
dev.off()

# Pass on result
result = list(value = outputPath, status="SUCCESS", message="See output path for plot." );
