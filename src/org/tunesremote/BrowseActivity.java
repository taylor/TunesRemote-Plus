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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/*
 * This is a meta-activity that handles the "Browse of song" activity and it's
 * subactivities Doesn't show any UI itself
 */
public class BrowseActivity extends Activity {

   public final static String TAG = AlbumsActivity.class.toString();

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
      // Okay result means we chose a new track
      // Cancel means no track chosen
      // Either way return back to Control Activity
      if (resultCode == RESULT_OK || resultCode == RESULT_CANCELED) {
         finish();
         return;
      }

      showWindow(resultCode);
   }

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      int windowType = this.getIntent().getIntExtra("windowType", BaseBrowseActivity.RESULT_SWITCH_TO_PLAYLISTS);
      showWindow(windowType);
   }

   protected void showWindow(int windowType) {
      Log.d(TAG, String.format("Switching to window: %d", windowType));
      if (windowType == BaseBrowseActivity.RESULT_SWITCH_TO_ARTISTS)
         this.startActivityForResult(new Intent(BrowseActivity.this, ArtistsActivity.class), 1);
      else if (windowType == BaseBrowseActivity.RESULT_SWITCH_TO_PLAYLISTS)
         this.startActivityForResult(new Intent(BrowseActivity.this, PlaylistsActivity.class), 1);
      else
         Log.e(TAG, String.format("Unknown window type: %d", windowType));
   }
}
