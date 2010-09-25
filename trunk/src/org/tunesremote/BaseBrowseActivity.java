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

import android.app.ListActivity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

public class BaseBrowseActivity extends ListActivity {

   public static final int RESULT_SWITCH_TO_ARTISTS = RESULT_FIRST_USER + 1;
   public static final int RESULT_SWITCH_TO_PLAYLISTS = RESULT_FIRST_USER + 2;

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
      super.onActivityResult(requestCode, resultCode, intent);

      // If canceled stay at current level
      if (resultCode == RESULT_CANCELED)
         return;

      // Otherwise pass this back up the chain
      this.setResult(resultCode, intent);
      this.finish();
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      super.onCreateOptionsMenu(menu);

      MenuItem artists = menu.add(R.string.control_menu_artists);
      artists.setIcon(R.drawable.ic_search_category_music_artist);
      artists.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {
            BaseBrowseActivity.this.setResult(RESULT_SWITCH_TO_ARTISTS);
            BaseBrowseActivity.this.finish();
            return true;
         }
      });

      MenuItem playlists = menu.add("Playlists");
      playlists.setIcon(R.drawable.ic_search_category_music_song);
      playlists.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {
            BaseBrowseActivity.this.setResult(RESULT_SWITCH_TO_PLAYLISTS);
            BaseBrowseActivity.this.finish();
            return true;
         }
      });
      return true;
   }
}
