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
package org.tunesremote;

import org.tunesremote.util.PairingDatabase;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Displays a list of servers that can be connected to.
 * <p>
 * Copyright (c) 2010 Melloware, Inc. <http://www.melloware.com>
 * @author Emil A. Lefkof III <info@melloware.com>
 * @version 1.0
 */
public class ServerActivity extends ListActivity {

   private static final String TAG = ServerActivity.class.getName();

   private BackendService backend;

   public ServiceConnection connection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
         backend = ((BackendService.BackendBinder) service).getService();

         Cursor cursor = backend.pairdb.fetchAllServers();
         startManagingCursor(cursor);
         String[] from = new String[] { PairingDatabase.FIELD_PAIR_LIBRARY, PairingDatabase.FIELD_PAIR_ADDRESS };
         int[] to = new int[] { R.id.server_library, R.id.server_address };
         // Now create a simple cursor adapter and set it to display
         SimpleCursorAdapter adapter = new SimpleCursorAdapter(ServerActivity.this, R.layout.server_row, cursor, from,
                  to);
         setListAdapter(adapter);
      }

      public void onServiceDisconnected(ComponentName className) {
         backend = null;

      }
   };

   /*
    * (non-Javadoc)
    * @see android.app.Activity#onCreate(android.os.Bundle)
    */
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setTitle(R.string.control_menu_picklibrary);
      setContentView(R.layout.server_list);
      ListView lv = getListView();
      lv.setTextFilterEnabled(true);
      lv.setOnItemClickListener(new OnItemClickListener() {
         public void onItemClick(AdapterView<?> a, View v, int position, long id) {
            Log.d(TAG, "Server selected!");
            try {
               Cursor cursor = backend.pairdb.fetchServer(id);
               if (cursor.getCount() > 0) {
                  backend.setLibrary(cursor.getString(1), cursor.getString(2), cursor.getString(3));
               }
               cursor.close();
            } catch (Exception ex) {
               Log.e(TAG, ex.getMessage(), ex);
            }
            finish();
         }
      });
   }

   @Override
   public void onStart() {
      super.onStart();
      this.bindService(new Intent(this, BackendService.class), connection, Context.BIND_AUTO_CREATE);

   }

   @Override
   public void onStop() {
      super.onStop();
      this.unbindService(connection);

   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      MenuItem forget = menu.add(R.string.library_menu_forget);
      forget.setIcon(android.R.drawable.ic_menu_delete);
      forget.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {

            new AlertDialog.Builder(ServerActivity.this).setMessage(R.string.library_forget).setPositiveButton(
                     R.string.library_forget_pos, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                           backend.pairdb.deleteAll();
                        }
                     }).setNegativeButton(R.string.library_forget_neg, new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
               }
            }).create().show();

            return true;
         }
      });
      return true;
   }

}
