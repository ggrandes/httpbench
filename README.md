# httpbench

HttpBench is a benchmark command line tool for measuring the performance of HTTP web servers. Open Source project under Apache License v2.0

### Current Stable Version is [1.0.0](https://search.maven.org/#search|ga|1|g%3Aorg.javastack%20a%3Ahttpbench)

---

## DOC

#### Usage Example (command line)

    java -jar httpbench-x.x.x.jar [<options>] <url> [<url> ...]
    
    Options:
    
    --connectTimeout (default: 30000)
    --readTimeout (default: 30000)
    --totalRequest (default: 1)
    --concurrency (default: 1)
    --contentType (default: text/plain)
    --method (default: GET)
    --keepAlive (default: false)

---
Inspired in [ApacheBench](https://httpd.apache.org/docs/2.4/programs/ab.html) and [Frederic Cambus](http://www.cambus.net/benchmarking-http-servers/), this code is Java-minimalistic version.
