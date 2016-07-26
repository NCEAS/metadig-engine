from edu.ucsb.nceas.mdqengine.dispatch import MDQCache

def get(url):
    cache = MDQCache()
    return cache.get(url)
