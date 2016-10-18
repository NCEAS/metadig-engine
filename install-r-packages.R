# Install all the R packages in 'r-packages.txt'

# Install devtools if it isn't already
if (!("devtools" %in% installed.packages())) {
  cat(paste("Installing devtools"), "\n")
  install.packages("devtools", repo = "http://cran.rstudio.com")
}

# Install packages
package_file <- "r-packages.txt"
stopifnot(file.exists(package_file))
packages <- readLines(package_file)

# Split packages into those installable from CRAN and those installable
# from GitHub
from_cran <- packages[grep("/", packages, invert=TRUE)]
from_gh <- packages[grep("/", packages)]

for (pkg in from_cran) {
  if (pkg %in% installed.packages()) next
  cat(paste("Installing", pkg), "\n")
  install.packages(pkg, repo = "http://cran.rstudio.com")
}

for (pkg in from_gh) {
  pkg_parts <- strsplit(pkg, ": ")
  pkg_name <- pkg_parts[[1]][1]
  pkg_repo <- pkg_parts[[1]][2]

  if (pkg_name %in% installed.packages()) next
  cat(paste("Installing", pkg_name), "\n")
  devtools::install_github(pkg_repo)
}
