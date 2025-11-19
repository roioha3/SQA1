package ac.il.bgu.qa;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import ac.il.bgu.qa.errors.BookAlreadyBorrowedException;
import ac.il.bgu.qa.errors.BookNotFoundException;
import ac.il.bgu.qa.errors.NoReviewsFoundException;
import ac.il.bgu.qa.errors.NotificationException;
import ac.il.bgu.qa.errors.ReviewException;
import ac.il.bgu.qa.errors.ReviewServiceUnavailableException;
import ac.il.bgu.qa.errors.UserNotRegisteredException;
import ac.il.bgu.qa.services.DatabaseService;
import ac.il.bgu.qa.services.NotificationService;
import ac.il.bgu.qa.services.ReviewService;

public class TestLibrary {

    @Mock
    private DatabaseService databaseService;

    @Mock
    private ReviewService reviewService;

    private Library library;

    private static final String VALID_ISBN = "9780306406157"; // valid ISBN-13
    private static final String VALID_USER_ID = "123456789012";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        library = new Library(databaseService, reviewService);
    }

    // =================================================================
    // Helper: create fully valid mocked user
    // =================================================================

    private User mockValidUser(String id) {
        User user = mock(User.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(user.getId()).thenReturn(id);
        when(user.getName()).thenReturn("Alice");
        when(user.getNotificationService()).thenReturn(notificationService);

        return user;
    }

    // =================================================================
    // registerUser tests
    // =================================================================

    @Test
    void registerUser_WhenUserIsValidAndNotExisting_ShouldRegisterUserInDatabase() {
        // arrange
        String userId = "123456789012";
        User user = mockValidUser(userId);

        when(databaseService.getUserById(userId)).thenReturn(null);

        // act
        library.registerUser(user);

        // assert
        verify(databaseService).getUserById(userId);
        verify(databaseService).registerUser(userId, user);
        verifyNoMoreInteractions(databaseService);
    }

    @Test
    void registerUser_WhenUserIsNull_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(null));

        assertEquals("Invalid user.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

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
        User user = mock(User.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(user.getId()).thenReturn(invalidId);
        when(user.getName()).thenReturn("Alice");
        when(user.getNotificationService()).thenReturn(notificationService);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(user));

        assertEquals("Invalid user Id.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @Test
    void registerUser_WhenUserIdIsNull_ShouldThrowIllegalArgumentException() {
        User user = mock(User.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(user.getId()).thenReturn(null);
        when(user.getName()).thenReturn("Alice");
        when(user.getNotificationService()).thenReturn(notificationService);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(user));

        assertEquals("Invalid user Id.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void registerUser_WhenUserNameIsNullOrEmpty_ShouldThrowIllegalArgumentException(String invalidName) {
        User user = mock(User.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(user.getId()).thenReturn("123456789012");
        when(user.getName()).thenReturn(invalidName);
        when(user.getNotificationService()).thenReturn(notificationService);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(user));

        assertEquals("Invalid user name.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @Test
    void registerUser_WhenNotificationServiceIsNull_ShouldThrowIllegalArgumentException() {
        User user = mock(User.class);

        when(user.getId()).thenReturn("123456789012");
        when(user.getName()).thenReturn("Alice");
        when(user.getNotificationService()).thenReturn(null);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(user));

        assertEquals("Invalid notification service.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @Test
    void registerUser_WhenUserAlreadyExists_ShouldThrowIllegalArgumentException() {
        String userId = "123456789012";
        User newUser = mockValidUser(userId);
        User existingUser = mockValidUser(userId);

        when(databaseService.getUserById(userId)).thenReturn(existingUser);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.registerUser(newUser));

        assertEquals("User already exists.", ex.getMessage());
        verify(databaseService).getUserById(userId);
        verify(databaseService, never()).registerUser(anyString(), any(User.class));
    }

    // =================================================================
    // getBookByISBN tests
    // =================================================================

    @Test
    void getBookByISBN_WhenInputIsValidAndBookAvailable_ShouldReturnBookAndNotifyUser() {
        Book book = mock(Book.class);
        when(book.isBorrowed()).thenReturn(false);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        Library spyLibrary = spy(new Library(databaseService, reviewService));
        doNothing().when(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        Book result = spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        assertSame(book, result);

        verify(databaseService).getBookByISBN(VALID_ISBN);
        verifyNoMoreInteractions(databaseService);

        verify(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);
    }

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
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.getBookByISBN(invalidIsbn, VALID_USER_ID));

        assertEquals("Invalid ISBN.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @Test
    void getBookByISBN_WhenISBNIsNull_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.getBookByISBN(null, VALID_USER_ID));

        assertEquals("Invalid ISBN.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",             // empty
            "12345678901",  // 11 digits
            "1234567890123",// 13 digits
            "12345678901a", // contains letter
            "abcdefghijkl"  // non-digits
    })
    void getBookByISBN_WhenUserIdIsInvalidFormat_ShouldThrowIllegalArgumentException(String invalidUserId) {
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class,
                    () -> library.getBookByISBN(VALID_ISBN, invalidUserId));

        assertEquals("Invalid user Id.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @Test
    void getBookByISBN_WhenUserIdIsNull_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.getBookByISBN(VALID_ISBN, null));

        assertEquals("Invalid user Id.", ex.getMessage());
        verifyNoInteractions(databaseService);
    }

    @Test
    void getBookByISBN_WhenBookDoesNotExist_ShouldThrowBookNotFoundException() {
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        BookNotFoundException ex =
                assertThrows(BookNotFoundException.class,
                        () -> library.getBookByISBN(VALID_ISBN, VALID_USER_ID));

        assertEquals("Book not found!", ex.getMessage());
        verify(databaseService).getBookByISBN(VALID_ISBN);
        verifyNoMoreInteractions(databaseService);
    }

    @Test
    void getBookByISBN_WhenBookIsAlreadyBorrowed_ShouldThrowBookAlreadyBorrowedException() {
        Book borrowedBook = mock(Book.class);
        when(borrowedBook.isBorrowed()).thenReturn(true);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(borrowedBook);

        Library spyLibrary = spy(new Library(databaseService, reviewService));

        BookAlreadyBorrowedException ex =
                assertThrows(BookAlreadyBorrowedException.class,
                        () -> spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID));

        assertEquals("Book was already borrowed!", ex.getMessage());

        verify(databaseService).getBookByISBN(VALID_ISBN);
        verifyNoMoreInteractions(databaseService);

        verify(spyLibrary, never())
                .notifyUserWithBookReviews(anyString(), anyString());
    }

    @Test
    void getBookByISBN_WhenNotificationFails_ShouldStillReturnBook() {
        Book book = mock(Book.class);
        when(book.isBorrowed()).thenReturn(false);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        Library spyLibrary = spy(new Library(databaseService, reviewService));

        doThrow(new RuntimeException("Notification failed internally"))
                .when(spyLibrary)
                .notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        Book result = spyLibrary.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        assertSame(book, result);

        verify(databaseService).getBookByISBN(VALID_ISBN);
        verifyNoMoreInteractions(databaseService);

        verify(spyLibrary).notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);
    }

    // =================================================================
    // notifyUserWithBookReviews tests
    // =================================================================

    @Test
    void notifyUserWithBookReviews_WhenAllValid_ShouldSendReviewsToUser() {
        String isbn = VALID_ISBN;
        String userId = VALID_USER_ID;

        Book book = mock(Book.class);
        when(book.getTitle()).thenReturn("Some Book");
        User user = mock(User.class);

        when(databaseService.getBookByISBN(isbn)).thenReturn(book);
        when(databaseService.getUserById(userId)).thenReturn(user);

        List<String> reviews = Arrays.asList("Great book", "Loved it");
        when(reviewService.getReviewsForBook(isbn)).thenReturn(reviews);

        // act
        library.notifyUserWithBookReviews(isbn, userId);

        // assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(user).sendNotification(messageCaptor.capture());

        String msg = messageCaptor.getValue();
        assertTrue(msg.contains("Reviews for 'Some Book':"));
        assertTrue(msg.contains("Great book"));
        assertTrue(msg.contains("Loved it"));

        verify(reviewService).getReviewsForBook(isbn);
        verify(reviewService).close(); // must be closed in finally
    }

    @Test
    void notifyUserWithBookReviews_WhenISBNIsInvalid_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.notifyUserWithBookReviews("123", VALID_USER_ID));

        assertEquals("Invalid ISBN.", ex.getMessage());
        verifyNoInteractions(databaseService);
        verifyNoInteractions(reviewService);
    }

    @Test
    void notifyUserWithBookReviews_WhenUserIdIsInvalid_ShouldThrowIllegalArgumentException() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> library.notifyUserWithBookReviews(VALID_ISBN, "123"));

        assertEquals("Invalid user Id.", ex.getMessage());
        verifyNoInteractions(databaseService);
        verifyNoInteractions(reviewService);
    }

    @Test
    void notifyUserWithBookReviews_WhenBookNotFound_ShouldThrowBookNotFoundException() {
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        BookNotFoundException ex =
                assertThrows(BookNotFoundException.class,
                        () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        assertEquals("Book not found!", ex.getMessage());
        verify(databaseService).getBookByISBN(VALID_ISBN);
        verify(databaseService, never()).getUserById(anyString());
        verifyNoInteractions(reviewService);
    }

    @Test
    void notifyUserWithBookReviews_WhenUserNotRegistered_ShouldThrowUserNotRegisteredException() {
        Book book = mock(Book.class);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(null);

        UserNotRegisteredException ex =
                assertThrows(UserNotRegisteredException.class,
                        () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        assertEquals("User not found!", ex.getMessage());
        verify(databaseService).getBookByISBN(VALID_ISBN);
        verify(databaseService).getUserById(VALID_USER_ID);
        verifyNoInteractions(reviewService);
    }

    @Test
    void notifyUserWithBookReviews_WhenNoReviewsFound_ShouldThrowNoReviewsFoundException() {
        Book book = mock(Book.class);
        User user = mock(User.class);

        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Arrays.asList());

        NoReviewsFoundException ex =
                assertThrows(NoReviewsFoundException.class,
                        () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        assertEquals("No reviews found!", ex.getMessage());
        verify(reviewService).getReviewsForBook(VALID_ISBN);
        verify(reviewService).close();
    }

    @Test
    void notifyUserWithBookReviews_WhenReviewServiceThrowsReviewException_ShouldThrowReviewServiceUnavailableException() {
        Book book = mock(Book.class);
        User user = mock(User.class);

        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN))
                .thenThrow(new ReviewException("Backend down"));

        ReviewServiceUnavailableException ex =
                assertThrows(ReviewServiceUnavailableException.class,
                        () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        assertEquals("Review service unavailable!", ex.getMessage());
        verify(reviewService).getReviewsForBook(VALID_ISBN);
        verify(reviewService).close();
    }

    @Test
    void notifyUserWithBookReviews_WhenNotificationFailsFiveTimes_ShouldThrowNotificationException() {
        Book book = mock(Book.class);
        User user = mock(User.class);

        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        List<String> reviews = Arrays.asList("Nice");
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(reviews);

        doThrow(new NotificationException("fail"))
                .when(user)
                .sendNotification(anyString());

        NotificationException ex =
                assertThrows(NotificationException.class,
                        () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));

        assertEquals("Notification failed!", ex.getMessage());
        verify(user, times(5)).sendNotification(anyString());
        verify(reviewService).close();
    }

    // =================================================================
    // isISBNValid tests (via reflection)
    // =================================================================

    private boolean callIsbnValid(String isbn) {
        try {
            Method m = Library.class.getDeclaredMethod("isISBNValid", String.class);
            m.setAccessible(true);
            return (boolean) m.invoke(library, isbn);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void isISBNValid_WhenIsbnIsNull_ShouldReturnFalse() {
        assertFalse(callIsbnValid(null));
    }

    @Test
    void isISBNValid_WhenIsbnIsValid13Digits_ShouldReturnTrue() {
        assertTrue(callIsbnValid("9780306406157"));
    }

    @Test
    void isISBNValid_WhenIsbnContainsHyphensAndIsValid_ShouldReturnTrue() {
        assertTrue(callIsbnValid("978-0-306-40615-7"));
    }

    @Test
    void isISBNValid_WhenIsbnWrongLength_ShouldReturnFalse() {
        assertFalse(callIsbnValid("978030640615"));   // 12 digits
        assertFalse(callIsbnValid("97803064061570")); // 14 digits
    }

    @Test
    void isISBNValid_WhenIsbnHasNonDigits_ShouldReturnFalse() {
        assertFalse(callIsbnValid("978030640615a"));
        assertFalse(callIsbnValid("abcdefghijklm"));
    }

    @Test
    void isISBNValid_WhenIsbnHasWrongChecksum_ShouldReturnFalse() {
        assertFalse(callIsbnValid("9780306406158")); // last digit changed
    }
    /*****************************************************************************************************/
}
