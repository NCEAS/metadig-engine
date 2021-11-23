library(tidyverse)
library(purrr)
library(RPostgres)

fair_suite <- '0.3.1'

con <- dbConnect(RPostgres::Postgres(),
                  host="localhost",
                  dbname="metadig",
                  user="metadig",
                  password="metadig")

print(con)
dbListTables(con)

# Variables that may be provided to create the graph
# - collectionId
#   - this could be a DataONE collection id, e.g. a seriesId for a portal. If this is specified, then a list of
#     DataONE pids that 'belong' to this collection must be provided by the calling program, as this program does not
#     have the capability to obtain this list itself.
# - suiteId
# - nodeId - if creating graph for an entire node
#   - could be for an MN
#   - could be for the CN, which is a special case for which results are not restricted to any one node, i.e.
#     results for all nodes are retrieved.
#

query <- "select c.check_id,c.check_name,c.check_type,c.check_level,c.status,i.data_source,i.metadata_id,i.obsoleted_by from identifiers i left join check_results c on i.metadata_id = c.metadata_id AND c.suite_id='FAIR-suite-0.3.1'"
checks <- dbGetQuery(con, query)

checks <- checks %>%
  filter(status != "SKIP") %>%
  filter(status != "ERROR") %>%
  mutate(check_type = factor(check_type, levels = c(F="Findable", A="Accessible", I="Interoperable", R="Reusable"))) %>%
  mutate(check_level = factor(check_level, levels = c(R="REQUIRED", O="OPTIONAL")))

checks_list <- checks %>%
  select(check_id, check_name, check_type, check_level) %>%
  group_by(check_id) %>%
  dplyr::slice(1) %>%
  arrange(check_type, check_level, check_name)

check_names_factor <- factor(levels = checks_list$check_name)

checks <- mutate(checks, check_name = factor(check_name, levels(check_names_factor)))

status_palette <- c("salmon2", "seagreen4")

checks_subset <- checks %>% filter(data_source %in% nodeId)

# ggplot(checks_subset, aes(x=forcats::fct_rev(check_level), fill=status)) +
#   geom_bar(position="fill") +
#   theme_bw() +
#   scale_fill_manual(values = status_palette) +
#   xlab(element_blank()) +
#   ylab("Proportion") +
#   facet_grid(rows = vars(data_source), cols = vars(check_type))
# ggsave("figures/dataone-fair-0.3.1-nodes-summary.png", height=10, width=10)

## Plot FAIR suite results by check and repository

plot_suite_results <- function(df, node = NA, fair_suite = NA) {

  status_palette <- c("salmon2", "seagreen4")
  p <- ggplot(df, aes(x=forcats::fct_rev(check_name), fill=status)) +
    geom_bar(position="fill") +
    theme_bw() +
    ggtitle(paste0(node, ': FAIR Suite ', fair_suite)) +
    scale_fill_manual(values = status_palette) +
    facet_wrap(facets = ~ check_type, ncol = 4) +
    xlab("Check Name") +
    ylab("Proportion") +
    coord_flip()
  ggsave(paste0(outfile), height=10, width=10)
  return(p)
}

checks <- checks %>%
  #filter(!data_source %in% (c("urn:node:mnTestNKN", "urn:node:ONEShare_test"))) %>%

  #filter(data_source %in% c("urn:node:CAS_CERN", "urn:node:ESA", "urn:node:FEMC", "urn:node:GLEON", "urn:node:GOA", "urn:node:GRIIDC", "urn:node:IOE",
                          #"urn:node:LTER", "urn:node:LTER_EUROPE", "urn:node:METAGRIL", "urn:node:NCEI", "urn:node:NEON", "urn:node:NRDC",
                          #"urn:node:OTS_NDC", "urn:node:PANGAEA", "urn:node:PISCO", "urn:node:PPBIO", "urn:node:RW", "urn:node:SANPARKS", "urn:node:TERN",
                          #"urn:node:TFRI", "urn:node:USANPN")) %>%
  filter(is.na(obsoleted_by)) %>%
  mutate(repo = stringr::str_remove(data_source, pattern="urn:node:")) %>%
  arrange(check_type)

repo_fair_plots <- checks %>%
  split(.$data_source) %>%
  purrr::imodify( ~plot_suite_results(df = as.data.frame(.x), node = .y, fair_suite = fair_suite))
repo_fair_plots