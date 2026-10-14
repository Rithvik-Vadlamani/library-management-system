package library.server;

import library.model.Item;
import library.model.Member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import com.google.gson.Gson;

public class LibraryTest {

    @TempDir
    Path tempDir;

    private Library library;

    @BeforeEach
    void setUp() throws IOException {
        Item book = new Item();
        book.id = "b-001";
        book.kind = "book";
        book.title = "Test Book";
        book.creator = "Test Author";
        book.summary = "A test";
        book.year = 2024;
        book.totalCopies = 2;
        book.availableCopies = 2;
        book.tags.add("fiction");
        book.pages = 300;
        book.isbn = "123";

        Item game = new Item();
        game.id = "g-001";
        game.kind = "game";
        game.title = "Test Game";
        game.creator = "Studio";
        game.summary = "A game";
        game.year = 2020;
        game.totalCopies = 1;
        game.availableCopies = 0;
        game.tags.add("indie");
        game.platform = "PC";
        game.esrbRating = "E";

        // Serialize to the catalog file.
        File catalogFile = tempDir.resolve("catalog.json").toFile();
        try (FileWriter w = new FileWriter(catalogFile)) {
            new Gson().toJson(java.util.Arrays.asList(book, game), w);
        }
        library = new Library(tempDir.toString());
    }
    @Test
    void registerCreatesNewMember() {
        Member m = library.register("alice", "password123");
        assertNotNull(m);
        assertEquals("alice", m.username);
        assertEquals("MEMBER", m.role);
        assertNotEquals("password123", m.passwordHash);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        library.register("alice", "password123");
        assertNull(library.register("alice", "different"));
    }

    @Test
    void registerRejectsBlankAndNullInputs() {
        assertNull(library.register("", "password123"));
        assertNull(library.register(null, "password123"));
        assertNull(library.register("alice", null));
    }

    @Test
    void registerHashesAreUniqueForSamePassword() {
        Member a = library.register("alice", "samepw");
        Member b = library.register("bob", "samepw");
        assertNotEquals(a.passwordHash, b.passwordHash);
        assertNotEquals(a.salt, b.salt);
    }

    @Test
    void loginWorksWithCorrectPasswordAndIsCaseInsensitive() {
        library.register("Alice", "secret");
        assertNotNull(library.login("alice", "secret"));
        assertNotNull(library.login("ALICE", "secret"));
    }

    @Test
    void loginFailsWithWrongPasswordOrUnknownUser() {
        library.register("alice", "secret");
        assertNull(library.login("alice", "wrong"));
        assertNull(library.login("nobody", "secret"));
    }

    @Test
    void borrowDecrementsAvailableCopies() {
        Member m = library.register("alice", "secret");
        assertNull(library.borrow(m, "b-001"));
        assertEquals(1, findItem("b-001").availableCopies);
        assertEquals(1, m.loans.size());
    }

    @Test
    void cannotBorrowSameItemTwice() {
        Member m = library.register("alice", "secret");
        library.borrow(m, "b-001");
        String err = library.borrow(m, "b-001");
        assertTrue(err.contains("already"));
    }

    @Test
    void cannotBorrowWhenNoCopiesAvailable() {
        Member alice = library.register("alice", "secret");
        Member bob = library.register("bob", "secret");
        Member charlie = library.register("charlie", "secret");
        library.borrow(alice, "b-001");
        library.borrow(bob, "b-001");
        String err = library.borrow(charlie, "b-001");
        assertTrue(err.contains("No copies available"));
    }

    @Test
    void returnIncrementsAvailableCopies() {
        Member m = library.register("alice", "secret");
        library.borrow(m, "b-001");
        assertNull(library.returnItem(m, "b-001"));
        assertEquals(2, findItem("b-001").availableCopies);
        assertEquals(0, m.loans.size());
    }

    @Test
    void cannotReturnItemYouDontHave() {
        Member m = library.register("alice", "secret");
        String err = library.returnItem(m, "b-001");
        assertTrue(err.contains("don't have"));
    }

