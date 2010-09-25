/*
    TunesRemote+ - http://code.google.com/p/tunesremote-plus/
    
    Copyright (C) 2008 Jeffrey Sharkey, http://jsharkey.org/
    Copyright (C) 2010 TunesRemote+, http://code.google.com/p/tunesremote-plus/
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
    
    The Initial Developer of the Original Code is Jeffrey Sharkey.
    Portions created by Jeffrey Sharkey are
    Copyright (C) 2008. Jeffrey Sharkey, http://jsharkey.org/
    All Rights Reserved.
 */
package org.tunesremote.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class PairingDatabase extends SQLiteOpenHelper {

   public final static String TAG = PairingDatabase.class.toString();

   public final static String DB_NAME = "pairing";
   public final static int DB_VERSION = 1;

   public final static String TABLE_PAIR = "pairing";
   public final static String FIELD_PAIR_LIBRARY = "library";
   public final static String FIELD_PAIR_ADDRESS = "address";
   public final static String FIELD_PAIR_GUID = "guid";

   public PairingDatabase(Context context) {
      super(context, DB_NAME, null, DB_VERSION);
   }

   @Override
   public void onCreate(SQLiteDatabase db) {
      db.execSQL("CREATE TABLE " + TABLE_PAIR + " (_id INTEGER PRIMARY KEY, " + FIELD_PAIR_LIBRARY + " TEXT, "
               + FIELD_PAIR_ADDRESS + " TEXT, " + FIELD_PAIR_GUID + " INTEGER)");
   }

   @Override
   public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      db.execSQL("DROP TABLE IF EXISTS " + TABLE_PAIR);
      onCreate(db);
   }

   protected String findCode(String field, String value) {
      SQLiteDatabase db = this.getReadableDatabase();
      Cursor c = db.query(TABLE_PAIR, new String[] { FIELD_PAIR_GUID }, field + " = ?", new String[] { value }, null,
               null, null);

      String code = null;
      if (c.getCount() > 0) {
         c.moveToFirst();
         code = c.getString(c.getColumnIndexOrThrow(FIELD_PAIR_GUID));
      }
      c.close();

      Log.d(TAG, String.format("findCode found code=%s for %s=%s", code, field, value));

      return code;

   }

   public String findCodeLibrary(String library) {
      return this.findCode(FIELD_PAIR_LIBRARY, library);

   }

   public String findCodeAddress(String address) {
      return this.findCode(FIELD_PAIR_ADDRESS, address);

   }

   public void deleteLibrary(String library) {
      SQLiteDatabase db = this.getWritableDatabase();
      Log.d(TAG, String.format("deleteLibrary library=%s", library));
      db.delete(TABLE_PAIR, FIELD_PAIR_LIBRARY + " = ?", new String[] { library });

   }

   public void deleteAll() {
      SQLiteDatabase db = this.getWritableDatabase();
      db.delete(TABLE_PAIR, "", new String[] {});

   }

   public void insertCode(String address, String library, String code) {
      SQLiteDatabase db = this.getWritableDatabase();

      // first make sure we erase any existing pairings for this library
      this.deleteLibrary(library);

      ContentValues values = new ContentValues();
      values.put(FIELD_PAIR_ADDRESS, address);
      values.put(FIELD_PAIR_LIBRARY, library);
      values.put(FIELD_PAIR_GUID, code);

      Log.d(TAG, String.format("insertCode address=%s, library=%s, code=%s", address, library, code));
      db.insert(TABLE_PAIR, null, values);

   }

   public boolean libraryExists(String library) {
      SQLiteDatabase db = this.getReadableDatabase();
      Cursor c = db.query(TABLE_PAIR, new String[] { FIELD_PAIR_GUID }, FIELD_PAIR_LIBRARY + " = ?",
               new String[] { library }, null, null, null);
      boolean exists = (c.getCount() > 0);
      c.close();
      Log.d(TAG, String.format("libraryExists library=%s result=%s", library, Boolean.toString(exists)));
      return exists;

   }

   public void updateAddress(String library, String address) {
      SQLiteDatabase db = this.getWritableDatabase();

      ContentValues values = new ContentValues();
      values.put(FIELD_PAIR_ADDRESS, address);

      Log.d(TAG, String.format("updateAddress library=%s, address=%s", library, address));
      db.update(TABLE_PAIR, values, FIELD_PAIR_LIBRARY + " = ?", new String[] { library });

   }

}
