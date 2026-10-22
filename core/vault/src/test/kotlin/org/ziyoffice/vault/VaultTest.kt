package org.ziyoffice.vault

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The vault: what it keeps, and what it refuses to give back.
 */
class VaultTest {

    private val folder: File = Files.createTempDirectory("ziy-vault").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun aFileGoesInAndComesOut() {
        val vault = VaultStore(folder).create("correct horse")
        val bytes = "a document".encodeToByteArray()
        vault.put("notes.txt", bytes)
        val entry = vault.list().single()
        assertEquals("notes.txt", entry.name)
        assertEquals(bytes.size, entry.size)
        assertTrue(vault.get(entry.id)?.contentEquals(bytes) == true)
    }

    @Test
    fun theBytesOnDiskAreNotTheBytesThatWentIn() {
        val vault = VaultStore(folder).create("correct horse")
        vault.put("notes.txt", "a document".encodeToByteArray())
        val onDisk = folder.walkTopDown()
            .filter { it.isFile }
            .filter { it.name.endsWith(".enc") }
            .first()
            .readBytes()
        assertTrue(
            !onDisk.toString(Charsets.ISO_8859_1).contains("a document"),
            "the text should not be readable on the disk",
        )
    }

    @Test
    fun theNamesAreNotReadableOnTheDisk() {
        val vault = VaultStore(folder).create("correct horse")
        vault.put("my tax return", "secret".encodeToByteArray())
        val index = File(folder, Vault.INDEX_NAME).readBytes()
        assertTrue(
            !index.toString(Charsets.ISO_8859_1).contains("my tax return"),
            "the name should not be readable on the disk",
        )
    }

    @Test
    fun theWrongPassphraseOpensNothing() {
        val store = VaultStore(folder)
        store.create("correct horse").put("notes.txt", "a document".encodeToByteArray())
        assertNull(store.open("wrong horse"))
    }

    @Test
    fun theRightPassphraseOpensTheVault() {
        val store = VaultStore(folder)
        store.create("correct horse").put("notes.txt", "a document".encodeToByteArray())
        val vault = assertNotNull(store.open("correct horse"))
        assertEquals(1, vault.list().size)
    }

    @Test
    fun aVaultThatIsNotThereCannotBeOpened() {
        assertNull(VaultStore(folder).open("correct horse"))
    }

    @Test
    fun aFileIsTakenOutOfTheVault() {
        val vault = VaultStore(folder).create("correct horse")
        vault.put("notes.txt", "a document".encodeToByteArray())
        val entry = vault.list().single()
        vault.delete(entry.id)
        assertTrue(vault.list().isEmpty())
        assertNull(vault.get(entry.id))
    }

    @Test
    fun aFileCanBeGivenAnotherName() {
        val vault = VaultStore(folder).create("correct horse")
        val entry = vault.put("notes.txt", "a document".encodeToByteArray())
        vault.rename(entry.id, "renamed.txt")
        assertEquals("renamed.txt", vault.list().single().name)
    }

    @Test
    fun twoFilesWithTheSameNameAreBothKept() {
        val vault = VaultStore(folder).create("correct horse")
        vault.put("notes.txt", "one".encodeToByteArray())
        vault.put("notes.txt", "two".encodeToByteArray())
        assertEquals(2, vault.list().size)
    }

    @Test
    fun aNameWithASpaceSurvives() {
        val vault = VaultStore(folder).create("correct horse")
        vault.put("my notes 2026", "a document".encodeToByteArray())
        assertEquals("my notes 2026", vault.list().single().name)
    }

    @Test
    fun theFilesAreListedInOrder() {
        val vault = VaultStore(folder).create("correct horse")
        vault.put("zebra.txt", "z".encodeToByteArray())
        vault.put("apple.txt", "a".encodeToByteArray())
        assertEquals(listOf("apple.txt", "zebra.txt"), vault.list().map { it.name })
    }

    @Test
    fun theVaultSurvivesBeingReopened() {
        val store = VaultStore(folder)
        store.create("correct horse").put("notes.txt", "a document".encodeToByteArray())
        val reopened = assertNotNull(store.open("correct horse"))
        val entry = reopened.list().single()
        assertEquals("a document", reopened.get(entry.id)?.decodeToString())
    }

    @Test
    fun theSameFileIsNeverEncryptedTheSameWayTwice() {
        val vault = VaultStore(folder).create("correct horse")
        val bytes = "the same bytes".encodeToByteArray()
        vault.put("one.txt", bytes)
        vault.put("two.txt", bytes)
        val encrypted = folder.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".enc") }
            .map { it.readBytes() }
            .toList()
        assertEquals(2, encrypted.size)
        assertTrue(!encrypted[0].contentEquals(encrypted[1]), "the two should differ")
    }
}
