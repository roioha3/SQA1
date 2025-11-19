package ac.il.bgu.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import ac.il.bgu.qa.errors.BookAlreadyBorrowedException;
import ac.il.bgu.qa.errors.BookNotFoundException;
import ac.il.bgu.qa.services.DatabaseService;
import ac.il.bgu.qa.services.NotificationService;
import ac.il.bgu.qa.services.ReviewService;

public class TestLibrary {

    @Mock
    private DatabaseService databaseService;

    @Mock
    private ReviewService reviewService;

    private Library library;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        library = new Library(databaseService, reviewService);
    }

    // =================================================================
    // registerUser tests
    // =================================================================

    // Helper to build a fully valid mocked user
    private User mockValidUser(String id) {
        User user = mock(User.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(user.getId()).thenReturn(id);
        when(user.getName()).thenReturn("Alice");
        when(user.getNotificationService()).thenReturn(notificationService);

        return user;
    }

    // --------------------- Happy path ---------------------

    @Test
    void registerUser_WhenUserIsValidAndNotExisting_ShouldRegisterUserInDatabase() {
        // arrange
        String userId = "123456789012";
        User user = mockValidUser(userId);

        // user does not exist yet
        when(databaseService.getUserById(userId)).thenReturn(null);

        // act
        library.registerUser(user);

        // assert
        verify(databaseService).getUserById(userId);
        verify(databaseService).registerUser(userId, user);
        verifyNoMoreInteractions(databaseService);
    }

    // --------------------- Null user ---------------------

    @Test
    void registerUser_WhenUserIsNull_ShouldThrowIllegalArgumentException() {
        // act
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(null));

        // assert
        assertEquals("Invalid user.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    // --------------------- Invalid user id ---------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "12345678901",      // 11 digits
            "1234567890123",    // 13 digits
            "12345678901a",     // contains letter
            "abcdefghijkl12",   // not digits
            "            12",   // spaces and digits
            ""                  // empty
    })
    void registerUser_WhenUserIdIsInvalidFormat_ShouldThrowIllegalArgumentException(String invalidId) {
        // arrange
        User user = mock(User.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(user.getId()).thenReturn(invalidId);
        when(user.getName()).thenReturn("Alice");                   // valid name
        when(user.getNotificationService()).thenReturn(notificationService); // valid service

        // act
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(user));

        // assert
        assertEquals("Invalid user Id.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @Test
    void registerUser_WhenUserIdIsNull_ShouldThrowIllegalArgumentException() {
        // arrange
        User user = mock(User.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(user.getId()).thenReturn(null);
        when(user.getName()).thenReturn("Alice");
        when(user.getNotificationService()).thenReturn(notificationService);

        // act
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(user));

        // assert
        assertEquals("Invalid user Id.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    // --------------------- Invalid user name ---------------------

    @ParameterizedTest
    @NullAndEmptySource
    void registerUser_WhenUserNameIsNullOrEmpty_ShouldThrowIllegalArgumentException(String invalidName) {
        // arrange
        User user = mock(User.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(user.getId()).thenReturn("123456789012");           // valid ID
        when(user.getName()).thenReturn(invalidName);            // invalid name
        when(user.getNotificationService()).thenReturn(notificationService);

        // act
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(user));

        // assert
        assertEquals("Invalid user name.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    // --------------------- Null notification service ---------------------

    @Test
    void registerUser_WhenNotificationServiceIsNull_ShouldThrowIllegalArgumentException() {
        // arrange
        User user = mock(User.class);

        when(user.getId()).thenReturn("123456789012");   // valid ID
        when(user.getName()).thenReturn("Alice");        // valid name
        when(user.getNotificationService()).thenReturn(null); // invalid service

        // act
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(user));

        // assert
        assertEquals("Invalid notification service.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    // --------------------- User already exists ---------------------

    @Test
    void registerUser_WhenUserAlreadyExists_ShouldThrowIllegalArgumentException() {
        // arrange
        String userId = "123456789012";
        User newUser = mockValidUser(userId);
        User existingUser = mockValidUser(userId);

        when(databaseService.getUserById(userId)).thenReturn(existingUser);

        // act
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(newUser));

        // assert
        assertEquals("User already exists.", ex.getMessage());
        verify(databaseService).getUserById(userId);
        verify(databaseService, never()).registerUser(anyString(), any(User.class));
    }

    // =================================================================
    // getBookByISBN tests
    // =================================================================

    private static final String VALID_ISBN = "9780306406157"; // valid ISBN-13
    private static final String VALID_USER_ID = "123456789012";

    // --------------------- Happy path ---------------------

    @Test
    void getBookByISBN_WhenInputIsValidAndBookAvailable_ShouldReturnBookAndNotifyUser() {
        // arrange
        Book book = mock(Book.class);
        when(book.isBorrowed()).thenReturn(false);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Spy to verify that notifyUserWithBookReviews is invoked
        Library spyLibrary = spy(new Library(databaseService, reviewService));
        doNothing().when(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // act
        Book result = spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // assert
        assertSame(book, result);

        verify(databaseService).getBookByISBN(VALID_ISBN);
        verifyNoMoreInteractions(databaseService);

        verify(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);
    }

    // --------------------- Invalid ISBN ---------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "",                 // empty
            "123",              // too short
            "978030640615",     // 12 digits
            "97803064061570",   // 14 digits
            "9780306406158",    // 13 digits but wrong check digit
            "abcde123456789"    // non-digits
    })
    void getBookByISBN_WhenISBNIsInvalid_ShouldThrowIllegalArgumentException(String invalidIsbn) {
        // act
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.getBookByISBN(invalidIsbn, VALID_USER_ID));

        // assert
        assertEquals("Invalid ISBN.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @Test
    void getBookByISBN_WhenISBNIsNull_ShouldThrowIllegalArgumentException() {
        // act
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.getBookByISBN(null, VALID_USER_ID));

        // assert
        assertEquals("Invalid ISBN.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    // --------------------- Invalid userId ---------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "",             // empty
            "12345678901",  // 11 digits
            "1234567890123",// 13 digits
            "12345678901a", // contains letter
            "abcdefghijkl"  // non-digits
    })
    void getBookByISBN_WhenUserIdIsInvalidFormat_ShouldThrowIllegalArgumentException(String invalidUserId) {
        // act
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.getBookByISBN(VALID_ISBN, invalidUserId));

        // assert
        assertEquals("Invalid user Id.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @Test
    void getBookByISBN_WhenUserIdIsNull_ShouldThrowIllegalArgumentException() {
        // act
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.getBookByISBN(VALID_ISBN, null));

        // assert
        assertEquals("Invalid user Id.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    // --------------------- Book not found ---------------------

    @Test
    void getBookByISBN_WhenBookDoesNotExist_ShouldThrowBookNotFoundException() {
        // arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // act
        BookNotFoundException ex =
                assertThrows(BookNotFoundException.class,
                        () -> library.getBookByISBN(VALID_ISBN, VALID_USER_ID));

        // assert
        assertEquals("Book not found!", ex.getMessage());
        verify(databaseService).getBookByISBN(VALID_ISBN);
        verifyNoMoreInteractions(databaseService);
    }

    // --------------------- Book already borrowed ---------------------

    @Test
    void getBookByISBN_WhenBookIsAlreadyBorrowed_ShouldThrowBookAlreadyBorrowedException() {
        // arrange
        Book borrowedBook = mock(Book.class);
        when(borrowedBook.isBorrowed()).thenReturn(true);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(borrowedBook);

        Library spyLibrary = spy(new Library(databaseService, reviewService));

        // act
        BookAlreadyBorrowedException ex =
                assertThrows(BookAlreadyBorrowedException.class,
                        () -> spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID));

        // assert
        assertEquals("Book was already borrowed!", ex.getMessage());

        verify(databaseService).getBookByISBN(VALID_ISBN);
        verifyNoMoreInteractions(databaseService);

        verify(spyLibrary, never())
                .notifyUserWithBookReviews(anyString(), anyString());
    }

    // --------------------- Notification failure is swallowed ---------------------

    @Test
    void getBookByISBN_WhenNotificationFails_ShouldStillReturnBook() {
        // arrange
        Book book = mock(Book.class);
        when(book.isBorrowed()).thenReturn(false);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        Library spyLibrary = spy(new Library(databaseService, reviewService));

        // simulate failure in notifyUserWithBookReviews
        doThrow(new RuntimeException("Notification failed internally"))
                .when(spyLibrary)
                .notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // act
        Book result = spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // assert: book is still returned even though notification failed
        assertSame(book, result);

        verify(databaseService).getBookByISBN(VALID_ISBN);
        verifyNoMoreInteractions(databaseService);

        verify(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);
    }
    /*************************************************************************************************/
}
