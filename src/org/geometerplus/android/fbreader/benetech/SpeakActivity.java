/*
 * Copyright (C) 2009-2011 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.benetech;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;

import org.accessibility.SimpleGestureFilter;
import org.accessibility.VoiceableDialog;
import org.benetech.android.R;
import org.geometerplus.android.fbreader.TOCActivity;
import org.geometerplus.android.fbreader.api.ApiServerImplementation;
import org.geometerplus.android.fbreader.api.TextPosition;
import org.geometerplus.fbreader.fbreader.FBReaderApp;
import org.geometerplus.zlibrary.core.application.ZLApplication;
import org.geometerplus.zlibrary.text.view.ZLTextElement;
import org.geometerplus.zlibrary.text.view.ZLTextWord;
import org.geometerplus.zlibrary.text.view.ZLTextWordCursor;

public class SpeakActivity extends Activity implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener, SimpleGestureFilter.SimpleGestureListener  {
    private ApiServerImplementation myApi;

	private TextToSpeech myTTS;

	private int myParagraphIndex = -1;
	private int myParagraphsNumber;

	private boolean myIsActive = false;

    private static final int PLAY_AFTER_TOC = 1;
    private static final int CHECK_TTS_INSTALLED = 0;
    public static final int SPEAK_BACK_PRESSED = 77;
    
    private SimpleGestureFilter detector;
    private Vibrator myVib;
    private int lastSentence = 0;
    private int lastSpoken = 0;
    private boolean justPaused = false;
    private boolean resumePlaying = false;
    private boolean abortedTOCReturn = false;
    private Iterator<String> sentenceListIterator;
    private ArrayList<Integer> wordIndexList;

    //Added for the detecting whether the talkback is on
    private AccessibilityManager accessibilityManager;

    private static final long[] VIBE_PATTERN = {
        0, 10, 70, 80
    };
    public static final String CONTENTS_EARCON = "[CONTENTS]";
    public static final String MENU_EARCON = "[MENU]";
    public static final String FORWARD_EARCON = "[FORWARD]";
    public static final String BACK_EARCON = "[BACK]";
    public static final String START_READING_EARCON = "[START]";

    private static Method MotionEvent_getX;
    private static Method MotionEvent_getY;
    private static Method AccessibilityManager_isTouchExplorationEnabled;

    static {
        initCompatibility();
    }

    private static void initCompatibility() {
        try {
            MotionEvent_getX = MotionEvent.class.getMethod("getX", new Class[] { Integer.TYPE });
            MotionEvent_getY = MotionEvent.class.getMethod("getY", new Class[] { Integer.TYPE });
            AccessibilityManager_isTouchExplorationEnabled = AccessibilityManager.class.getMethod(
                    "isTouchExplorationEnabled");
            /* success, this is a newer device */
        } catch (NoSuchMethodException nsme) {
            /* failure, must be older device */
        }
    }

    private static boolean isTouchExplorationEnabled(AccessibilityManager am) {
        try {
            if (AccessibilityManager_isTouchExplorationEnabled != null) {
                Object retobj = AccessibilityManager_isTouchExplorationEnabled.invoke(am);
                return (Boolean) retobj;
            }
        } catch (IllegalAccessException ie) {
            System.err.println("unexpected " + ie);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    private void setListener(int id, View.OnClickListener listener) {
		findViewById(id).setOnClickListener(listener);
	}
    
    private void setTouchFocusEnabled(int id) {
        findViewById(id).setFocusableInTouchMode(true);
    }

    private class MyHoverListener implements View.OnHoverListener {

        @Override
        public boolean onHover(View view, MotionEvent motionEvent) {
            stopTalking();
            justPaused = true;
            return false;
        }
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        WindowManager.LayoutParams params =
        getWindow().getAttributes();
        params.gravity = Gravity.BOTTOM;
        this.getWindow().setAttributes(params);
        this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        
        detector = new SimpleGestureFilter(this,this);
        myVib = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
        accessibilityManager =
            (AccessibilityManager) getApplicationContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isEnabled()) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        setContentView(R.layout.view_spokentext);

        if (isTouchExplorationEnabled(accessibilityManager)) {
            findViewById(R.id.speak_menu_back).setOnHoverListener(new MyHoverListener());
            findViewById(R.id.speak_menu_forward).setOnHoverListener(new MyHoverListener());
            findViewById(R.id.speak_menu_pause).setOnHoverListener(new MyHoverListener());
            findViewById(R.id.speak_menu_contents).setOnHoverListener(new MyHoverListener());
        }

		setListener(R.id.speak_menu_back, new View.OnClickListener() {
			public void onClick(View v) {
                goBackward();
			}
		});
        findViewById(R.id.speak_menu_back).setOnFocusChangeListener(
            new View.OnFocusChangeListener() {
                public void onFocusChange(android.view.View view, boolean b) {
                    if (b) {
                        stopTalking();
                        justPaused = true;
                    }
                }
            }
        );
		setListener(R.id.speak_menu_forward, new View.OnClickListener() {
			public void onClick(View v) {
                goForward();
			}
		});
        findViewById(R.id.speak_menu_forward).setOnFocusChangeListener(
            new View.OnFocusChangeListener() {
                public void onFocusChange(android.view.View view, boolean b) {
                    if (b) {
                        stopTalking();
                        justPaused = true;
                    }
                }
            }
        );
/*		setListener(R.id.button_close, new View.OnClickListener() {
			public void onClick(View v) {
				stopTalking();
				finish();
			}
		});*/
		setListener(R.id.speak_menu_pause, new View.OnClickListener() {
			public void onClick(View v) {
                playOrPause();
            }
		});
        setListener(R.id.speak_menu_contents, new View.OnClickListener() {
            public void onClick(View v) {
                showContents();
            }
        });
        findViewById(R.id.speak_menu_contents).setOnFocusChangeListener(
            new View.OnFocusChangeListener() {
                public void onFocusChange(android.view.View view, boolean b) {
                    if (b) {
                        stopTalking();
                        justPaused = true;
                    }
                }
            }
        );

		((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).listen(
			new PhoneStateListener() {
				public void onCallStateChanged(int state, String incomingNumber) {
					if (state == TelephonyManager.CALL_STATE_RINGING) {
						stopTalking();
					}
				}
			},
			PhoneStateListener.LISTEN_CALL_STATE
		);

		setActive(false);
		setActionsEnabled(false);

		myApi = new ApiServerImplementation();
		try {
			startActivityForResult(
				new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA), CHECK_TTS_INSTALLED
			);
		} catch (ActivityNotFoundException e) {
			showErrorMessage(getText(R.string.no_tts_installed), true);
		}

        if (!accessibilityManager.isEnabled()) {
		    setTitle(R.string.initializing);
        }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHECK_TTS_INSTALLED) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                myTTS = new TextToSpeech(this, this);
            } else {
                startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
            }
        } else {
            if (resultCode == TOCActivity.BACK_PRESSED) {
                abortedTOCReturn = true;
            } else {
                justPaused = false;
                resumePlaying = true;
            }
        }
	}

	@Override
	protected void onResume() {
		super.onResume();
        findViewById(R.id.speak_menu_pause).requestFocus();

        if (! abortedTOCReturn) {
            setCurrentLocation();
        }
        abortedTOCReturn = false;

        if (resumePlaying || justPaused) {
            resumePlaying = false;
            myTTS.playEarcon(START_READING_EARCON, TextToSpeech.QUEUE_ADD, null);
            speakParagraph(getNextParagraph());
        }
	}

    private void setCurrentLocation() {
        myParagraphIndex = myApi.getPageStart().ParagraphIndex;
        myParagraphsNumber = myApi.getParagraphsNumber();
    }

    @Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		stopTalking();
        myApi.clearHighlighting();

		LastReadPageOfCurrentBook.saveLocationOfLastReadPage(this);
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		if (myTTS != null) {
			myTTS.shutdown();
		}
		super.onDestroy();
	}

	private volatile int myInitializationStatus;
	private final static int TTS_INITIALIZED = 2;
	private final static int FULLY_INITIALIZED =  TTS_INITIALIZED;

	// implements TextToSpeech.OnInitListener
	public void onInit(int status) {
		if (myInitializationStatus != FULLY_INITIALIZED) {
			myInitializationStatus |= TTS_INITIALIZED;
			if (myInitializationStatus == FULLY_INITIALIZED) {
				doFinalInitialization();
			}
		}
	}

	private void setActionsEnabled(final boolean enabled) {
		runOnUiThread(new Runnable() {
			public void run() {
				findViewById(R.id.speak_menu_back).setEnabled(enabled);
				findViewById(R.id.speak_menu_forward).setEnabled(enabled);
				findViewById(R.id.speak_menu_pause).setEnabled(enabled);
				findViewById(R.id.speak_menu_contents).setEnabled(enabled);
			}
		});
	}

	private void doFinalInitialization() {

        if (null == myTTS.getLanguage()) {
            setActionsEnabled(false);
            showErrorMessage(getText(R.string.no_tts_language), true);
            return;
        }

		myTTS.setOnUtteranceCompletedListener(this);

        myTTS.addEarcon(CONTENTS_EARCON, "org.benetech.android", R.raw.sound_toc);
        myTTS.addEarcon(MENU_EARCON, "org.benetech.android", R.raw.sound_main_menu);
        myTTS.addEarcon(FORWARD_EARCON, "org.benetech.android", R.raw.sound_forward);
        myTTS.addEarcon(BACK_EARCON, "org.benetech.android", R.raw.sound_back);
        myTTS.addEarcon(START_READING_EARCON, "org.benetech.android", R.raw.sound_start_reading);

        setCurrentLocation();

        myTTS.playEarcon(START_READING_EARCON, TextToSpeech.QUEUE_ADD, null);

        if (accessibilityManager.isEnabled()) {
            speakString(myApi.getBookTitle(), 0);
        } else {
            setTitle(myApi.getBookTitle());
        }

        setActionsEnabled(true);
        speakParagraph(getNextParagraph());
	}

	@Override
	public void onUtteranceCompleted(String uttId) {
        String lastSentenceID = Integer.toString(lastSentence);
		if (myIsActive && uttId.equals(lastSentenceID)) {
            ++myParagraphIndex;
            speakParagraph(getNextParagraph());
            if (myParagraphIndex >= myParagraphsNumber) {
                stopTalking();
            }
		} else {
            lastSpoken = Integer.parseInt(uttId);
            if (myIsActive) {
                int listSize = wordIndexList.size();
                if (listSize > 1 && lastSpoken < listSize) {
                    highlightSentence(wordIndexList.get(lastSpoken - 1) + 1, wordIndexList.get(lastSpoken));
                }
            }
		}
	}

	private void highlightParagraph()  {
		if (0 <= myParagraphIndex && myParagraphIndex < myParagraphsNumber) {
			myApi.highlightArea(
                    new TextPosition(myParagraphIndex, 0, 0),
                    new TextPosition(myParagraphIndex, Integer.MAX_VALUE, 0)
            );
		} else {
			myApi.clearHighlighting();
		}
	}

	private void stopTalking() {
		setActive(false);
		if (myTTS != null) {
			myTTS.stop();
		}
	}

	private void showErrorMessage(final CharSequence text, final boolean fatal) {
        final VoiceableDialog finishedDialog = new VoiceableDialog(this);
        if (fatal) {
            setTitle(R.string.failure);
        }
        finishedDialog.popup(text.toString(), 5000);
	}

	private volatile PowerManager.WakeLock myWakeLock;

	private synchronized void setActive(final boolean active) {


		runOnUiThread(new Runnable() {
			public void run() {
                if (myIsActive != active) {
                    ((Button)findViewById(R.id.speak_menu_pause)).setText(active ? R.string.on_press_pause : R.string.on_press_play);
                }
			}
		});

        myIsActive = active;

		if (active) {
			if (myWakeLock == null) {
				myWakeLock =
					((PowerManager)getSystemService(POWER_SERVICE))
						.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FBReader TTS plugin");
				myWakeLock.acquire();
			}
		} else {
			if (myWakeLock != null) {
				myWakeLock.release();
				myWakeLock = null;
			}
		}
	}

	private void speakString(String text, final int sentenceNumber) {
		HashMap<String, String> callbackMap = new HashMap<String, String>();
		callbackMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, Integer.toString(sentenceNumber));
		myTTS.speak(text, TextToSpeech.QUEUE_ADD, callbackMap);
	}

	private void gotoPreviousParagraph() {
        for (int i = myParagraphIndex - 1; i >= 0; --i) {
            if (myApi.getParagraphText(i).length() > 0) {
                myParagraphIndex = i;
                break;
            }
        }
        if (myApi.getPageStart().ParagraphIndex >= myParagraphIndex) {
            myApi.setPageStart(new TextPosition(myParagraphIndex, 0, 0));
        }
        highlightParagraph();
        runOnUiThread(new Runnable() {
            public void run() {
                findViewById(R.id.speak_menu_forward).setEnabled(true);
                findViewById(R.id.speak_menu_pause).setEnabled(true);
            }
        });

	}

	private String getNextParagraph() {
			String text = "";
			for (; myParagraphIndex < myParagraphsNumber; ++myParagraphIndex) {
				final String s = myApi.getParagraphText(myParagraphIndex);
				if (s.length() > 0) {
					text = s;
					break;
				}
			}
			if (!"".equals(text) && !myApi.isPageEndOfText()) {
				myApi.setPageStart(new TextPosition(myParagraphIndex, 0, 0));
			}
			highlightParagraph();
			if (myParagraphIndex >= myParagraphsNumber) {
				runOnUiThread(new Runnable() {
					public void run() {
						findViewById(R.id.speak_menu_forward).setEnabled(false);
					}
				});
			}
			return text;

	}

    // Bookshare custom methods
    
    private void highlightSentence(int startWord, int endWord)  {
        if (0 <= myParagraphIndex && myParagraphIndex < myParagraphsNumber) {
            myApi.highlightArea(
                    new TextPosition(myParagraphIndex, startWord, 0),
                    new TextPosition(myParagraphIndex, endWord + 1, 0)
            );
        } else {
            myApi.clearHighlighting();
        }
    }

    private void speakParagraph(String text) {
        if (text.length() < 1) {
            return;
        }
        setActive(true);
        createSentenceIterator();
        
        String currentSentence;
        int sentenceNumber = 0;
        int numWordIndices = wordIndexList.size();

        if (justPaused) {                    // on returning from pause, iterate to the last sentence spoken
            justPaused = false;
            for (int i=1; i< lastSpoken; i++) {
                if (sentenceListIterator.hasNext()) {
                    sentenceListIterator.next();
                }
            }
            if (lastSpoken > 1 && numWordIndices > lastSpoken) {
                sentenceNumber = lastSpoken - 1;
                highlightSentence(wordIndexList.get(lastSpoken - 2) + 1, wordIndexList.get(lastSpoken - 1));
            }

        } else { //should only highlight first sentence of paragraph if we haven't just paused
            if (numWordIndices > 0) {
                highlightSentence(0, wordIndexList.get(0));
            }
        }

        while (sentenceListIterator.hasNext())  { 	// if there are sentences in the sentence queue
            sentenceNumber++;
            currentSentence = sentenceListIterator.next();
            speakString(currentSentence, sentenceNumber);
        }
        
        lastSentence = sentenceNumber;
    }

    private void createSentenceIterator() {
        StringBuilder sb = new StringBuilder();
        boolean inSentence = true;

        ArrayList<String> sentenceList = new ArrayList<String>();
        wordIndexList = new ArrayList<Integer>();

        FBReaderApp myReader = (FBReaderApp) ZLApplication.Instance();
        final ZLTextWordCursor cursor = new ZLTextWordCursor(myReader.getTextView().getStartCursor());
        cursor.moveToParagraph(myParagraphIndex);
        cursor.moveToParagraphStart();

        while(!cursor.isEndOfParagraph()) {
            ZLTextElement element = cursor.getElement();
            while (inSentence)  {
                if(element instanceof ZLTextWord) {
                    if (element.toString().indexOf(".") == (element.toString().length() -1) ) {           // detects period at end of element
                       sb.append(element.toString().substring(0,element.toString().indexOf(".")));        // remove period
                        wordIndexList.add(cursor.getElementIndex());
                       inSentence = false;
                    } else {
                          sb.append(element.toString()).append(" ");
                    }
                }
                cursor.nextWord();
                if (cursor.isEndOfParagraph())
                    break;
                element = cursor.getElement();
            }

            sentenceList.add(sb.toString());

            sb.setLength(0);
            inSentence = true;
        }
        sentenceListIterator = sentenceList.iterator();
    }

    private void playOrPause() {
            if (!myIsActive) {
                final String nextParagraph = getNextParagraph();
                if (null == nextParagraph || nextParagraph.length() < 1) {
                    setCurrentLocation();
                    justPaused = false;
                }
                speakParagraph(nextParagraph);
            } else {
                stopTalking();
                justPaused = true;
            }
        }

    private void goForward() {
        stopTalking();
        myTTS.playEarcon(FORWARD_EARCON, TextToSpeech.QUEUE_ADD, null);
        if (myParagraphIndex < myParagraphsNumber) {
            ++myParagraphIndex;
            speakParagraph(getNextParagraph());
        }
    }

    private void goBackward() {
        stopTalking();
        myTTS.playEarcon(BACK_EARCON, TextToSpeech.QUEUE_ADD, null);
        gotoPreviousParagraph();
        speakParagraph(getNextParagraph());
    }

    private void showContents() {
        justPaused = true;
        stopTalking();
        myTTS.playEarcon(CONTENTS_EARCON, TextToSpeech.QUEUE_FLUSH, null);
        Intent tocIntent = new Intent(this, TOCActivity.class);
        startActivityForResult(tocIntent, PLAY_AFTER_TOC);
    }
    
    private void showMainMenu() {
        stopTalking();
        justPaused = true;
        myTTS.playEarcon(MENU_EARCON, TextToSpeech.QUEUE_ADD, null);
        resumePlaying = true;
        Intent intent = new Intent(this, AccessibleMainMenuActivity.class);
        startActivityForResult(intent, PLAY_AFTER_TOC);
    }

    @Override
         public boolean dispatchTouchEvent(MotionEvent me){
           this.detector.onTouchEvent(me);
          return super.dispatchTouchEvent(me);
         }

    @Override
    public void onSwipe(int direction) {
        myVib.vibrate(VIBE_PATTERN, -1);
        switch (direction) {
            case SimpleGestureFilter.SWIPE_RIGHT :
                goForward();
                break;
            case SimpleGestureFilter.SWIPE_LEFT :
                goBackward();
                break;
            case SimpleGestureFilter.SWIPE_DOWN :
                showMainMenu();
                break;
            case SimpleGestureFilter.SWIPE_UP :
                showContents();
                break;
          }
    }

    @Override
    public void onDoubleTap() {
        myVib.vibrate(VIBE_PATTERN, -1);
        playOrPause();
    }

    /*
     * show accessible full screen menu when accessibility is turned on
     *
    */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_MENU){
            showMainMenu();
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            stopTalking();
            if (accessibilityManager.isEnabled()) {
                this.setResult(SPEAK_BACK_PRESSED);
            }
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }
}
