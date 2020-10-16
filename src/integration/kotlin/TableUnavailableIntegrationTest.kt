import app.configuration.LocalStackConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import util.doesNotExistAttributeValue
import util.getItemRequest
import util.integrationTestCorrelationId
import util.primaryKeyMap

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [LocalStackConfiguration::class])
@ActiveProfiles("localstackConfiguration")
class TableUnavailableIntegrationTest {

    @Autowired
    private lateinit var amazonDynamoDb: AmazonDynamoDB

    @Test
    fun dynamoDBShouldHaveTableUnavailableRecord() {

        val correlationIdAttributeValue = integrationTestCorrelationId()

        val collectionNameAttributeValue = doesNotExistAttributeValue()

        val primaryKey = primaryKeyMap(correlationIdAttributeValue, collectionNameAttributeValue)

        val getItemRequest = getItemRequest(primaryKey)

        val result = amazonDynamoDb.getItem(getItemRequest)
        val item = result.item
        val status = item["CollectionStatus"]?.s

        val expectedCollectionStatus = "Table_Unavailable"

        status shouldBe expectedCollectionStatus
    }
}
