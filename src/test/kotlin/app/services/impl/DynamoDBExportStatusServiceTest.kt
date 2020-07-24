package app.services.impl

import app.services.ExportStatusService
import com.amazonaws.SdkClientException
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.retry.annotation.EnableRetry
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@EnableRetry
@SpringBootTest(classes = [DynamoDBExportStatusService::class])
class DynamoDBExportStatusServiceTest {

    @SpyBean
    @Autowired
    private lateinit var exportStatusService: ExportStatusService

    @MockBean
    private lateinit var amazonDynamoDB: AmazonDynamoDB

    @Before
    fun before() = reset(amazonDynamoDB)

    @Test
    fun incrementExportedCountRetries() {
        given(amazonDynamoDB.updateItem(any()))
                .willThrow(SdkClientException(""))
                .willThrow(SdkClientException(""))
                .willReturn(mock())
        exportStatusService.incrementExportedCount("")
        verify(amazonDynamoDB, times(3)).updateItem(any())
    }

    @Test
    fun setFailedSetsCorrectStatus() {
        given(amazonDynamoDB.updateItem(any()))
                .willReturn(mock())
        exportStatusService.setFailedStatus()
        val argumentCaptor = argumentCaptor<UpdateItemRequest>()
        verify(amazonDynamoDB, times(1)).updateItem(argumentCaptor.capture())
        val actual = argumentCaptor.firstValue.expressionAttributeValues
        val expected =  mapOf(":x" to AttributeValue().apply { s = "Export_Failed" })
        assertEquals(actual, expected)
    }

    @Test
    fun setExportedSetsCorrectStatus() {
        given(amazonDynamoDB.updateItem(any()))
                .willReturn(mock())
        exportStatusService.setExportedStatus()
        val argumentCaptor = argumentCaptor<UpdateItemRequest>()
        verify(amazonDynamoDB, times(1)).updateItem(argumentCaptor.capture())
        val actual = argumentCaptor.firstValue.expressionAttributeValues
        val expected =  mapOf(":x" to AttributeValue().apply { s = "Exported" })
        assertEquals(actual, expected)
    }

    @Test
    fun setExportedStatusRetries() {
        given(amazonDynamoDB.updateItem(any()))
                .willThrow(SdkClientException(""))
                .willThrow(SdkClientException(""))
                .willReturn(mock())
        exportStatusService.setExportedStatus()
        verify(amazonDynamoDB, times(3)).updateItem(any())
    }

    @Test
    fun setFailedStatusRetries() {
        given(amazonDynamoDB.updateItem(any()))
                .willThrow(SdkClientException(""))
                .willThrow(SdkClientException(""))
                .willReturn(mock())
        exportStatusService.setFailedStatus()
        verify(amazonDynamoDB, times(3)).updateItem(any())
    }
}
