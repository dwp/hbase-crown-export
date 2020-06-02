package app.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DateWrapperTest {

    @Test
    fun processesDeepDates() {
        val dateKey = "\$date"

        val json = """{
            |   _lastModifiedDateTime: {
            |       $dateKey: "2001-12-14T15:01:02.000+0000"    
            |   },
            |   notDate1: 123,
            |   notDate2: "abc",
            |   parentDate: "2017-12-14T15:01:02.000+0000",
            |   childObjectWithDates: {
            |       _lastModifiedDateTime: {
            |           $dateKey: "1980-12-14T15:01:02.000+0000"    
            |       },
            |       grandChildObjectWithDate: {
            |           notDate1: 123,
            |           notDate2: "abc",
            |           grandChildDate1: "2019-12-14T15:01:02.000+0000"
            |       },
            |       childDate: "2018-12-14T15:01:02.000+0000",
            |       arrayWithDates: [
            |           789,
            |           "xyz",
            |           "2010-12-14T15:01:02.000+0000",
            |           [
            |               "2011-12-14T15:01:02.000+0000",
            |               "qwerty"
            |           ],
            |           {
            |               grandChildDate3: "2012-12-14T15:01:02.000+0000",
            |               _lastModifiedDateTime: "1995-12-14T15:01:02.000+0000"
            |           }
            |       ]
            |   }
            |}
        """.trimMargin()

        val jsonObject = Gson().fromJson(json, JsonObject::class.java)
        DateWrapper().processJsonObject(jsonObject, false)

        val wrapped = """{
            |   _lastModifiedDateTime: {
            |       $dateKey: "2001-12-14T15:01:02.000+0000"    
            |   },
            |   notDate1: 123,
            |   notDate2: "abc",
            |   parentDate: {
            |       $dateKey: "2017-12-14T15:01:02.000Z"
            |   },
            |   childObjectWithDates: {
            |       _lastModifiedDateTime: {
            |           $dateKey: "1980-12-14T15:01:02.000Z"    
            |       },
            |       grandChildObjectWithDate: {
            |           notDate1: 123,
            |           notDate2: "abc",
            |           grandChildDate1: {
            |               "$dateKey": "2019-12-14T15:01:02.000Z"
            |           }
            |       },
            |       childDate: {
            |           "$dateKey": "2018-12-14T15:01:02.000Z"
            |       },
            |       arrayWithDates: [
            |           789,
            |           "xyz",
            |           { $dateKey: "2010-12-14T15:01:02.000Z" },
            |           [
            |               { $dateKey: "2011-12-14T15:01:02.000Z" },
            |               "qwerty"
            |           ],
            |           {
            |               grandChildDate3: { $dateKey: "2012-12-14T15:01:02.000Z" },
            |               _lastModifiedDateTime: { $dateKey: "1995-12-14T15:01:02.000Z" }
            |           }
            |       ]
            |    }
            |}
        """.trimMargin()
        val wrappedObject = Gson().fromJson(wrapped, JsonObject::class.java)
        assertEquals(wrappedObject, jsonObject)
    }

    @Test
    fun testIgnoresLastModifiedDateTime() {
        val json = """{
            |   _lastModifiedDateTime: "2001-12-14T15:01:02.000+0000"    
            |}
        """.trimMargin()

        val jsonObject = Gson().fromJson(json, JsonObject::class.java)
        DateWrapper().processJsonObject(jsonObject, false)
        val wrappedObject = Gson().fromJson(json, JsonObject::class.java)
        assertEquals(wrappedObject, jsonObject)
    }

    @Test
    fun testWrapsCommonDates() {
        val dateKey = "\$date"

        val json = """{
            |   _lastModifiedDateTime: "2001-12-14T15:01:02.000+0000",    
            |   createdDateTime: "2001-12-01T15:01:02.000+0000",    
            |   _removedDateTime: "2001-12-02T15:01:02.000+0000",    
            |   _archivedDateTime: "2001-12-03T15:01:02.000+0000"    
            |}
        """.trimMargin()

        val jsonObject = Gson().fromJson(json, JsonObject::class.java)
        DateWrapper().processJsonObject(jsonObject, false)

        val wrapped = """{
            |   _lastModifiedDateTime: "2001-12-14T15:01:02.000+0000",    
            |   createdDateTime: { $dateKey: "2001-12-01T15:01:02.000Z" },    
            |   _removedDateTime: { $dateKey: "2001-12-02T15:01:02.000Z" },    
            |   _archivedDateTime: { $dateKey: "2001-12-03T15:01:02.000Z" }
            |}
        """.trimMargin()
        val wrappedObject = Gson().fromJson(wrapped, JsonObject::class.java)
        assertEquals(wrappedObject, jsonObject)
    }


    @Test
    fun testHandlesNonUTC() {
        val dateKey = "\$date"

        val json = """{
            |   dateTime: "2001-12-01T15:01:02.000+0100"    
            |}
        """.trimMargin()

        val jsonObject = Gson().fromJson(json, JsonObject::class.java)
        DateWrapper().processJsonObject(jsonObject, false)

        val wrapped = """{
            |   dateTime: { $dateKey: "2001-12-01T14:01:02.000Z" }   
            |}
        """.trimMargin()
        val wrappedObject = Gson().fromJson(wrapped, JsonObject::class.java)
        assertEquals(wrappedObject, jsonObject)
    }

    @Test
    fun testRewrapsMongoDates() {
        val dateKey = "\$date"

        val json = """{
            |   dateTime: { $dateKey: "2001-12-01T15:01:02.000+0000" }    
            |}
        """.trimMargin()

        val jsonObject = Gson().fromJson(json, JsonObject::class.java)
        DateWrapper().processJsonObject(jsonObject, false)

        val wrapped = """{
            |   dateTime: { $dateKey: "2001-12-01T15:01:02.000Z" }   
            |}
        """.trimMargin()
        val wrappedObject = Gson().fromJson(wrapped, JsonObject::class.java)
        assertEquals(wrappedObject, jsonObject)
    }


    @Test
    fun testWrapsIdDates() {
        val dateKey = "\$date"

        val json = """{
            |   _id: {
            |       _lastModifiedDateTime: "2001-12-14T15:01:02.000+0000",    
            |       createdDateTime: "2001-12-01T15:01:02.000+0000",    
            |       _removedDateTime: "2001-12-02T15:01:02.000+0000",    
            |       _archivedDateTime: "2001-12-03T15:01:02.000+0000",
            |       someOtherDate: "1990-12-02T15:01:02.000+0000"
            |   }
            |}
        """.trimMargin()

        val jsonObject = Gson().fromJson(json, JsonObject::class.java)
        DateWrapper().processJsonObject(jsonObject, false)

        val wrapped = """{
            |   _id: {
            |       _lastModifiedDateTime: { $dateKey: "2001-12-14T15:01:02.000Z" },    
            |       createdDateTime: { $dateKey: "2001-12-01T15:01:02.000Z" },    
            |       _removedDateTime: { $dateKey: "2001-12-02T15:01:02.000Z" },    
            |       _archivedDateTime: { $dateKey: "2001-12-03T15:01:02.000Z" },
            |       someOtherDate: { $dateKey: "1990-12-02T15:01:02.000Z" }
            |   }
            |}
        """.trimMargin()
        val wrappedObject = Gson().fromJson(wrapped, JsonObject::class.java)
        assertEquals(wrappedObject, jsonObject)
    }
}
