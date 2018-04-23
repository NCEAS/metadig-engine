# Install all the R packages

# The packages we're going to install
packages_cran <- c("jsonlite",
                   "digest",
                   "httr")

packages_github <- list("metadig" = "NCEAS/metadig-r")

# Install devtools if it isn't already
if (!("devtools" %in% installed.packages())) {
  cat(paste("Installing devtools"), "\n")
  install.packages("devtools", repo = "http://cran.rstudio.com")
}

# Install packages
for (pkg in packages_cran) {
  if (pkg %in% installed.packages()) next
  cat(paste("Installing", pkg), "\n")
  install.packages(pkg, repo = "http://cran.rstudio.com")
}

for (pkg in names(packages_github)) {
  pkg_repo <- packages_github[[pkg]]

  if (pkg %in% installed.packages()) next
  cat(paste("Installing", pkg), "\n")
  devtools::install_github(pkg_repo)
}
