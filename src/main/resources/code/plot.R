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
  mutate(nchecks = length(pid)) %>%
  group_by(status, level) %>%
  summarize(proportion = length(status) / unique(nchecks),
            datasource = unique(datasource))


# Plot
png(outputPath, width=400, height=400)
ggplot(results_summarized, aes(level, proportion, fill = status)) +
  geom_bar(stat = "identity") +
  scale_fill_manual(drop = FALSE,
                    values = c("#00CC00", "#CC0000", "#CCCCCC", "#CCCC00")) +
  facet_wrap(~datasource) +
  labs(x = "", y = "Proportion of All Checks Run") +
  theme(legend.title = element_blank())
dev.off()

# Pass on result
result = list(value = outputPath, status="SUCCESS", message="See output path for plot." );
