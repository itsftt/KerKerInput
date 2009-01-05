﻿/* ZeroBPMFInput for Android Platform Version 0.1
 * 
 * Copyright (c) 2008 Zero, Chien-An Cho
 * (MIT License)
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * 
 * Contact:: itszero at gmail dot com
 * 
 * KerKerInputMethod: the TextKeyListener implement for the BPMF input.
 */
package idv.Zero.KerKerInput;

import android.text.method.TextKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.Cursor;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Locale;

public class KerKerInputMethod extends TextKeyListener
{
	private TextView inputCandidatesView;
	private static final int STATE_INPUT = 0;
	private static final int STATE_CHOOSE = 1;
	private static final int STATE_ENGLISH = 0;
	private static final int STATE_BPMF = 1;
	public static final int MODE_PHYSICAL_KB = 0;
	public static final int MODE_VIRTUAL_KB = 1;
	private StringBuilder inputBuffer = new StringBuilder();
	private StringBuilder inputBufferRaw = new StringBuilder();
	private int state_ime = STATE_BPMF;
	private int state = STATE_INPUT;
	private int input_mode = MODE_PHYSICAL_KB;
	private boolean bypassKeyUp = false;
	private HashMap<Integer, String> K2N;
	private Cursor currentQuery;
	private static int CANDIDATES_PER_PAGE = 5;
	private int currentPage = 0;
	private int totalPages = 0;
	private ArrayList<String> candidates;
	private ArrayList<Button> candidate_buttons;
	private static final int DBDOWNLOAD_NOTIFICATION = 0x1005;
	private static final String DATABASE_URL = "http://zero.itszero.info/KerKerInput/cin.db";
	
	private SQLiteDatabase db;
	
	public KerKerInputMethod(Context c, TextView tv, ArrayList<Button> candidate_buttons)
	{
		super(null, false);
		inputCandidatesView = tv;
		this.candidate_buttons = candidate_buttons;
		
		initKeyNameData();
		try
		{
			String dbpath = c.getDatabasePath("cin.db").toString();
			db = SQLiteDatabase.openDatabase(dbpath, null, SQLiteDatabase.OPEN_READONLY);
		}
		catch(SQLiteException ex)
		{
			System.out.println("Error, no database file found. Downloading...");
			final NotificationManager nm = (NotificationManager)c.getSystemService("notification");
			Notification n = new Notification(R.drawable.icon, "KerKer: 正在下載資料庫...", System.currentTimeMillis());
			PendingIntent contentIntent = PendingIntent.getActivity(c, 0, new Intent(c, KerKerInputUI.class), 0);
			n.flags = Notification.FLAG_NO_CLEAR;
			n.setLatestEventInfo(c, "科科輸入法", "正在下載資料庫...", contentIntent);
			nm.notify(DBDOWNLOAD_NOTIFICATION, n);

			// Create the database (and the directories required) then close it.
			db = c.openOrCreateDatabase("cin.db", 0, null);
			db.close();
			String dbpath = c.getDatabasePath("cin.db").toString();
			FileDownload.download(DATABASE_URL, dbpath);
			db = SQLiteDatabase.openDatabase(dbpath, null, SQLiteDatabase.OPEN_READONLY);
			System.out.println("OK, resume initalization process...");
			nm.cancel(DBDOWNLOAD_NOTIFICATION);
		}
		db.setLocale(Locale.TRADITIONAL_CHINESE);
	}
	
	public void setInputMode(int mode)
	{
		this.input_mode = mode;
		if (mode == MODE_PHYSICAL_KB)
			CANDIDATES_PER_PAGE = 5;
		else if (mode == MODE_VIRTUAL_KB)
			CANDIDATES_PER_PAGE = 6;
	}
	
