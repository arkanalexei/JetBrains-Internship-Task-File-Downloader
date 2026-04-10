# Concurrent File Downloader

A concurrent file downloader written in Kotlin that splits files into chunks and downloads them in parallel, combining
them into a single output file.

## How It Works

1. Sends a `HEAD` request to discover the file size and whether the server supports range requests
2. Divides the file into equally-sized byte ranges via `ChunkCalculator`
3. Launches each chunk as a concurrent coroutine on `Dispatchers.IO`, each sending a `GET` request with the appropriate
   `Range` header
4. Writes each chunk directly to its correct offset in the pre-allocated output file using `FileChannel`, which is safe
   for concurrent non-overlapping writes
5. Emits a `DownloadState` flow of `Starting → Downloading (progress) → Success | Error`
6. Gracefully degrades to single-threaded if the server does not support range requests, to prevent file corruption

## Architecture

```
downloader/
├── core/
│   ├── ChunkCalculator.kt          # Divides file size into byte ranges
│   └── ConcurrentFileDownloader.kt # Orchestrates parallel download, emits state flow
├── domain/
│   ├── ByteRange.kt                # Value type: start/end byte offsets
│   ├── DownloadState.kt            # Sealed interface: Starting, Downloading, Success, Error
│   └── FileInfo.kt                 # Value type: file size + range support flag
├── io/
│   └── ConcurrentFileWriter.kt     # Thread-safe file writes via FileChannel
├── network/
│   └── NetworkClient.kt            # HEAD + ranged GET via Ktor HttpClient
└── Main.kt                         # Entry point
```

## Prerequisites

- JDK 17+
- Docker (to run the local file server)

## Running the Downloader

### 1. Create a test file

Open **Command Prompt** and run:

```cmd
mkdir data
fsutil file createnew data\my-local-file.txt 52428800
```

This creates a 50 MB file filled with zeroes.

### 2. Start the local file server

```cmd
docker run --rm -p 8080:80 -v "${PWD}\data:/usr/local/apache2/htdocs/" httpd:latest
```

Verify it is running by visiting [http://localhost:8080/my-local-file.txt](http://localhost:8080/my-local-file.txt) in
your browser.

### 3. Run the downloader

```cmd
./gradlew run
```

Or with custom arguments (URL, destination, thread count):

```cmd
./gradlew run --args="http://localhost:8080/my-local-file.txt data/downloaded-file.txt 4"
```

The downloader will log progress and print a completion message when done.

### 4. Verify file integrity

Compare the MD5 hash of the original file against the downloaded file to confirm they are byte-for-byte identical:

```cmd
CertUtil -hashfile data\my-local-file.txt MD5
CertUtil -hashfile data\downloaded-file.txt MD5
```

Both hashes should match. For example:

```
MD5 hash of data\my-local-file.txt:
7f9669c4c4b5091c5fdfa83680976f95
CertUtil: -hashfile command completed successfully.

MD5 hash of data\downloaded-file.txt:
7f9669c4c4b5091c5fdfa83680976f95
CertUtil: -hashfile command completed successfully.
```

## Running the Tests

```cmd
./gradlew test
```

### Test coverage report

```cmd
./gradlew test jacocoTestReport
```

Open `build/reports/jacoco/test/html/index.html` in your browser.

### Test structure

| Test class                       | What it covers                                                                      |
|----------------------------------|-------------------------------------------------------------------------------------|
| `ChunkCalculatorTest`            | Edge cases: single byte, remainder distribution, gap/overlap checks, invalid inputs |
| `ConcurrentFileWriterTest`       | Correct byte offsets, pre-allocation, concurrent non-overlapping writes             |
| `NetworkClientTest`              | HEAD response parsing, Range header correctness, missing Content-Length             |
| `NetworkClientMissingHeaderTest` | Missing headers, non-success HTTP status codes                                      |
| `ConcurrentFileDownloaderTest`   | Full state sequence, error emission, CancellationException propagation              |
| `DownloaderIntegrationTest`      | End-to-end download against a real embedded Ktor server                             |

## Further Improvements

The following were intentionally omitted to keep the implementation focused on the core requirements, but would be
natural next steps in a production context:

- Retry logic. A failed chunk currently cancels the entire download via structured concurrency. A production
  downloader would retry individual chunks with exponential backoff before propagating the error
- Timeout configuration. The Ktor `HttpClient` has no timeout set, meaning a hung connection would stall
  indefinitely. Configuring `connectTimeoutMillis` and `socketTimeoutMillis` would make the downloader resilient to
  unresponsive servers
- Buffer ownership. The same `ByteArray` buffer is reused across loop iterations in `NetworkClient.streamChunk` and
  passed by reference to `onBytesReceived`. This is safe given the current synchronous callback usage, but if the
  callback were ever made asynchronous, it would cause data corruption. A defensive fix would be to copy the buffer
  before passing it: `onBytesReceived(buffer.copyOf(bytesRead), bytesRead)`

## Dependencies

| Library                  | Purpose                               |
|--------------------------|---------------------------------------|
| Ktor Client              | HTTP requests (HEAD + ranged GET)     |
| Ktor Server (test only)  | Embedded server for integration tests |
| Kotlin Coroutines        | Parallel chunk downloading            |
| MockK                    | Mocking in unit tests                 |
| Turbine                  | Flow assertion in unit tests          |
| kotlin-logging / Logback | Structured logging                    |
| JUnit 5                  | Test runner                           |
