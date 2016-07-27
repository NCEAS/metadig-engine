library(methods)
library(svglite)
library(ggplot2)

#args = commandArgs(trailingOnly=TRUE)
#inputPath = args[1]
#outputPath = args[2]

results = read.csv(inputPath)

png(outputPath, width=400, height=400)
ggplot(results, aes(level, fill = status)) + geom_bar() + facet_wrap(~ datasource)
dev.off()

result = list(value = outputPath, status="SUCCESS", message="See output path for plot." );
