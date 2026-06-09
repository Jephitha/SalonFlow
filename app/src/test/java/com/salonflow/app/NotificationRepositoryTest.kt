package com.salonflow.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationRepositoryTest {

    private val repo: NotificationRepository
        get() = NotificationRepository.getInstance(RuntimeEnvironment.getApplication())

    @Test
    fun add_and_findAll() {
        repo.clearAll()
        val id = repo.add("test", "Title", "Message body")
        assertTrue("id should be positive", id > 0)
        val all = repo.findAll()
        assertEquals(1, all.size)
        assertEquals(id, all[0].id)
        assertEquals("Title", all[0].title)
    }

    @Test
    fun unreadCount() {
        repo.clearAll()
        repo.add("test", "One", "Unread")
        repo.add("test", "Two", "Unread")
        val id3 = repo.add("test", "Three", "Unread")
        repo.markAsRead(id3)
        assertEquals(2, repo.unreadCount())
    }

    @Test
    fun markAsRead() {
        repo.clearAll()
        val id = repo.add("test", "Mark", "Read this")
        repo.markAsRead(id)
        val entry = repo.findAll().find { it.id == id }
        assertNotNull(entry)
        assertTrue(entry!!.read)
    }
}
