library(digest);

get <- function(url) {
    # TODO: get correct temp dir from system property
    tempDir = Sys.getenv("java.io.tmpdir");
    cacheDir = tempDir + "/mdq-cache/";
    key = digest(url, algo="md5" ascii=TRUE);
    filePath = cacheDir + key;
    if (!filePath exists) {
        # TODO: read from URL to filePath on disk
    }
    return filePath;
}