    @Test
    void cannotPlaceHoldOnAvailableItem() {
        Member m = library.register("alice", "secret");
        String err = library.placeHold(m, "b-001");
        assertTrue(err.contains("just borrow"));
    }

    @Test
    void holdBecomesReadyWhenCopyIsReturned() {
        Member alice = library.register("alice", "secret");
        Member bob = library.register("bob", "secret");
        Member charlie = library.register("charlie", "secret");

        library.borrow(alice, "b-001");
        library.borrow(bob, "b-001");
        library.placeHold(charlie, "b-001");
        assertFalse(charlie.holds.get(0).ready);

        library.returnItem(alice, "b-001");
        assertTrue(charlie.holds.get(0).ready);
        // Copy is reserved for charlie, so availableCopies stays 0.
        assertEquals(0, findItem("b-001").availableCopies);
    }

    @Test
    void reservedHolderCanBorrowEvenWhenNoCopiesAvailable() {
        Member alice = library.register("alice", "secret");
        Member bob = library.register("bob", "secret");
        Member charlie = library.register("charlie", "secret");

        library.borrow(alice, "b-001");
        library.borrow(bob, "b-001");
        library.placeHold(charlie, "b-001");
        library.returnItem(alice, "b-001");

        assertEquals(0, findItem("b-001").availableCopies);
        assertNull(library.borrow(charlie, "b-001"));
        assertEquals(0, charlie.holds.size());
    }

    @Test
    void cancelHoldRemovesIt() {
        Member m = library.register("alice", "secret");
        library.placeHold(m, "g-001");
        assertNull(library.cancelHold(m, "g-001"));
        assertEquals(0, m.holds.size());
    }

    @Test
    void searchFindsItemsByTitleCaseInsensitive() {
        assertEquals(1, library.searchCatalog("TEST BOOK", null, null, null).size());
        assertEquals(0, library.searchCatalog("nonexistent", null, null, null).size());
    }

    @Test
    void searchFiltersByKindAndTag() {
        assertEquals(1, library.searchCatalog("", "book", null, null).size());
        assertEquals(1, library.searchCatalog("", null, "fiction", null).size());
        assertEquals(0, library.searchCatalog("", null, "missing-tag", null).size());
    }

    @Test
    void reviewIsAddedAndRejectsBadRating() {
        Member m = library.register("alice", "secret");
        assertNull(library.addReview(m, "b-001", 5, "Great"));
        assertEquals(5, findItem("b-001").reviews.get(0).rating);
        assertNotNull(library.addReview(m, "b-001", 0, "bad"));
        assertNotNull(library.addReview(m, "b-001", 6, "bad"));
    }

    @Test
    void resetPasswordWorksWithCorrectOldPassword() {
        Member m = library.register("alice", "old");
        assertNull(library.resetPassword(m, "old", "new1234"));
        assertNull(library.login("alice", "old"));
        assertNotNull(library.login("alice", "new1234"));
    }

    @Test
    void changesPersistAcrossLibraryRestart() {
        Member alice = library.register("alice", "secret");
        library.borrow(alice, "b-001");
        library.addReview(alice, "g-001", 4, "good");

        Library reloaded = new Library(tempDir.toString());
        Member sameAlice = reloaded.login("alice", "secret");
        assertNotNull(sameAlice);
        assertEquals(1, sameAlice.loans.size());

        Item bookAfter = null;
        Item gameAfter = null;
        for (Item it : reloaded.searchCatalog(null, null, null, null)) {
            if (it.id.equals("b-001")) {
                bookAfter = it;
            }
            else if (it.id.equals("g-001")) {
                gameAfter = it;
            }
        }
        assertEquals(1, bookAfter.availableCopies);
        assertEquals(1, gameAfter.reviews.size());
    }

    private Item findItem(String id) {
        for (Item it : library.searchCatalog(null, null, null, null)) {
            if (it.id.equals(id)) {
                return it;
            }
        }
        return null;
    }
}