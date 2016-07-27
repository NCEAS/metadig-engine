# Install all the R packages in 'r-packages.txt'
package_file <- "r-packages.txt"
stopifnot(file.exists(package_file))
packages <- readLines(package_file)
install.packages(packages, repo = "http://cran.rstudio.com")
