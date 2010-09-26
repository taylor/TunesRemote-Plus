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

import org.tunesremote.daap.Library;
import org.tunesremote.daap.Playlist;
import org.tunesremote.daap.Session;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PlaylistsActivity extends BaseBrowseActivity {

   public final static String TAG = PlaylistsActivity.class.toString();

   protected BackendService backend;
   protected Session session;
   protected Library library;
   protected ListView list;
   protected PlaylistsAdapter adapter;

   public ServiceConnection connection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
         backend = ((BackendService.BackendBinder) service).getService();
         session = backend.getSession();

         if (session == null)
            return;

         adapter.results.clear();

         // begin search now that we have a backend
         library = new Library(session);
         library.readPlaylists(adapter);

      }

      public void onServiceDisconnected(ComponentName className) {
         backend = null;
         session = null;

      }
   };

   public Handler resultsUpdated = new Handler() {
      @Override
      public void handleMessage(Message msg) {
         adapter.notifyDataSetChanged();
      }
   };

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
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.gen_list);

      ((TextView) this.findViewById(android.R.id.empty)).setText(R.string.playlists_empty);
      this.list = this.getListView();
      this.adapter = new PlaylistsAdapter(this, resultsUpdated);
      this.setListAdapter(adapter);

      this.registerForContextMenu(this.getListView());

      this.getListView().setOnItemClickListener(new OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            try {
               final Playlist ply = (Playlist) adapter.getItem(position);
               final String playlistid = Long.toString(ply.getID());

               Intent intent = new Intent(PlaylistsActivity.this, TracksActivity.class);
               intent.putExtra(Intent.EXTRA_TITLE, "");
               intent.putExtra("Playlist", playlistid);
               intent.putExtra("PlaylistPersistentId", ply.getPersistentId());
               intent.putExtra("AllAlbums", false);
               PlaylistsActivity.this.startActivityForResult(intent, 1);

            } catch (Exception e) {
               Log.w(TAG, "onCreate:" + e.getMessage());
            }

         }
      });
   }

   @Override
   public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

      final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

      try {
         // create context menu to play entire artist
         final Playlist ply = (Playlist) adapter.getItem(info.position);
         menu.setHeaderTitle(ply.getName());
         final String playlistid = Long.toString(ply.getID());

         final MenuItem browse = menu.add(R.string.albums_menu_browse);
         browse.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
               Intent intent = new Intent(PlaylistsActivity.this, TracksActivity.class);
               intent.putExtra(Intent.EXTRA_TITLE, "");
               intent.putExtra("Playlist", playlistid);
               intent.putExtra("PlaylistPersistentId", ply.getPersistentId());
               intent.putExtra("AllAlbums", false);
               PlaylistsActivity.this.startActivityForResult(intent, 1);

               return true;
            }

         });
      } catch (Exception e) {
         Log.w(TAG, "onCreateContextMenu:" + e.getMessage());
      }

   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

      // Handle switching to playlists
      Log.d(TAG, String.format(" PlaylistActivity onAcivityResult resultCode = %d", resultCode));
      if (resultCode == BaseBrowseActivity.RESULT_SWITCH_TO_PLAYLISTS)
         return;

      super.onActivityResult(requestCode, resultCode, intent);
   }
}