	public void handleClick(EditText target, int touchKeyCode)
	{
		// When input in touch mode, we emulate the keyboard input.
		// Using ` as escape sequence since it's not used in BPMF input.
		String translate = "1qaz`2wsx`edc``rfv``5tgb`yhn``ujm``8ik,`9ol.`0p;/- 6347";
		String key;
		int keyCode = 0;
		if (touchKeyCode >= translate.length())
		{
			if (touchKeyCode == 55)
				keyCode = KeyEvent.KEYCODE_DEL;
			else if (touchKeyCode == 56)
				keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
			else if (touchKeyCode == 58)
				keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
			else if (touchKeyCode == 70)
				keyCode = KeyEvent.KEYCODE_ALT_LEFT;
			else if (touchKeyCode >= 60 && touchKeyCode < 70)
			{
				try {
					keyCode = KeyEvent.class.getDeclaredField("KEYCODE_" + new Integer(touchKeyCode - 60 + 1).toString()).getInt(null);
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (SecurityException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (NoSuchFieldException e) {
					e.printStackTrace();
				}
			}
		}
		else
		{
			key = translate.substring(touchKeyCode, touchKeyCode + 1);
			System.out.println("Get VKB [" + key + "]");
			if (key.equals("/"))
				key = "SLASH";
			else if (key.equals(","))
				key = "COMMA";
			else if (key.equals("."))
				key = "PERIOD";
			else if (key.equals("-"))
				key = "MINUS";
			else if (key.equals(" "))
				key = "SPACE";
			else if (key.equals(";"))
				key = "SEMICOLON";
			
			try {
				keyCode = KeyEvent.class.getField("KEYCODE_" + key.toUpperCase()).getInt(null);
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (NoSuchFieldException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		
		KeyEvent ed = new KeyEvent(1, 1, KeyEvent.ACTION_DOWN, keyCode, 0);
		KeyEvent eu = new KeyEvent(1, 1, KeyEvent.ACTION_UP, keyCode, 0);
		this.onKeyDown(null, target.getText(), keyCode, ed);
		this.onKeyUp(null, target.getText(), keyCode, eu);
	}
	
	public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
		if (event.getDownTime() == 1)
			this.setInputMode(MODE_VIRTUAL_KB);
		else
			this.setInputMode(MODE_PHYSICAL_KB);
		
		if (keyCode == KeyEvent.KEYCODE_ALT_LEFT)
		{
			switch (state_ime)
			{
			case STATE_ENGLISH:
				state_ime = STATE_BPMF;
				inputCandidatesView.setText(R.string.bpmf_label);
				break;
				
			case STATE_BPMF:
				state_ime = STATE_ENGLISH;
				state = STATE_INPUT;
				inputCandidatesView.setText(R.string.eng_label);
				inputBuffer.delete(0, inputBuffer.length());
				inputBufferRaw.delete(0, inputBufferRaw.length());
				break;
			}
			
			bypassKeyUp = true;
			return true;
		}
		
		return false;
	}
	
	public boolean onKeyUp(View view, Editable content, int keyCode, KeyEvent event) {
		if (bypassKeyUp)
		{
			bypassKeyUp = false;
			
			return true;
		}
		
		switch (state)
		{
		case STATE_INPUT:
			switch (state_ime)
			{
			case STATE_ENGLISH:
				int c = event.getUnicodeChar();
				if (c != 0)
					content.append((char)c);
				else if (keyCode == KeyEvent.KEYCODE_DEL)
					if (content.length() > 0)
						content.delete(content.length() - 1, content.length());
				break;
				
			case STATE_BPMF:
				switch (keyCode)
				{
				case KeyEvent.KEYCODE_DEL:
					if (inputBuffer.length() == 0)
					{
						if (content.length() > 0)
							content.delete(content.length() - 1, content.length());
					}
					else
					{
						inputBuffer.deleteCharAt(inputBuffer.length() - 1);
						inputBufferRaw.deleteCharAt(inputBufferRaw.length() - 1);
					}
					break;
				case KeyEvent.KEYCODE_SPACE:
					state = STATE_CHOOSE;
					break;
					
				default:
					char keyRaw = (char)event.getUnicodeChar();
					// Workaround for G1
					if (keyCode == KeyEvent.KEYCODE_I && event.isAltPressed())
					{
						keyCode = KeyEvent.KEYCODE_MINUS;
						keyRaw = '-';
					}
					if (keyCode == KeyEvent.KEYCODE_J && event.isAltPressed())
					{
						keyCode = KeyEvent.KEYCODE_SEMICOLON;
						keyRaw = ';';
					}
					if (keyCode == KeyEvent.KEYCODE_PERIOD && event.isAltPressed())
					{
						keyCode = KeyEvent.KEYCODE_SLASH;
						keyRaw = '/';
					}
									
					String keyName = K2N.get(keyCode);
					if (keyName != null)
					{
						inputBuffer.append(keyName);
						inputBufferRaw.append(keyRaw);
					}
					// 如果是音調符號，直接進入選字模式。
					if (keyRaw == '3' || keyRaw == '4' || keyRaw == '6' || keyRaw == '7')
						state = STATE_CHOOSE;
					break;
				}
				
				if (state == STATE_CHOOSE)
				{
					String args = inputBufferRaw.toString();
					currentQuery = db.rawQuery("Select val from bpmf where key ='" + args + "'", null);
					Log.i("DB", "Query by key=" + inputBufferRaw.toString());
					if (currentQuery.getCount() == 0)
					{
						inputBuffer.delete(0, inputBuffer.length());
						inputBufferRaw.delete(0, inputBufferRaw.length());
						this.updateCandidates(" 找不到對應。");
						
						// reset state
						state = STATE_INPUT;
					}
					else
					{
						currentPage = 0;
						totalPages = currentQuery.getCount() / CANDIDATES_PER_PAGE;
						if (CANDIDATES_PER_PAGE  * totalPages < currentQuery.getCount())
							totalPages++;
						
						candidates = new ArrayList<String>();
						
						int count = currentQuery.getCount();
						int colIdx = currentQuery.getColumnIndex("val");
						// Move to first record
						currentQuery.moveToNext();
						for(int i=0;i<count;i++)
						{
							String ca = currentQuery.getString(colIdx);
							candidates.add(ca);
							currentQuery.moveToNext();
						}
						
						this.updateCandidates(null);
					}
				}
				else
				{
					this.updateCandidates(null);
				}
				break;
			}
			break;
			
		case STATE_CHOOSE:
			switch (keyCode)
			{
			case KeyEvent.KEYCODE_DEL:
				state = STATE_INPUT;
				inputBuffer.delete(0, inputBuffer.length());
				inputBufferRaw.delete(0, inputBufferRaw.length());
				break;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				if (currentPage > 0)
					currentPage--;
				else
					currentPage = totalPages - 1;
				break;
			case KeyEvent.KEYCODE_SPACE:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if (currentPage < totalPages - 1)
					currentPage++;
				else
					currentPage = 0;
				break;
			default:
				if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
				{
				    // This prevents user hit tone symbol twice makes program crash.
				    // It's because first sign interpreted as bpmf symbol, and second one is treated as candidate choose.
				    // Also prevent user select non-exist candidates on physical kb.
				    if ((currentPage * CANDIDATES_PER_PAGE + keyCode - KeyEvent.KEYCODE_0 - 1) < candidates.size())
				    {
				        content.append(candidates.get(currentPage * CANDIDATES_PER_PAGE + keyCode - KeyEvent.KEYCODE_0 - 1));
				        state = STATE_INPUT;
				        inputBuffer.delete(0, inputBuffer.length());
				        inputBufferRaw.delete(0, inputBufferRaw.length());
				    }
				}
				break;
			}
			
			updateCandidates(null);
			break;
		}
		
		if (keyCode == KeyEvent.KEYCODE_MENU)
			return false;
		else
			return true;
	}
	
	private void updateCandidates(String msg)
	{
		inputCandidatesView.setText(R.string.bpmf_label);
		if (msg != null)
		{
			inputCandidatesView.append(msg);
			return;
		}
		else
		{
			inputCandidatesView.append(" " + inputBuffer.toString());
			if (state == STATE_CHOOSE)
			{
				if (input_mode == MODE_PHYSICAL_KB)
					inputCandidatesView.append(" ");
				
				for(int i = 0;i<CANDIDATES_PER_PAGE && (i + currentPage * CANDIDATES_PER_PAGE) < candidates.size();i++)
				{
					String candidate_char = candidates.get(i + currentPage * CANDIDATES_PER_PAGE);
					candidate_buttons.get(i).setVisibility(View.GONE);
					switch(input_mode)
					{
					case MODE_PHYSICAL_KB:
						if (i > 0) inputCandidatesView.append(" ");
						inputCandidatesView.append((i+1) + " " + candidate_char);
						break;
					case MODE_VIRTUAL_KB:
						candidate_buttons.get(i).setText(candidate_char);
						candidate_buttons.get(i).setVisibility(View.VISIBLE);
						break;
					}
				}
				
				inputCandidatesView.append(" " + (currentPage+1) + "/" + totalPages);
			}
			else
			{
				for(int i = 0;i<CANDIDATES_PER_PAGE;i++)
					candidate_buttons.get(i).setVisibility(View.GONE);
			}
		}
	}
	
	private void initKeyNameData()
	{
		K2N = new HashMap<Integer, String>();			
		K2N.put(KeyEvent.KEYCODE_COMMA,     "ㄝ"); // ,
		K2N.put(KeyEvent.KEYCODE_MINUS,     "ㄦ"); // -
		K2N.put(KeyEvent.KEYCODE_PERIOD,    "ㄡ"); // .
		K2N.put(KeyEvent.KEYCODE_SLASH,     "ㄥ"); // /
		K2N.put(KeyEvent.KEYCODE_0,         "ㄢ"); // 0
		K2N.put(KeyEvent.KEYCODE_1,         "ㄅ"); // 1
		K2N.put(KeyEvent.KEYCODE_2,         "ㄉ"); // 2
		K2N.put(KeyEvent.KEYCODE_3,         "ˇ"); // 3
		K2N.put(KeyEvent.KEYCODE_4,         "ˋ"); // 4
		K2N.put(KeyEvent.KEYCODE_5,         "ㄓ"); // 5
		K2N.put(KeyEvent.KEYCODE_6,         "ˊ"); // 6
		K2N.put(KeyEvent.KEYCODE_7,         "˙"); // 7
		K2N.put(KeyEvent.KEYCODE_8,         "ㄚ"); // 8
		K2N.put(KeyEvent.KEYCODE_9,         "ㄞ"); // 9
		K2N.put(KeyEvent.KEYCODE_SEMICOLON, "ㄤ"); // ;
		K2N.put(KeyEvent.KEYCODE_A,         "ㄇ"); // a
		K2N.put(KeyEvent.KEYCODE_B,         "ㄖ"); // b
		K2N.put(KeyEvent.KEYCODE_C,         "ㄏ"); // c
		K2N.put(KeyEvent.KEYCODE_D,         "ㄎ"); // d
		K2N.put(KeyEvent.KEYCODE_E,         "ㄍ"); // e
		K2N.put(KeyEvent.KEYCODE_F,         "ㄑ"); // f
		K2N.put(KeyEvent.KEYCODE_G,         "ㄕ"); // g
		K2N.put(KeyEvent.KEYCODE_H,         "ㄘ"); // h
		K2N.put(KeyEvent.KEYCODE_I,         "ㄛ"); // i
		K2N.put(KeyEvent.KEYCODE_J,         "ㄨ"); // j
		K2N.put(KeyEvent.KEYCODE_K,         "ㄜ"); // k
		K2N.put(KeyEvent.KEYCODE_L,         "ㄠ"); // l
		K2N.put(KeyEvent.KEYCODE_M,         "ㄩ"); // m
		K2N.put(KeyEvent.KEYCODE_N,         "ㄙ"); // n
		K2N.put(KeyEvent.KEYCODE_O,         "ㄟ"); // o
		K2N.put(KeyEvent.KEYCODE_P,         "ㄣ"); // p
		K2N.put(KeyEvent.KEYCODE_Q,         "ㄆ"); // q
		K2N.put(KeyEvent.KEYCODE_R,         "ㄐ"); // r
		K2N.put(KeyEvent.KEYCODE_S,         "ㄋ"); // s
		K2N.put(KeyEvent.KEYCODE_T,         "ㄔ"); // t
		K2N.put(KeyEvent.KEYCODE_U,         "ㄧ"); // u
		K2N.put(KeyEvent.KEYCODE_V,         "ㄒ"); // v
		K2N.put(KeyEvent.KEYCODE_W,         "ㄊ"); // w
		K2N.put(KeyEvent.KEYCODE_X,         "ㄌ"); // x
		K2N.put(KeyEvent.KEYCODE_Y,         "ㄗ"); // y
		K2N.put(KeyEvent.KEYCODE_Z,         "ㄈ"); // z
	}
}
