package app.services.impl

import app.configuration.HttpClientProvider
import app.domain.DataKeyResult
import app.exceptions.DataKeyDecryptionException
import app.exceptions.DataKeyServiceUnavailableException
import app.services.KeyService
import app.utils.UUIDGenerator
import com.google.gson.Gson
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder

@Service
@Profile("httpDataKeyService")
class HttpKeyService(private val httpClientProvider: HttpClientProvider,
                     private val uuidGenerator: UUIDGenerator): KeyService {

    companion object {
        val logger = DataworksLogger.getLogger(HttpKeyService::class)
    }

    @Override
    @Retryable(value = [DataKeyServiceUnavailableException::class],
        maxAttemptsExpression = "\${keyservice.retry.maxAttempts:5}",
        backoff = Backoff(delayExpression = "\${keyservice.retry.delay:1000}",
            multiplierExpression = "\${keyservice.retry.multiplier:2}"))
    @Throws(DataKeyServiceUnavailableException::class)
    @Synchronized
    @Cacheable("ENCRYPTED_KEY_CACHE")
    override fun batchDataKey(): DataKeyResult {
        return dataKey()
//        if (dataKeyResult == null) {
//            dataKeyResult = dataKey()
//        }
//        return dataKeyResult!!
    }

    private fun dataKey(): DataKeyResult {
        val dksUrl = "$dataKeyServiceUrl/datakey"
        val dksCorrelationId = uuidGenerator.randomUUID()
        val dksUrlWithCorrelationId = "$dksUrl?correlationId=$dksCorrelationId"
        try {
            httpClientProvider.client().use { client ->
                client.execute(HttpGet(dksUrlWithCorrelationId)).use { response ->
                    val statusCode = response.statusLine.statusCode
                    return if (statusCode == 201) {
                        val entity = response.entity
                        val result = BufferedReader(InputStreamReader(entity.content))
                            .use(BufferedReader::readText).let {
                                Gson().fromJson(it, DataKeyResult::class.java)
                            }
                        EntityUtils.consume(entity)
                        result
                    } else {
                        logger.warn("Getting batch data key - data key service returned bad status code",
                            "dks_url" to dksUrl,
                            "dks_correlation_id" to dksCorrelationId,
                            "status_code" to "$statusCode")
                        throw DataKeyServiceUnavailableException("Getting batch data key - data key service returned bad status code '$statusCode' for dks_correlation_id: '$dksCorrelationId'")
                    }
                }
            }
        } catch (ex: Exception) {
            when (ex) {
                is DataKeyServiceUnavailableException -> {
                    throw ex
                }
                else -> {
                    ex.printStackTrace()
                    throw DataKeyServiceUnavailableException("Error contacting data key service: '$ex' for dks_correlation_id: '$dksCorrelationId'")
                }
            }
        }
    }

    @Override
    @Retryable(value = [DataKeyServiceUnavailableException::class],
        maxAttemptsExpression = "\${keyservice.retry.maxAttempts:5}",
        backoff = Backoff(delayExpression = "\${keyservice.retry.delay:1000}",
            multiplierExpression = "\${keyservice.retry.multiplier:2}"))
    @Throws(DataKeyServiceUnavailableException::class, DataKeyDecryptionException::class)
    @Cacheable("DECRYPTED_KEY_CACHE")
    override fun decryptKey(encryptionKeyId: String, encryptedKey: String): String {
        val dksCorrelationId = uuidGenerator.randomUUID()
        try {
            val cacheKey = "$encryptedKey/$encryptionKeyId"
//            return if (decryptedKeyCache.containsKey(cacheKey)) {
//                decryptedKeyCache[cacheKey]!!
//            } else {
                httpClientProvider.client().use { client ->
                    val dksUrl = "$dataKeyServiceUrl/datakey/actions/decrypt?keyId=${
                        URLEncoder.encode(encryptionKeyId,
                            "US-ASCII")
                    }"
                    val dksUrlWithCorrelationId = "$dksUrl&correlationId=$dksCorrelationId"
                    logger.debug(
                        "Starting decryptKey call to data key service",
                        "dks_url" to dksUrl,
                        "dks_correlation_id" to dksCorrelationId
                    )
                    val httpPost = HttpPost(dksUrlWithCorrelationId)
                    httpPost.entity = StringEntity(encryptedKey, ContentType.TEXT_PLAIN)
                    client.execute(httpPost).use { response ->
                        val statusCode = response.statusLine.statusCode
                        logger.debug(
                            "Finished decryptKey call to data key service",
                            "dks_url" to dksUrl,
                            "dks_correlation_id" to dksCorrelationId,
                            "status_code" to "$statusCode"
                        )
                        return when (statusCode) {
                            200 -> {
                                val entity = response.entity
                                val text =
                                    BufferedReader(InputStreamReader(response.entity.content)).use(BufferedReader::readText)
                                EntityUtils.consume(entity)
                                val dataKeyResult = Gson().fromJson(text, DataKeyResult::class.java)
                                decryptedKeyCache[cacheKey] = dataKeyResult.plaintextDataKey
                                dataKeyResult.plaintextDataKey
                            }
                            400 -> {
                                logger.error(
                                    "DataKeyDecryptionException from data key service",
                                    "encrypted_key" to encryptedKey,
                                    "key_encryption_key_id" to encryptionKeyId,
                                    "dks_url" to dksUrl,
                                    "dks_correlation_id" to dksCorrelationId,
                                    "status_code" to "$statusCode"
                                )
                                throw DataKeyDecryptionException(
                                    "Decrypting encryptedKey: '$encryptedKey' with keyEncryptionKeyId: '$encryptionKeyId' data key service returned status code '$statusCode' for dks_correlation_id: '$dksCorrelationId'"
                                )
                            }
                            else -> {
                                logger.error(
                                    "DataKeyServiceUnavailableException from data key service",
                                    "encrypted_key" to encryptedKey,
                                    "key_encryption_key_id" to encryptionKeyId,
                                    "dks_url" to dksUrl,
                                    "dks_correlation_id" to dksCorrelationId,
                                    "status_code" to "$statusCode"
                                )
                                val ex = DataKeyServiceUnavailableException(
                                    "Decrypting encryptedKey: '$encryptedKey' with keyEncryptionKeyId: '$encryptionKeyId' data key service returned status code '$statusCode' for dks_correlation_id: '$dksCorrelationId'"
                                )
                                throw ex
                            }
                        }
                    }
                }
            //}
        } catch (ex: Exception) {
            when (ex) {
                is DataKeyDecryptionException, is DataKeyServiceUnavailableException -> {
                    throw ex
                }
                else -> throw DataKeyServiceUnavailableException("Error contacting data key service: '$ex' for dks_correlation_id: '$dksCorrelationId'")
            }
        }
    }

    fun clearCache() {
        this.decryptedKeyCache = mutableMapOf()
    }

    private var decryptedKeyCache = mutableMapOf<String, String>()

    private var dataKeyResult: DataKeyResult? = null

    @Value("\${data.key.service.url}")
    private lateinit var dataKeyServiceUrl: String
}
