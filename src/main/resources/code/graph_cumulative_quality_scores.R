library(dplyr)
library(tidyr)
library(ggplot2)
library(scales)
library(lubridate)
library(readr)
library(magrittr)

# Plot cummulative quality scores by month
# This program is dispatched (called) by the MetaDIG Scorer class. Several
# variables are injected by metadig-engine Dispatcher
# - title: the graph title
# - inFile: the CSV file containing quality scores, which has been prepared by Grapher
# - outFile: the graphics output file to create
# Variables read by metadig-engine Dispatcher after execution
# mdq_result, output, status

# Define these variable ("infile", "outFile" for local testing only
#inFile <- "toolik.csv"
#outFile <- "toolik-cumulative.png"

axisTextFontSize <- 7
legendTextFontSize <- 8
axisTitleFontSize <- 9
legendTitleFontSize <- 9
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
score_cumulative_alone <- score_cumulative %>% filter(metric %in% c("Cum. Findable", "Cum. Accessible", "Cum. Interoperable", "Cum. Reusable"))

# Fetch the last year in the cumulative scores
ymLatest <- with(score_cumulative, max(ym))
# Fetch last means - these will be used for the legend to show the mean of the latest and hopefully
# best scores for the latest time slot (month)
mfLatest <- score_cumulative_alone %>% filter(ym == ymLatest) %>% filter(metric %in% "Cum. Findable") %>% extract2("mean")
maLatest <- score_cumulative_alone %>% filter(ym == ymLatest) %>% filter(metric %in% "Cum. Accessible") %>% extract2("mean")
miLatest <- score_cumulative_alone %>% filter(ym == ymLatest) %>% filter(metric %in% "Cum. Interoperable") %>% extract2("mean")
mrLatest <- score_cumulative_alone %>% filter(ym == ymLatest) %>% filter(metric %in% "Cum. Reusable") %>% extract2("mean")

# See if the 'dateUploaded' dates span multiple years and if not, the x-axis needs to be configured for ggplot so that
# it will display. If it is configured for years and only a single year exists, the x-axis will not display.
minYear <- format(with(scores, min(dateUploaded)), "%Y")
maxYear <- format(with(scores, max(dateUploaded)), "%Y")
if(minYear == maxYear) {
  xLabel <- "Month"
  dateBreaks <- "months"
  dateMinorBreaks <- "day"
  dateFormat <- "%Y-%m"
} else {
  xLabel <- "Year"
  dateBreaks <- "year"
  dateMinorBreaks <- "months"
  dateFormat <- "%Y"
}

# Plot cummulative overall
d1_colors <- c("#ff582d", "#c70a61", "#1a6379", "#60c5e4", "#ff582d", "#c70a61", "#1a6379", "#60c5e4")
p <- ggplot(data=score_cumulative_alone, mapping=aes(x=ym, y=mean, color=metric)) +
  geom_line() +
  geom_point(size=1) +
  theme_bw() +
  theme(panel.border = element_blank(),
        axis.line = element_line(colour = "black"),
        axis.text = element_text(size = axisTextFontSize),
        axis.title = element_text(size = axisTitleFontSize),
        legend.title = element_text(size = legendTitleFontSize),
        legend.text = element_text(size = legendTextFontSize),
        panel.grid.minor = element_blank(),
        panel.background = element_blank()) +

  scale_color_manual(name = "Metric", labels = c(sprintf("Findable (%.0f%%)", mfLatest),
                                                 sprintf("Accessible (%.0f%%)", maLatest),
                                                 sprintf("Interoperable (%.0f%%)", miLatest),
                                                 sprintf("Reusable (%.0f%%)", mrLatest)), values=d1_colors) +
  scale_x_date(date_breaks=dateBreaks, date_minor_breaks=dateMinorBreaks, labels=date_format(dateFormat)) +
  xlab(xLabel) +
  scale_y_continuous(limits=c(0,100)) +
  ylab("Average FAIR Score") +
  #ggtitle(paste0("DataONE: FAIR scores for ", format(sum(standards$n), big.mark=","), " EML and ISO metadata records"))
  #scale_fill_discrete(name = "metric", labels = c("Finabl", "Accessibl", "Interoperabl", "Reusabl")) +
  ggsave(outFile, width = 8.0, height = 3.0)

output <- sprintf("Created graphics file %s", outFile)
status <- "SUCCESS"

mdq_result <- list(status = "SUCCESS",
                   output = list(list(value = "Plot created successfully.")))

