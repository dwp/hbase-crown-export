package app.batch.processor

import app.batch.Validator
import app.domain.DecryptedRecord
import app.domain.SourceRecord
import app.exceptions.BadDecryptedDataException
import app.exceptions.DataKeyServiceUnavailableException
import app.exceptions.DecryptionFailureException
import app.services.CipherService
import app.services.KeyService
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.util.*

@Component
class DecryptionProcessor(private val cipherService: CipherService,
                          private val keyService: KeyService) :
        ItemProcessor<SourceRecord, DecryptedRecord> {

    @Autowired
    private lateinit var validator: Validator

    @Throws(DataKeyServiceUnavailableException::class)
    override fun process(item: SourceRecord): DecryptedRecord? {
        try {
            logger.info("Processing '$item'.")
            val decryptedKey = keyService.decryptKey(
                    item.encryption.keyEncryptionKeyId,
                    item.encryption.encryptedEncryptionKey)
            val decrypted =
                    cipherService.decrypt(
                            decryptedKey,
                            item.encryption.initializationVector,
                            item.dbObject)
            return validator.skipBadDecryptedRecords(item, decrypted)
        } catch (e: DataKeyServiceUnavailableException) {
            throw e
        } catch (e: Exception) {
            logger.error("Rejecting '$item': '${e.message}': '${e.javaClass}': '${e.message}'.")
            throw DecryptionFailureException(
                    "database-unknown",
                    "collection-unknown",
                    item.hbaseRowId,
                    item.hbaseTimestamp,
                    item.encryption.keyEncryptionKeyId,
                    e)
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DecryptionProcessor::class.toString())
    }
}





