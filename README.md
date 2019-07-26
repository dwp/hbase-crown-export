# hbase-to-mongo-export

Selects the latest records from a single hbase table and writes them out in
mongo backup format, i.e. 1 json record per line.

## Requirements

1. JDK 8+
2. Docker
3. docker-compose

## Run locally

### In an IDE or not containerized (with containerized hbase)

This is slightly cumbersome as zookeeper gives the docker network name of the
hbase host which can't be resolved outside of docker, however this can be remedied
if the name given by zookeeper is then entered into the local ```/etc/hosts```
file.

1. Bring up the hbase container:

    docker-compose up -d hbase hbase-populate

2. Add hbase entry in local /etc/hosts file:

    sudo ./scripts/hosts.sh

It should now be possible to run code in an IDE against the local instance.

* The main class is
  ```app.HBaseToMongoExport```

* The program arguments are

  | Parameter name           | Default Value         | Further info
  |--------------------------|-----------------------|--------------
  | compress.output          | false                 | Whether to compress the output.
  | dataKeyServiceUrl        | http://localhost:8080 | Url of remote data key service.
  | directory.output         |                       | Directory to write output files to.
  | encrypt.output           | true                  | Whether to encrypt the output.
  | file.output              |                       | File to write output to - only needed if 'outputToFile' spring profile is active so not used in production.
  | hbase.zookeeper.quorum   | hbase                 | Name of the hbase host (set this to 'localhost' to run from IDE).
  | output.batch.size.max    |                       | The maxmum size of each  batch of output (calculated before compression and encryption.
  | source.cipher.algorithm  | AES/CTR/NoPadding     | The algorithm that was used to encrypt the source data.
  | source.table.name        |                       | Table in hbase to read data from.
  | target.cipher.algorithm  | AES/CTR/NoPadding     | The algorithm that should be used to encrypt the output data.


* The available spring profiles are

  | Profile name         | Production profile | Result
  |----------------------|--------------------|-------
  | aesCipherService     | Yes                | Use the actual cipher service that does real encryption and decryption.
  | batchRun             | Yes                | Activates the batch run (omit for unit tests).
  | httpDataKeyService   | Yes                | Use a remote (i.e. real) data key service (not a fake stubbed one)
  | localDataSource      | No                 | Indicates hbase is running locally i.e. not on a cluster.
  | outputToConsole      | No                 | Output is written to console as is (not encrypted or compressed).
  | outputToDirectory    | Yes                | Output is chunked and written to the configured directory.
  | outputToFile         | No                 | Output is written to configured file as is (used for the hbase integration test).
  | phoneyCipherService  | No                 | Use a cipher service that does not do real encryption.
  | phoneyDataKeyService | No                 | Use a dummy key service that does not require a configured DKS instance.
  | production           | Yes                | Use real http client and a strong random number generator (contrast with 'unitTest').
  | unitTest             | No                 | Use mock http client and psuedo random number generator (contrast with 'production').


### Run locally containerized
    HBASE_TO_MONGO_EXPORT_VERSION=$(cat ./gradle.properties | cut -f2 -d'=') \
        docker-compose up --build -d hbase hbase-populate hbase-to-mongo-export

### Additionally run the integration tests against local containerized setup
    docker-compose build hbase-to-mongo-export-itest
    docker-compose run hbase-to-mongo-export-itest
