package com.example

import com.example.data.reader.UrlCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderExtractorTest {

    @Test
    fun urlCleaner_stripsFbclidAndExtractsUrlFromSharedText() {
        val raw = "খবরটি পড়ুন https://www.ittefaq.com.bd/821351?fbclid=IwZXh0bgNhZW0CMTEAcGRvZgNzcnRjBmFwcF9pZA8yNzUyNTQ2OTI1OTgyNzkAAR-jz2sp1vfFavCdiUFbCcyjoIR5cPV4QaW5DMFfDHRAOnFi2o8eDSL7wiX0Aw_aem_Jb3fG43fJ0Uqdu6dHSrQCA&utm_source=facebook"
        val result = UrlCleaner.extractAndClean(raw)
        assertNotNull(result)
        assertEquals("https://www.ittefaq.com.bd/821351", result?.cleanUrl)
        assertEquals("ittefaq.com.bd", result?.domain)
        assertEquals(2, result?.strippedTrackersCount)
    }

    @Test
    fun urlCleaner_keepsNonTrackingQueryParams() {
        val raw = "https://news.example.com/article?id=42&utm_medium=social&gclid=abc123"
        val result = UrlCleaner.extractAndClean(raw)
        assertNotNull(result)
        assertEquals("https://news.example.com/article?id=42", result?.cleanUrl)
        assertEquals(2, result?.strippedTrackersCount)
    }

    @Test
    fun urlCleaner_rejectsBlankOrNonUrlText() {
        assertNull(UrlCleaner.extractAndClean("   "))
        assertNull(UrlCleaner.extractAndClean("just some random words"))
        assertTrue(UrlCleaner.extractAndClean("ittefaq.com.bd/821351")?.cleanUrl == "https://ittefaq.com.bd/821351")
    }
}
