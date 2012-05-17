package org.geometerplus.android.fbreader.benetech;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.geometerplus.fbreader.fbreader.FBReaderApp;
import org.geometerplus.fbreader.fbreader.FBView;
import org.geometerplus.fbreader.library.Book;
import org.geometerplus.fbreader.library.Bookmark;
import org.geometerplus.zlibrary.text.model.ZLTextModel;
import org.geometerplus.zlibrary.text.view.ZLTextParagraphCursor;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.text.view.ZLTextWordCursor;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupManager;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

/** Creates a back up service connection (for API Levels >= FROYO) that keeps up with the 
 * last read page of any book that the user is reading. The class heavily relies on the 
 * FBReader API's ZLTextParagraphCursor. @see {@link android.app.backup.BackupAgent}
 * */
public final class LastReadPageOfCurrentBook  extends BackupAgentHelper {
	private static final String LAST_PAGE_PREFS = "last_page_preferences";
	private static final String BACKUP_SERVICE_KEY = "AEdPqrEAAAAIqZUzcWMpHB8rbjYiGM7eUtke3U-QPrYBhSSh4w";
	private static Method BackupManager_dataChanged;

	
	static {
		try {
			BackupManager_dataChanged = BackupManager.class.getMethod("dataChanged");
		} catch (NoSuchMethodException nsme) {
			Log.e(BackupAgentHelper.class.getSimpleName(),"unexpected " + nsme);
		}
	}
	
	/**  Activated when the last page of the current book is saved (via BackupManager(Context).dataChanged()).
	 * @see {@link android.app.backup.BackupAgent}
	 * @see http://developer.android.com/guide/topics/data/backup.html
	 * */
	public void onCreate() {
		/*  
		 * DURING A BACK UP OPERATION THE BACK UP MANAGER DELIVERS ALL THE 
		 * PAGE PREFERENCE CONTENT TO A BACK UP TRANSPORT, WHICH THEN DELIVERS 
		 * THE DATA TO GOOGLE'S CLOUD BACK UP RESOURCES (FOR DEVICES >= FROYO).
		 * 
		 * REFERENCED FROM: http://developer.android.com/guide/topics/data/backup.html
		 */
		addHelper(BACKUP_SERVICE_KEY, new SharedPreferencesBackupHelper(this, LAST_PAGE_PREFS));
	}

	private static void InvokeIf_dataChanged_IsAvailable(BackupManager bm) {
		try {
			if (BackupManager_dataChanged != null) {
				BackupManager_dataChanged.invoke(bm);
			}
		} catch (IllegalAccessException ie) {
			Log.e(BackupAgentHelper.class.getSimpleName(),"unexpected " + ie);
		} catch (IllegalArgumentException e) {
			Log.e(BackupAgentHelper.class.getSimpleName(),"unexpected " + e);
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			Log.e(BackupAgentHelper.class.getSimpleName(),"unexpected " + e);
			e.printStackTrace();
		}
	}
	
	/**
	 * Where the last page of the current book is saved (referenced with a bookId).
	 * 
	 * @see {@link org.geometerplus.fbreader.library.Book}
	 * @see #org.geometerplus.fbreader.library.Book.getId()
	 **/
	public static void saveLocationOfLastReadPage(Context zla) {

		// RETRIEVE FBReaderApp RESOURCES AND PREFERENCES EDITOR
		final FBReaderApp fbReader = (FBReaderApp) FBReaderApp.Instance();
		final Editor ed = zla.getSharedPreferences(LAST_PAGE_PREFS,
				Context.MODE_PRIVATE).edit();

		// RETRIEVE POSITION, PARAGRAPH INDEX AND BOOK ID
		final ZLTextPosition zlp = fbReader.Model.Book.getStoredPosition();
		final int paragraphIndex = zlp.getParagraphIndex();
		final long bookId = fbReader.Model.Book.getId();

		// COMMIT PARAGRAPH INDEX INTO PREFERENCES UNDER BOOK ID
		ed.putLong("lastBook", bookId);
		ed.putInt("" + bookId, paragraphIndex).commit();

		final int currentAPIVersion = android.os.Build.VERSION.SDK_INT;
		if (currentAPIVersion >= android.os.Build.VERSION_CODES.FROYO){
			
			// ACTIVATE BackupManager TO DELIVER LAST PAGE PREFERENCES
			InvokeIf_dataChanged_IsAvailable(new BackupManager(zla));
		} 
	}

	/**
	 * The last page of the current book is loaded (using a bookId).
	 * 
	 * @see {@link org.geometerplus.fbreader.library.Book}
	 * @see #org.geometerplus.fbreader.library.Book.getId()
	 * */
	public static void loadLocationOfLastReadPage(Context zla) {

		// RETRIEVE APPLICATION FBReaderApp RESOURCES AND PREFERENCES
		final FBReaderApp fbReader = (FBReaderApp) FBReaderApp.Instance();
		final SharedPreferences sp = zla.getSharedPreferences(LAST_PAGE_PREFS,Context.MODE_PRIVATE);

		// RETRIEVE BOOK
		final long lastBook = sp.getLong("lastBook", -1);
		Book book = Book.getById(lastBook);

		// RETRIEVE BOOK MODEL
		final FBView view = fbReader.getTextView();
		final ZLTextModel model = view.getModel();

		if (book == null && fbReader.Model != null)
			book = fbReader.Model.Book;

		if (book == null) return; // TODO SET UP THE BOOK HERE IF NEEDED

		// RETRIEVE PARAGRAPH INDEX
		final long bookId = book.getId();
		final int paragraphIndex = sp.getInt("" + bookId, -1);

		// VALIDATE NEEDED DATA IS AVAILABLE
		if (model == null || paragraphIndex == -1) return;

		// RETRIEVE PARAGRAPH/WORD CURSORS FOR LAST PAGE POSITION
		final ZLTextParagraphCursor parag = ZLTextParagraphCursor.cursor(model,paragraphIndex);
		final ZLTextWordCursor cursor = new ZLTextWordCursor(parag);

		if (cursor.isNull()) return;

		// RETRIEVE BOOK AND OPEN TO ITS LAST PAGE POSITION
		fbReader.openBook(book, new Bookmark(fbReader.Model.Book, 
			view.getModel().getId(), cursor, 0, false));
	}
	
	
}
