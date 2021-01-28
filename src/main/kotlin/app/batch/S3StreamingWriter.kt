package app.batch

import app.configuration.CompressionInstanceProvider
import app.domain.EncryptingOutputStream
import app.domain.Record
import app.services.CipherService
import app.services.ExportStatusService
import app.services.KeyService
import app.services.SnapshotSenderMessagingService
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.annotation.AfterStep
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.io.*
import java.security.Key
import java.security.SecureRandom
import java.security.Security
import java.util.*
import javax.crypto.spec.SecretKeySpec
import kotlin.math.absoluteValue


@Component
@Profile("outputToS3")
@StepScope
class S3StreamingWriter(private val cipherService: CipherService,
                        private val keyService: KeyService,
                        private val secureRandom: SecureRandom,
                        private val s3: AmazonS3,
                        private val streamingManifestWriter: StreamingManifestWriter,
                        private val compressionInstanceProvider: CompressionInstanceProvider,
                        private val exportStatusService: ExportStatusService,
                        private val snapshotSenderMessagingService: SnapshotSenderMessagingService):
        ItemWriter<Record> {

    private var absoluteStart: Int = Int.MIN_VALUE
    private var absoluteStop: Int = Int.MAX_VALUE

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        absoluteStart = (stepExecution.executionContext["start"] as Int).absoluteValue
        absoluteStop = (stepExecution.executionContext["stop"] as Int).absoluteValue
    }

    @AfterStep
    fun afterStep(stepExecution: StepExecution): ExitStatus {
        writeOutput(openNext = false)
        return stepExecution.exitStatus
    }

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    override fun write(items: MutableList<out Record>) {
        items.forEach {
            val item = "${it.dbObjectAsString}\n"
            if (batchSizeBytes + item.length > maxBatchOutputSizeBytes || batchSizeBytes == 0) {
                writeOutput()
            }
            currentOutputStream!!.write(item.toByteArray())
            batchSizeBytes += item.length
            recordsInBatch++
            currentOutputStream!!.writeManifestRecord(it.manifestRecord)
        }
    }

    fun writeOutput(openNext: Boolean = true) {
        if (batchSizeBytes > 0) {
            val closed = currentOutputStream!!.close()


            val data = currentOutputStream!!.data()

            val inputStream = ByteArrayInputStream(data)
            val filePrefix = filePrefix()
            val slashRemovedPrefix = exportPrefix.replace(Regex("""/+$"""), "")
            val objectKey: String = "${slashRemovedPrefix}/$filePrefix-%06d.txt.${compressionInstanceProvider.compressionExtension()}.enc".format(currentBatch)

            if (!closed) {
                logger.error("Failed to close output streams cleanly", "object_key" to objectKey)
            }

            val metadata = ObjectMetadata().apply {
                contentType = "binary/octetstream"
                addUserMetadata("x-amz-meta-title", objectKey)
                addUserMetadata("iv", currentOutputStream!!.initialisationVector)
                addUserMetadata("cipherText", currentOutputStream!!.dataKeyResult.ciphertextDataKey)
                addUserMetadata("dataKeyEncryptionKeyId", currentOutputStream!!.dataKeyResult.dataKeyEncryptionKeyId)
                contentLength = data.size.toLong()
            }

            logger.info("Putting batch object into bucket",
                "s3_location" to objectKey, "records_in_batch" to "$recordsInBatch", "batch_size_bytes" to "$batchSizeBytes",
                "data_size_bytes" to "${data.size}", "export_bucket" to exportBucket, "max_batch_output_size_bytes" to "$maxBatchOutputSizeBytes",
                "total_snapshot_files_already_written" to "$totalBatches", "total_bytes_already_written" to "$totalBytes",
                "total_records_already_written" to "$totalRecords")

            // FIXME: 28/01/2021 Add retries 
            inputStream.use {
                val request = PutObjectRequest(exportBucket, objectKey, it, metadata)
                s3.putObject(request)
            }

            logger.info("Put batch object into bucket")

            exportStatusService.incrementExportedCount(objectKey)
            snapshotSenderMessagingService.notifySnapshotSender(objectKey)
            
            totalBatches++
            totalBytes += batchSizeBytes
            totalRecords += recordsInBatch

            try {
                streamingManifestWriter.sendManifest(s3, currentOutputStream!!.manifestFile, manifestBucket, manifestPrefix)
                totalManifestFiles++
                totalManifestRecords += currentOutputStream!!.manifestFile.length()
            } catch (e: Exception) {
                logger.error("Failed to write manifest", e, "manifest_file" to "${currentOutputStream!!.manifestFile}")
            }
        }

        if (openNext) {
            currentOutputStream = encryptingOutputStream()
            batchSizeBytes = 0
            recordsInBatch = 0
            currentBatch++
        }
    }

    private fun encryptingOutputStream(): EncryptingOutputStream {
        val keyResponse = keyService.batchDataKey()
        val key: Key = SecretKeySpec(Base64.getDecoder().decode(keyResponse.plaintextDataKey), "AES")
        val byteArrayOutputStream = ByteArrayOutputStream()
        val initialisationVector = ByteArray(16).apply {
            secureRandom.nextBytes(this)
        }
        val cipherOutputStream = cipherService.cipherOutputStream(key, initialisationVector, byteArrayOutputStream)
        val compressingStream = compressionInstanceProvider.compressorOutputStream(cipherOutputStream)
        val filePrefix = filePrefix()
        val manifestFile = File("$manifestOutputDirectory/$filePrefix-%06d.csv".format(currentBatch))
        val manifestWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(manifestFile)))

        return EncryptingOutputStream(
            BufferedOutputStream(compressingStream),
            byteArrayOutputStream,
            keyResponse,
            Base64.getEncoder().encodeToString(initialisationVector),
            manifestFile,
            manifestWriter)
    }

    private fun filePrefix() = "$topicName-%03d-%03d".format(absoluteStart, absoluteStop)

    private var currentOutputStream: EncryptingOutputStream? = null
    private var currentBatch = 0
    private var batchSizeBytes = 0
    private var recordsInBatch = 0
    private var totalBatches = 0
    private var totalBytes = 0
    private var totalRecords = 0
    private var totalManifestFiles = 0
    private var totalManifestRecords: Long = 0

    @Value("\${output.batch.size.max.bytes}")
    protected var maxBatchOutputSizeBytes: Int = 0

    @Value("\${s3.bucket}")
    private lateinit var exportBucket: String // i.e. "1234567890"

    @Value("\${s3.prefix.folder}")
    private lateinit var exportPrefix: String //i.e. "mongo-export-2019-06-23"

    @Value("\${s3.manifest.bucket}")
    private lateinit var manifestBucket: String

    @Value("\${s3.manifest.prefix.folder}")
    private lateinit var manifestPrefix: String

    @Value("\${topic.name}")
    private lateinit var topicName: String // i.e. "db.user.data"

    @Value("\${manifest.output.directory:.}")
    private lateinit var manifestOutputDirectory: String

    companion object {
        val logger = DataworksLogger.getLogger(S3StreamingWriter::class)
    }
}
