package app.batch

import app.domain.DecryptedRecord
import app.domain.ManifestRecord
import app.domain.SourceRecord
import app.exceptions.BadDecryptedDataException
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.util.*

@Component
class Validator {

    fun skipBadDecryptedRecords(item: SourceRecord, decrypted: String): DecryptedRecord? {
        val hbaseRowKey = Arrays.copyOfRange(item.hbaseRowId, 4, item.hbaseRowId.size)
        val hbaseRowId = String(hbaseRowKey)
        val db = item.db
        val collection = item.collection
        try {
            val jsonObject = parseDecrypted(decrypted)
            logger.info("Successfully parsed decrypted object.")
            if (null != jsonObject) {
                val id = retrieveId(jsonObject)
                val lastUpdatedTimestamp = retrieveLastUpdatedTimestamp(jsonObject)
                val timeAsLong = lastUpdatedTimestamp?.let { validateTimestampFormat(lastUpdatedTimestamp) }
                jsonObject.addProperty("timestamp", item.hbaseTimestamp)
                // Code reaches here only if the id and time are not nulls
                val manifestRecord = ManifestRecord(id!!.toString(), timeAsLong!!, db, collection, "EXPORT")
                return DecryptedRecord(jsonObject, manifestRecord)
            }
        } catch (e: Exception) {
            val ex = BadDecryptedDataException(hbaseRowId, db, collection, e.message!!)
            logger.error(ex.message)
            throw ex
        }
        return null
    }

    fun parseDecrypted(decrypted: String): JsonObject? {
        try {
            val jsonObject = Gson().fromJson(decrypted, JsonObject::class.java)
            return jsonObject
        } catch (e: Exception) {
            val parsingException = "Exception occurred while parsing decrypted db object"
            throw Exception(parsingException)
        }
    }

    fun retrieveId(jsonObject: JsonObject): JsonObject? {
        val id = jsonObject.getAsJsonObject("_id")
        if (null == id) {
            val idNotFound = "id not found in the decrypted db object"
            throw Exception(idNotFound)
        }
        return id
    }

    fun retrieveLastUpdatedTimestamp(jsonObject: JsonObject): JsonPrimitive? {
        val lastModifiedElement = jsonObject.get("_lastModifiedDateTime")
        logger.info("Getting '_lastModifiedDateTime' field is '$lastModifiedElement'.")

        return if (lastModifiedElement != null) {
            if (lastModifiedElement.isJsonPrimitive) {
                lastModifiedElement.asJsonPrimitive
            }
            else {
                val asObject = lastModifiedElement.asJsonObject
                val dateSubField = "\$date"
                asObject.getAsJsonPrimitive(dateSubField)
            }
        }
        else {
            throw Exception("'_lastModifiedDateTime' field not found in the decrypted db object")
        }
    }

    fun validateTimestampFormat(lastUpdatedTimestamp: JsonPrimitive): Long {

        val timestampAsStr = lastUpdatedTimestamp.getAsString()
        val validTimestamps = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZZZZ"
            )

        validTimestamps.forEach {
            try {
                val df = SimpleDateFormat(it)
                return df.parse(timestampAsStr).time
            }
            catch (e: Exception) {
                logger.debug("'$timestampAsStr' did not match date format '$it'")
            }
        }
        throw Exception("Unparseable date: \"$timestampAsStr\"")
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(Validator::class.toString())
    }
}
