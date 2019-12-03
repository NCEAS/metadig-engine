library(dplyr)
library(tidyr)
library(ggplot2)
library(scales)
library(lubridate)
library(readr)

# Plot cummulative quality scores by month
# This program is dispatched (called) by the MetaDIG Grapher class. Several
# variables are injected by metadig-engine Dispatcher
# - title: the graph title
# - title: the graph title
# - inFile: the CSV file containing quality scores, which has been prepared by Grapher
# - outFile: the graphics output file to create
# Variables read by metadig-engine Dispatcher after execution
# mdq_result, output, status 

# Load data
fsr <- read_csv(inFile)

scores <- mutate(fsr, ym = as.Date(sprintf("%4s-%02d-01", year(dateUploaded), month(dateUploaded)))) %>%
  mutate(scoreF = scoreFindable * 100.0) %>%
  mutate(scoreA = scoreAccessible * 100.0) %>%
  mutate(scoreI = scoreInteroperable * 100.0) %>%
  mutate(scoreR = scoreReusable * 100.0)

most_recent <- scores %>%
  arrange(ym, sequenceId, dateUploaded) %>%
  group_by(ym, sequenceId) %>%
  top_n(1, dateUploaded)
head(most_recent)

# calculate cummulative overall
score_cumulative <- most_recent %>%
  arrange(ym) %>%
  group_by(ym) %>%
  summarise(f=mean(scoreF), a=mean(scoreA), i=mean(scoreI), r=mean(scoreR)) %>%
  mutate(fc=cummean(f), ac=cummean(a), ic=cummean(i), rc=cummean(r)) %>%
  select(ym, f, a, i, r, fc, ac, ic, rc) %>%
  gather(metric, mean, -ym)
score_cumulative$metric <- factor(score_cumulative$metric,
                                  levels=c("f", "a", "i", "r", "fc", "ac", "ic", "rc"),
                                  labels=c("Findable", "Accessible", "Interoperable", "Reusable",
                                           "Cum. Findable", "Cum. Accessible", "Cum. Interoperable", "Cum. Reusable"))
score_monthly <- score_cumulative %>% filter(metric %in% c("Findable", "Accessible", "Interoperable", "Reusable"))
score_cumulative_alone <- score_cumulative %>% filter(metric %in% c("Cum. Findable", "Cum. Accessible", "Cum. Interoperable", "Cum. Reusable"))


# Plot cummulative overall
d1_colors <- c("#ff582d", "#c70a61", "#1a6379", "#60c5e4", "#ff582d", "#c70a61", "#1a6379", "#60c5e4")
p <- ggplot(data=score_cumulative_alone, mapping=aes(x=ym, y=mean, color=metric)) +
  geom_line() +
  geom_point(size=1) +
  theme_bw() +
  theme(panel.border = element_blank(),
        axis.line = element_line(colour = "black"),
        panel.grid.minor = element_blank(),
        panel.background = element_blank()) +
  scale_colour_manual(values=d1_colors) +
  scale_x_date(date_breaks="year", date_minor_breaks="months", labels=date_format("%Y")) +
  xlab("Year") +
  scale_y_continuous(limits=c(0,100)) +
  ylab("Average FAIR Score") +
  #ggtitle(paste0("DataONE: FAIR scores for ", format(sum(standards$n), big.mark=","), " EML and ISO metadata records"))
  #ggtitle(title)
ggsave(outFile, width = 8, height = 4)

#output <- "/Users/slaughter/tmp/dataone-fair-scores-overall.png"
output <- sprintf("Created graphics file %s", outFile)
status <- "SUCCESS"

mdq_result <- list(status = "SUCCESS",
                   output = list(list(value = "Plot created successfully.")))

