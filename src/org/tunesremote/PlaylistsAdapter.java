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

import java.util.LinkedList;
import java.util.List;

import org.tunesremote.daap.Playlist;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * Adapter used for rendering playlists. Broken out into its own public class
 * because it is used in more than one location throughout TunesRemote.
 * <p>
 */
public class PlaylistsAdapter extends BaseAdapter {

   public final static String TAG = PlaylistsAdapter.class.toString();

   protected Context context;
   protected LayoutInflater inflater;
   public final List<Playlist> results = new LinkedList<Playlist>();
   private final Handler handler;

   public PlaylistsAdapter(Context context, Handler resultsHandler) {
      this.context = context;
      this.handler = resultsHandler;
      this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
   }

   public int getCount() {
      return results.size();
   }

   public Object getItem(int position) {
      return results.get(position);
   }

   public long getItemId(int position) {
      return position;
   }

   public View getView(int position, View convertView, ViewGroup parent) {
      try {
         convertView = this.inflater.inflate(R.layout.item_playlist, parent, false);
         Playlist ply = (Playlist) this.getItem(position);
         String title = ply.getName();
         String caption = context.getResources().getString(R.string.playlists_playlist_caption, ply.getCount());

         ((TextView) convertView.findViewById(android.R.id.text1)).setText(title);
         ((TextView) convertView.findViewById(android.R.id.text2)).setText(caption);

      } catch (Exception e) {
         Log.w(TAG, "getView:" + e.getMessage());
      }

      return convertView;
   }

   public void foundPlaylist(Playlist ply) {
      results.add(ply);
   }

   public void searchDone() {
      this.handler.removeMessages(-1);
      this.handler.sendEmptyMessage(-1);
   }

   @Override
   public boolean hasStableIds() {
      return true;
   }

}
